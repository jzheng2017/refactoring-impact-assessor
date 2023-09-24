package nl.jiankai.refactoring.core.project.query;

import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.util.JavaParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class JavaParserProjectQuery implements ProjectQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParserProjectQuery.class);

    @Override
    public List<MethodUsages> mostUsedMethods(Project provider, Collection<Project> users) {
        Set<String> allMethodNames = new HashSet<>();

        Map<String, Long> methodUsages = createMethodUsageMapAndPopulateAllMethodsMap(provider, allMethodNames);

        users.parallelStream().forEach(project -> computeMethodUsagesForProject(project, allMethodNames, methodUsages));

        return methodUsages
                .entrySet()
                .stream()
                .map(e -> new MethodUsages(new Method(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingLong(MethodUsages::usages).reversed())
                .toList();
    }

    private void computeMethodUsagesForProject(Project userProject, Set<String> allMethodNames, Map<String, Long> methodUsages) {
        JavaParserUtil
                .getAllMethodCalls(userProject)
                .filter(m -> hasPotential(m.getNameAsString(), allMethodNames))
                .forEach(m -> {
                    try {
                        ResolvedMethodDeclaration methodDeclaration = m.resolve();
                        String fullyQualifiedSignature = methodDeclaration.getQualifiedSignature();
                        methodUsages.merge(fullyQualifiedSignature, 1L, Long::sum);
                    } catch (Exception e) {
                        LOGGER.debug("Could not resolve method '{}'", m.getNameAsString(), e);
                    }
                });
    }

    private static Map<String, Long> createMethodUsageMapAndPopulateAllMethodsMap(Project provider, Set<String> allMethodNames) {
        return JavaParserUtil
                .getAllPublicMethods(provider)
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
    }

    private boolean hasPotential(String methodName, Set<String> methodNames) {
        return methodNames.contains(methodName);
    }
}
