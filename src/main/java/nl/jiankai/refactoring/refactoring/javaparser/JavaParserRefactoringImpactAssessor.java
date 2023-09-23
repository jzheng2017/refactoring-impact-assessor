package nl.jiankai.refactoring.refactoring.javaparser;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.ParserCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import nl.jiankai.refactoring.project.CompositeProjectFactory;
import nl.jiankai.refactoring.project.Project;
import nl.jiankai.refactoring.project.dependencymanagement.ProjectData;
import nl.jiankai.refactoring.refactoring.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public class JavaParserRefactoringImpactAssessor implements RefactoringImpactAssessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParserRefactoringImpactAssessor.class);
    private Set<RefactoringType> supportedRefactoringTypes = Set.of(RefactoringType.METHOD_SIGNATURE, RefactoringType.METHOD_NAME);
    private ProjectsToScan projectsToScan;

    public JavaParserRefactoringImpactAssessor() {
        projectsToScan = new ProjectsToScan();
    }

    @Override
    public ImpactAssessment assesImpact(RefactoringData refactoringData) {
        if (!supportedRefactoringTypes.contains(refactoringData.refactoringType())) {
            throw new UnsupportedOperationException("Assessing impact for refactoring type '%s' is not supported yet".formatted(refactoringData.refactoringType()));
        }

        LOGGER.info("Computing refactoring impact for all registered projects");

        Map<ProjectData, Collection<CompilationUnit>> projects = getAllProjects();
        Map<ProjectData, List<RefactoringImpact>> impacts = projects
                .entrySet()
                .parallelStream()
                .collect(
                        toMap(
                                Map.Entry::getKey,
                                entry -> entry
                                        .getValue()
                                        .stream()
                                        .flatMap(cu -> collectRefactoringImpact(cu, refactoringData))
                                        .toList()
                        ));

        return new ImpactAssessment(impacts, RefactoringStatisticsGenerator.compute(impacts));
    }

    @Override
    public List<RefactoringImpact> assesImpact(ProjectData projectData, RefactoringData refactoringData) {
        LOGGER.info("Computing refactoring impact for project {}", projectData);
        return getProject(projectData.pathToProject())
                .stream()
                .flatMap(compilationUnit -> collectRefactoringImpact(compilationUnit, refactoringData))
                .toList();
    }

    private Stream<RefactoringImpact> collectRefactoringImpact(CompilationUnit compilationUnit, RefactoringData refactoringData) {
        if (isMethodRefactoringType(refactoringData.refactoringType())) {
            return JavaParserUtil
                    .getMethodUsages(compilationUnit, refactoringData.fullyQualifiedSignature(), refactoringData.elementName())
                    .stream()
                    .map(method -> {
                        Range range = method.getRange().orElse(Range.range(0, 0, 0, 0));
                        String filePath = "";
                        String fileName = "";
                        if (compilationUnit.getStorage().isPresent()) {
                            CompilationUnit.Storage storage = compilationUnit.getStorage().get();
                            filePath = storage.getPath().toAbsolutePath().toString();
                            fileName = storage.getFileName();
                        }

                        return new RefactoringImpact(
                                filePath, fileName, getPackageName(method), getClassName(method), method.getNameAsString(),
                                new RefactoringImpact.Position(range.begin.column, range.end.column, range.begin.line, range.end.line),
                                JavaParserUtil.isBreakingChange(method, refactoringData));
                    });
        } else {
            throw new UnsupportedOperationException("Refactoring type '%s' is not supported yet".formatted(refactoringData.refactoringType()));
        }
    }

    private String getPackageName(Node node) {
        while (node.hasParentNode()) {
            node = node.getParentNode().get();
            if (node instanceof CompilationUnit cu) {
                return cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
            }
        }

        return "";
    }

    private String getClassName(Node node) {
        while (node.hasParentNode()) {
            node = node.getParentNode().get();
            if (node instanceof ClassOrInterfaceDeclaration coid) {
                return coid.getNameAsString();
            }
        }

        return "";
    }

    private Map<ProjectData, Collection<CompilationUnit>> getAllProjects() {
        return projectsToScan
                .projects()
                .stream()
                .collect(toMap(nl.jiankai.refactoring.project.Project::getProjectVersion, this::getProject));
    }

    private Collection<CompilationUnit> getProject(File pathToProject) {
        return getProject(new CompositeProjectFactory().createProject(pathToProject));
    }

    private Collection<CompilationUnit> getProject(Project project) {
        project.install();
        Collection<File> jarLocations = project.jars();
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
                    .map(parseResult -> parseResult.getResult().get())
                    .toList();
        } catch (Exception ex) {
            LOGGER.warn("Parsing project '{}' went wrong. Reason: {}", projectPath, ex.getMessage(), ex);
        }

        return new ArrayList<>();
    }

    private List<File> collectAllSourceDirectories(File root) {
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

    private boolean isMethodRefactoringType(RefactoringType refactoringType) {
        return switch (refactoringType) {
            case METHOD_NAME, METHOD_SIGNATURE -> true;
            case UNKNOWN -> false;
        };
    }
}
