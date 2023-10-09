package nl.jiankai.refactoring.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.ParserCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import nl.jiankai.refactoring.core.project.CompositeProjectFactory;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.refactoring.RefactoringData;
import nl.jiankai.refactoring.core.refactoring.RefactoringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public class JavaParserUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParserUtil.class);

    public static List<MethodCallExpr> getMethodUsages(CompilationUnit compilationUnit, String fullyQualifiedSignature, String methodName) {
        AtomicLong failedResolves = new AtomicLong();
        AtomicLong totalResolveAttempts = new AtomicLong();
        List<MethodCallExpr> methodsUsages = compilationUnit.findAll(MethodCallExpr.class, methodCall -> {
            try {
                if (Objects.equals(methodCall.getNameAsString(), methodName)) {
                    totalResolveAttempts.getAndIncrement();
                    return Objects.equals(fullyQualifiedSignature, methodCall.resolve().getQualifiedSignature());
                }
                return false;
            } catch (Exception ex) {
                failedResolves.getAndIncrement();
                return false;
            }
        });

        if (failedResolves.get() > 0) {
            LOGGER.warn("{} out of {} methods that matched the pattern {} could not be resolved correctly", failedResolves.get(), totalResolveAttempts, fullyQualifiedSignature);
        }

        return methodsUsages;
    }

    public static Stream<MethodCallExpr> getAllMethodCalls(Project project) {
        return getProject(project)
                .parallelStream()
                .flatMap(compilationUnit -> compilationUnit.findAll(MethodCallExpr.class).stream())
                .sequential();
    }


    public static Collection<CompilationUnit> getProject(File pathToProject) {
        return getProject(new CompositeProjectFactory().createProject(pathToProject));
    }

    public static Collection<CompilationUnit> getClasses(Project project, List<String> relativePaths) {
        return getProjectAsStream(project).filter(compilationUnit -> {
            try {
                String filePath = compilationUnit.getStorage().orElseThrow().getPath().toString();

                return relativePaths.stream().anyMatch(filePath::endsWith);
            } catch (Exception e) {
                return false;
            }
        }).toList();
    }

    public static Collection<CompilationUnit> getProject(Project project) {
        return getProjectAsStream(project).toList();
    }

    public static Stream<CompilationUnit> getProjectAsStream(Project project) {
        Collection<File> jarLocations = new ArrayList<>();
        try {
            project.install();
            jarLocations = project.jars();
        } catch (Exception exception) {
            LOGGER.warn("Could not properly install project '{}' dependencies. Parsing the project may cause problems...", project.getId());
        }
        File projectPath = project.getLocalPath();
        List<File> allSourceDirectories = collectAllSourceDirectories(projectPath);
        try {
            CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());

            for (File sourceDir : allSourceDirectories) {
                typeSolver.add(new JavaParserTypeSolver(sourceDir));
            }

            for (File jar : jarLocations) {
                typeSolver.add(new JarTypeSolver(jar.getAbsolutePath()));
            }

            ProjectRoot projectRoot = new ParserCollectionStrategy(new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver))).collect(projectPath.toPath());

            return projectRoot
                    .getSourceRoots()
                    .stream()
                    .flatMap(
                            sourceRoot -> {
                                try {
                                    return sourceRoot.tryToParse().stream();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .filter(ParseResult::isSuccessful)
                    .map(parseResult -> parseResult.getResult().orElse(null))
                    .filter(Objects::nonNull);
        } catch (Exception ex) {
            LOGGER.warn("Parsing project '{}' went wrong. Reason: {}", projectPath, ex.getMessage(), ex);
        }

        return Stream.empty();
    }

    public static Stream<MethodDeclaration> getAllPublicMethods(Project project) {
        return getProject(project)
                .stream()
                .filter(compilationUnit -> {
                    String classOrInterfaceName = compilationUnit.getPrimaryTypeName().orElse("");
                    ClassOrInterfaceDeclaration classOrInterfaceDeclaration = compilationUnit.getClassByName(classOrInterfaceName).orElseGet(() -> compilationUnit.getInterfaceByName(classOrInterfaceName).orElse(null));
                    if (classOrInterfaceDeclaration != null) {
                        return classOrInterfaceDeclaration.isPublic() || classOrInterfaceDeclaration.isInterface();
                    } else {
                        LOGGER.debug("Could not get public methods of class '{}'", classOrInterfaceName);
                        return false;
                    }
                })
                .flatMap(compilationUnit -> compilationUnit.findAll(MethodDeclaration.class).stream());
    }

    private static List<File> collectAllSourceDirectories(File root) {
        List<File> sourceDirs = new ArrayList<>();

        File[] files = root.listFiles();
        if (files != null) {
            for (File child : files) {
                if (child.isDirectory() && child.getPath().endsWith("/src/main/java")) {
                    sourceDirs.add(child);
                }
                sourceDirs.addAll(collectAllSourceDirectories(child));
            }

        }
        return sourceDirs;
    }

    public static boolean isBreakingChange(MethodCallExpr methodCallExpr, RefactoringData refactoringData) {
        RefactoringType refactoringType = refactoringData.refactoringType();

        return switch (refactoringType) {
            case METHOD_NAME -> true;
            case METHOD_SIGNATURE -> computeMethodSignatureIsBreaking(methodCallExpr);
            default -> false;
        };
    }

    private static boolean computeMethodSignatureIsBreaking(MethodCallExpr methodCallExpr) {
        return false;
    }
}
