package nl.jiankai.refactoring.core.project.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import nl.jiankai.refactoring.configuration.CacheLocation;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectCoordinate;
import nl.jiankai.refactoring.core.refactoring.javaparser.Dependency;
import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.api.Identifiable;
import nl.jiankai.refactoring.core.storage.filestorage.MultiFileCacheService;
import nl.jiankai.refactoring.serialisation.JacksonSerializationService;
import nl.jiankai.refactoring.util.JavaParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class JavaParserProjectQuery implements ProjectQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParserProjectQuery.class);
    private final CacheService<ProjectPublicMethodsResult> publicMethodsCacheService = new MultiFileCacheService<>(CacheLocation.PUBLIC_METHODS, new JacksonSerializationService(), ProjectPublicMethodsResult.class);
    private final CacheService<ProjectMethodCallsResult> methodCallsCacheService = new MultiFileCacheService<>(CacheLocation.METHOD_CALLS, new JacksonSerializationService(), ProjectMethodCallsResult.class);

    @Override
    public List<MethodUsages> mostUsedMethods(Project provider, Collection<? extends Project> users) {
        LOGGER.info("Computing most used methods of project '{}'", provider.getId());
        Set<String> allMethodNames = new HashSet<>();
        Map<String, Long> methodUsages = createMethodUsageMapAndPopulateAllMethodsMap(provider, allMethodNames);
        AtomicInteger fullyProcessed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        users.forEach(project -> {
            try {
                computeMethodUsagesForProject(project, allMethodNames, methodUsages);
            } catch (Exception e) {
                LOGGER.warn("Failed to compute usages for project '{}'", project.getId());
                failed.incrementAndGet();
            }
            LOGGER.info("{} out of {} projects processed ({} failures)", fullyProcessed.incrementAndGet(), users.size(), failed.get());
        });

        return sortByUsagesHighToLow(methodUsages);
    }

    @Override
    public Optional<String> findLatestVersionWithDependency(Project project, Dependency dependency) {
        throw new UnsupportedOperationException();
    }

    private static List<MethodUsages> sortByUsagesHighToLow(Map<String, Long> methodUsages) {
        return methodUsages
                .entrySet()
                .stream()
                .map(e -> new MethodUsages(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(MethodUsages::usages).reversed())
                .toList();
    }

    private void computeMethodUsagesForProject(Project project, Set<String> allMethodNames, Map<String, Long> methodUsages) {
        try {
            ProjectCoordinate projectCoordinates = project.getProjectVersion().coordinate();
            if (methodCallsCacheService.isCached(projectCoordinates.toString())) {
                LOGGER.info("Project '{}' method calls are cached!", projectCoordinates);
                Optional<ProjectMethodCallsResult> optional = methodCallsCacheService.get(projectCoordinates.toString());

                optional.ifPresentOrElse(
                        result -> {
                            List<MethodUsages> methodCalls = result.methodCalls;
                            for (MethodUsages methodCall : methodCalls) {
                                if (methodUsages.containsKey(methodCall.fullyQualifiedSignature())) {
                                    methodUsages.merge(methodCall.fullyQualifiedSignature(), methodCall.usages(), Long::sum);
                                }
                            }
                        },
                        () -> computeAllMethodCalls(project, allMethodNames, methodUsages, projectCoordinates));
            } else {
                computeAllMethodCalls(project, allMethodNames, methodUsages, projectCoordinates);
            }
        } catch (Exception e) {
            computeAllMethodCalls(project, allMethodNames, methodUsages, null);
        }
    }

    private void computeAllMethodCalls(Project project, Set<String> allMethodNames, Map<String, Long> methodUsages, ProjectCoordinate projectCoordinates) {
        LOGGER.info("Computing all method calls for project {}", project.getId());
        JavaParserUtil
                .getAllMethodCalls(project)
                .filter(m -> hasPotential(m.getNameAsString(), allMethodNames))
                .forEach(m -> {
                    try {
                        ResolvedMethodDeclaration methodDeclaration = m.resolve();
                        String fullyQualifiedSignature = methodDeclaration.getQualifiedSignature();
                        if (methodUsages.containsKey(fullyQualifiedSignature)) {
                            methodUsages.merge(fullyQualifiedSignature, 1L, Long::sum);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Could not resolve method '{}'", m.getNameAsString(), e);
                        methodUsages.merge("COULD NOT DETERMINE THE FULLY QUALIFIED SIGNATURE", 1L, Long::sum);
                    }
                });

        if (projectCoordinates != null) {
            methodCallsCacheService.write(new ProjectMethodCallsResult(projectCoordinates, methodUsages.entrySet().stream().map(e -> new MethodUsages(e.getKey(), e.getValue())).toList()));
        }
    }

    private Map<String, Long> createMethodUsageMapAndPopulateAllMethodsMap(Project project, Set<String> allMethodNames) {
        try {
            ProjectCoordinate projectCoordinates = project.getProjectVersion().coordinate();
            if (publicMethodsCacheService.isCached(projectCoordinates.toString())) {
                LOGGER.info("Project '{}' public methods are cached!", projectCoordinates);
                Optional<ProjectPublicMethodsResult> optional = publicMethodsCacheService.get(projectCoordinates.toString());

                return optional
                        .map(result -> {
                            allMethodNames.addAll(result.methods.stream().map(method -> {
                                String methodName = method.substring(0, method.indexOf("("));
                                return methodName.substring(method.lastIndexOf(".") + 1);
                            }).toList());
                            return result.methods.stream().collect(Collectors.toMap(m -> m, m -> 0L));
                        })
                        .orElseGet(() -> computePublicMethodsAndCache(project, allMethodNames, projectCoordinates));
            } else {
                return computePublicMethodsAndCache(project, allMethodNames, projectCoordinates);
            }
        } catch (Exception e) {
            return computePublicMethodsAndCache(project, allMethodNames, null);
        }
    }

    private Map<String, Long> computePublicMethodsAndCache(Project project, Set<String> allMethodNames, ProjectCoordinate projectCoordinates) {
        Map<String, Long> methodUsages = JavaParserUtil
                .getAllPublicMethods(project)
                .map(methodDeclaration -> {
                    try {
                        allMethodNames.add(methodDeclaration.getNameAsString());
                        return methodDeclaration.resolve();
                    } catch (Exception e) {
                        LOGGER.debug("Could not resolve method '{}'", methodDeclaration.getNameAsString(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toConcurrentMap(m -> {
                    try {
                        return m.getQualifiedSignature();
                    } catch (Exception e) {
                        return "COULD NOT DETERMINE THE FULLY QUALIFIED SIGNATURE";
                    }
                }, m -> 0L, (a, b) -> a));

        if (projectCoordinates != null) {
            publicMethodsCacheService.write(new ProjectPublicMethodsResult(projectCoordinates, methodUsages.keySet()));
        }
        return methodUsages;
    }

    private boolean hasPotential(String methodName, Set<String> methodNames) {
        return methodNames.contains(methodName);
    }

    @JsonIgnoreProperties({"id"})
    private record ProjectPublicMethodsResult(ProjectCoordinate coordinate,
                                              Set<String> methods) implements Identifiable {

        @Override
        public String getId() {
            return coordinate.toString();
        }
    }

    @JsonIgnoreProperties({"id"})
    private record ProjectMethodCallsResult(ProjectCoordinate coordinate,
                                            List<MethodUsages> methodCalls) implements Identifiable {

        @Override
        public String getId() {
            return coordinate.toString();
        }
    }

    @JsonIgnoreProperties({"id"})
    private record MethodUsagesResult(ProjectCoordinate coordinate,
                                      List<MethodUsages> methodUsages) implements Identifiable {
        @Override
        public String getId() {
            return coordinate.toString();
        }
    }
}
