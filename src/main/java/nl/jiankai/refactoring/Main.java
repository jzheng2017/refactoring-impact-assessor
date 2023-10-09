package nl.jiankai.refactoring;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.core.project.LocalFileProjectDiscovery;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectDiscovery;
import nl.jiankai.refactoring.core.project.git.GitRepository;
import nl.jiankai.refactoring.core.project.git.JGitRepositoryFactory;
import nl.jiankai.refactoring.core.project.query.JGitProjectQuery;
import nl.jiankai.refactoring.core.project.query.JavaParserProjectQuery;
import nl.jiankai.refactoring.core.project.query.MethodUsages;
import nl.jiankai.refactoring.core.project.query.ProjectQuery;
import nl.jiankai.refactoring.core.project.repository.Artifact;
import nl.jiankai.refactoring.core.project.repository.ArtifactRepository;
import nl.jiankai.refactoring.core.project.repository.maven.MavenCentralRepository;
import nl.jiankai.refactoring.core.refactoring.*;
import nl.jiankai.refactoring.core.refactoring.javaparser.Dependency;
import nl.jiankai.refactoring.core.refactoring.refactoringminer.RefactoringMinerRefactoringDetector;
import nl.jiankai.refactoring.util.JavaParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String baseLocation = ApplicationConfiguration.applicationAllProjectsLocation();

    public static void main(String[] args) throws URISyntaxException, MalformedURLException {
        /**
         * ALGORITHM STEPS:
         * 1: Use RefactoringMiner to get all method-related refactorings
         * 2: For each library project
         * 2.1: Compute all public methods of library project
         * 2.2: Fetch 100 projects depending on library project
         * 2.3: Compute library public method usages by dependent projects
         * 2.4: Compute correlation between popular api methods and refactored methods
         * 2.5: Write results to file
         * 3: Display results to console
         */

        //        refactoring between two commits
        GitRepository parentProject = new JGitRepositoryFactory().createProject(new File("/home/jiankai/IdeaProjects/commons-text"));
        Artifact.Coordinate parentArtifact = new Artifact.Coordinate("org.apache.commons", "commons-text", "1.10.0");

        ProjectDiscovery projectDiscovery = new LocalFileProjectDiscovery(createProjectLocation(parentArtifact).getAbsolutePath());

//        List<MethodDeclaration> allRefactoredMethods = findAllRefactoredMethods(parentProject, "59b6954", "606b568");

//        System.out.println(allRefactoredMethods)
//
        ProjectQuery projectQuery = new JavaParserProjectQuery();
        long start = System.currentTimeMillis();
        List<MethodUsages> usages = projectQuery.mostUsedMethods(parentProject, projectDiscovery.discover().toList());
        long end = System.currentTimeMillis();
        usages.stream().filter(methodUsages -> methodUsages.usages() > 0).limit(10).forEach(System.out::println);
        System.out.println(end - start);
//        repositories.forEach(System.out::println);
//
//        Artifact.Coordinate parentArtifact = new Artifact.Coordinate("org.apache.commons", "commons-text", "1.10.0");
//        Dependency dependency = new Dependency("org.apache.commons", "commons-text", "1.10.0");
//        JGitProjectQuery gitProjectQuery = new JGitProjectQuery();
//        ProjectDiscovery projectDiscovery = new LocalFileProjectDiscovery(createProjectLocation(parentArtifact).getAbsolutePath());
//        Map<Project, Optional<String>> projects = projectDiscovery
//                .discover()
//                .parallel()
//                .collect(toMap(p->p, project -> {
//                    try {
//                        return gitProjectQuery.findLatestVersionWithDependency(project, dependency);
//                    } catch (Exception e) {
//                        return Optional.empty();
//                    }
//                }));
//
//        projects.forEach((key, value) -> System.out.println(key + "- hash: " + value.orElse("no hash found")));
//        System.out.println(projects.values().stream().filter(Optional::isEmpty).count());
//        System.out.println(projects.size());
    }

    private static List<MethodDeclaration> findAllRefactoredMethods(GitRepository gitRepository, String startCommitId, String endCommitId) {
        RefactoringDetector refactoringDetector = new RefactoringMinerRefactoringDetector();
        Collection<Refactoring> refactorings = refactoringDetector.detectRefactoringBetweenCommit(gitRepository, startCommitId, endCommitId, Set.of(RefactoringType.METHOD_NAME, RefactoringType.METHOD_SIGNATURE));
        Map<String, List<Refactoring>> refactoringsByCommitMap = refactorings.stream().collect(Collectors.groupingBy(Refactoring::commitId));
        List<CompilationUnit> refactoredClasses = getRefactoredClasses(gitRepository, refactoringsByCommitMap);
        Set<String> fullyQualifiedPathsOfRefactoredElements = refactorings.stream().map(Refactoring::packagePath).collect(Collectors.toSet());

        List<ClassOrInterfaceDeclaration> classes = refactoredClasses
                .stream()
                .flatMap(c -> c.getTypes().stream().filter(ClassOrInterfaceDeclaration.class::isInstance).map(ClassOrInterfaceDeclaration.class::cast))
                .filter(c -> fullyQualifiedPathsOfRefactoredElements.contains(c.getFullyQualifiedName().orElse("")))
                .toList();

        return getRefactoredMethods(refactorings, classes);
    }

    private static List<CompilationUnit> getRefactoredClasses(GitRepository gitRepository, Map<String, List<Refactoring>> refactoringsByCommitMap) {
        return refactoringsByCommitMap
                .entrySet()
                .stream()
                .flatMap(entry -> {
                    gitRepository.checkout(entry.getKey());
                    return JavaParserUtil.getClasses(gitRepository, entry.getValue().stream().map(Refactoring::filePath).toList()).stream();
                })
                .toList();
    }

    private static List<MethodDeclaration> getRefactoredMethods(Collection<Refactoring> refactorings, List<ClassOrInterfaceDeclaration> refactoredClasses) {
        return refactoredClasses
                .stream()
                .flatMap(c ->
                        c
                                .getMembers()
                                .stream()
                                .filter(BodyDeclaration::isMethodDeclaration)
                                .map(MethodDeclaration.class::cast))
                .filter(method -> {
                    Range methodRange = method.getRange().orElse(null);

                    if (methodRange == null) {
                        return false;
                    }
                    return refactorings.stream().anyMatch(r -> Range.range(r.position().rowStart(), r.position().columnStart(), r.position().rowEnd(), r.position().columnEnd()).contains(methodRange));
                })
                .toList();
    }

    private static File createProjectLocation(Artifact.Coordinate coordinate) {
        return new File(baseLocation + File.separator + coordinate.toString() + "-dependents");
    }

    private static File createProjectLocation(Dependency dependency) {
        return new File(baseLocation + File.separator + dependency.toString() + "-dependents");
    }

    private static File createRepositoryLocation(Dependency dependency, String directory) {
        return new File(createProjectLocation(dependency).getAbsolutePath() + File.separator + directory);
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private static List<GitRepository> getDependentRepositories(Artifact.Coordinate parentArtifact, int desiredRepositories) {
        ArtifactRepository artifactRepository = new MavenCentralRepository();
        JGitRepositoryFactory factory = new JGitRepositoryFactory();

        int page = 0;
        File artifactLocation = createProjectLocation(parentArtifact);
        ProjectDiscovery projectDiscovery = new LocalFileProjectDiscovery(artifactLocation.getAbsolutePath());
        List<GitRepository> repositories = projectDiscovery
                .discover()
                .filter(GitRepository.class::isInstance)
                .map(GitRepository.class::cast)
                .toList();

        int retries = 0;
        while (repositories.size() <= desiredRepositories) {
            List<Artifact> artifacts = artifactRepository.getArtifactUsages(parentArtifact, new ArtifactRepository.PageOptions(page, 200), new ArtifactRepository.FilterOptions(true));
            if (artifacts.isEmpty()) {
                LOGGER.info("No more artifacts could be found at page {}. Total usable projects: {}.", page, repositories.size());
                if (retries >= 3) {
                    break;
                } else {
                    retries++;
                }
            }
            artifacts
                    .stream()
                    .filter(distinctByKey(artifact -> artifact.coordinate().groupId() + "-" + artifact.coordinate().artifactId()))
                    .parallel()
                    .map(artifact -> {
                        try {
                            return factory.createProject(artifact.sourceControlUrl(), new File(artifactLocation.getAbsolutePath() + File.separator + artifact.getId()));
                        } catch (Exception e) {
                            LOGGER.warn("Could not create project '{}'", artifact.getId(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(repositories::add);
            page++;
        }

        return repositories;
    }

}
