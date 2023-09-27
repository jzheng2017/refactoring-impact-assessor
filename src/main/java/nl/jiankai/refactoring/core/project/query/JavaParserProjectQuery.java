package nl.jiankai.refactoring.core.project.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import nl.jiankai.refactoring.configuration.CacheLocation;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectCoordinate;
import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.api.Identifiable;
import nl.jiankai.refactoring.core.storage.filestorage.CacheServiceImpl;
import nl.jiankai.refactoring.serialisation.JacksonSerializationService;
import nl.jiankai.refactoring.util.JavaParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class JavaParserProjectQuery implements ProjectQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParserProjectQuery.class);
    private final CacheService<ProjectPublicMethodsResult> publicMethodsCacheService = new CacheServiceImpl<>(CacheLocation.PUBLIC_METHODS, new JacksonSerializationService(), ProjectPublicMethodsResult.class);
    private final CacheService<ProjectMethodCallsResult> methodCallsCacheService = new CacheServiceImpl<>(CacheLocation.METHOD_CALLS, new JacksonSerializationService(), ProjectMethodCallsResult.class);

    @Override
    public List<MethodUsages> mostUsedMethods(Project provider, Collection<Project> users) {
        LOGGER.info("Computing most used methods of project '{}'", provider.getId());
        Set<String> allMethodNames = new HashSet<>();
        Map<String, Long> methodUsages = createMethodUsageMapAndPopulateAllMethodsMap(provider, allMethodNames);
        users.parallelStream().forEach(project -> computeMethodUsagesForProject(project, allMethodNames, methodUsages));

        return sortByUsagesHighToLow(methodUsages);
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
        ProjectCoordinate projectCoordinates = project.getProjectVersion().coordinate();
        if (methodCallsCacheService.isCached(projectCoordinates.toString())) {
            LOGGER.info("Project '{}' method calls are cached!", projectCoordinates);
            Optional<ProjectMethodCallsResult> optional = methodCallsCacheService.get(projectCoordinates.toString());

            optional.ifPresentOrElse(
                    result -> {
                        List<MethodUsages> methodCalls = result.methodCalls;
                        for (MethodUsages methodCall : methodCalls) {
                            methodUsages.put(methodCall.fullyQualifiedSignature(), methodCall.usages());
                        }
                    },
                    () -> computeAllMethodCalls(project, allMethodNames, methodUsages, projectCoordinates));
        } else {
            computeAllMethodCalls(project, allMethodNames, methodUsages, projectCoordinates);
        }
    }

    private void computeAllMethodCalls(Project project, Set<String> allMethodNames, Map<String, Long> methodUsages, ProjectCoordinate projectCoordinates) {
        JavaParserUtil
                .getAllMethodCalls(project)
                .filter(m -> hasPotential(m.getNameAsString(), allMethodNames))
                .forEach(m -> {
                    try {
                        ResolvedMethodDeclaration methodDeclaration = m.resolve();
                        String fullyQualifiedSignature = methodDeclaration.getQualifiedSignature();
                        methodUsages.merge(fullyQualifiedSignature, 1L, Long::sum);
                    } catch (Exception e) {
                        LOGGER.debug("Could not resolve method '{}'", m.getNameAsString(), e);
                        methodUsages.merge(null, 1L, Long::sum);
                    }
                });

        methodCallsCacheService.write(new ProjectMethodCallsResult(projectCoordinates, methodUsages.entrySet().stream().map(e -> new MethodUsages(e.getKey(), e.getValue())).toList()));
    }

    private Map<String, Long> createMethodUsageMapAndPopulateAllMethodsMap(Project project, Set<String> allMethodNames) {
        ProjectCoordinate projectCoordinates = project.getProjectVersion().coordinate();
        if (publicMethodsCacheService.isCached(projectCoordinates.toString())) {
            LOGGER.info("Project '{}' public methods are cached!", projectCoordinates);
            Optional<ProjectPublicMethodsResult> optional = publicMethodsCacheService.get(projectCoordinates.toString());

            return optional
                    .map(result -> result.methods.stream().collect(Collectors.toMap(m -> m, m -> 0L)))
                    .orElseGet(() -> computePublicMethodsAndCache(project, allMethodNames, projectCoordinates));
        } else {
            return computePublicMethodsAndCache(project, allMethodNames, projectCoordinates);
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

        publicMethodsCacheService.write(new ProjectPublicMethodsResult(projectCoordinates, methodUsages.keySet()));
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
