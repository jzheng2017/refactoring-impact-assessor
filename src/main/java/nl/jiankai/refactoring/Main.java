package nl.jiankai.refactoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.configuration.CacheLocation;
import nl.jiankai.refactoring.core.project.LocalFileProjectDiscovery;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectDiscovery;
import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectCoordinate;
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
import nl.jiankai.refactoring.core.storage.api.CacheService;
import nl.jiankai.refactoring.core.storage.api.Identifiable;
import nl.jiankai.refactoring.core.storage.filestorage.LocalFileStorageService;
import nl.jiankai.refactoring.core.storage.filestorage.MultiFileCacheService;
import nl.jiankai.refactoring.serialisation.JacksonSerializationService;
import nl.jiankai.refactoring.serialisation.SerializationService;
import nl.jiankai.refactoring.util.JavaParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.reducing;
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

        LocalFileStorageService projectsToAnalyzeStorage = new LocalFileStorageService(ApplicationConfiguration.applicationAssetsBaseDirectory() + File.separator + "projects_to_analyze.txt", false);
        List<String> projectsToAnalyze = projectsToAnalyzeStorage.read().toList();
        for (String projectToAnalyze : projectsToAnalyze) {
            String[] split = projectToAnalyze.split(";");
            Artifact.Coordinate parentArtifact = Artifact.Coordinate.read(split[1]);
            GitRepository parentProject = new JGitRepositoryFactory().createProject(split[0], new File(ApplicationConfiguration.applicationAllProjectsLocation() + File.separator + parentArtifact));
//        ProjectDiscovery projectDiscovery = new LocalFileProjectDiscovery(createProjectLocation(parentArtifact).getAbsolutePath());

            // refactoring between two commits
            CacheService<ProjectRefactoring> projectRefactoringCacheService = new MultiFileCacheService<>(CacheLocation.PROJECT_REFACTORINGS, new JacksonSerializationService(), ProjectRefactoring.class);
            String startCommitId = split[2];
            String endCommitId = split[3];
            String projectRefactoringIdentifier = startCommitId + endCommitId;
            Optional<ProjectRefactoring> projectRefactoring = projectRefactoringCacheService.get(projectRefactoringIdentifier);
            Set<String> allRefactoredMethods = projectRefactoring
                    .orElseGet(() -> {
                        Set<String> methodDeclarations = findAllRefactoredMethods(parentProject, startCommitId, endCommitId)
                                .stream()
                                .map(m -> {
                                    try {
                                        ResolvedMethodDeclaration resolvedMethodDeclaration = m.resolve();
                                        return resolvedMethodDeclaration.getQualifiedSignature();
                                    } catch (Exception e) {
                                        LOGGER.warn("Could not resolve method {}", m.getNameAsString(), e);
                                        return "";
                                    }
                                })
                                .filter(Predicate.not(String::isEmpty))
                                .collect(Collectors.toSet());
                        projectRefactoringCacheService.write(new ProjectRefactoring(startCommitId, endCommitId, methodDeclarations));
                        return new ProjectRefactoring(startCommitId, endCommitId, methodDeclarations);
                    })
                    .refactoredMethods();

            //dependents
            List<GitRepository> dependents = getDependentRepositories(parentArtifact, 100);
            JGitProjectQuery gitProjectQuery = new JGitProjectQuery();
            Dependency dependency = toDependency(parentArtifact);
            Map<GitRepository, Optional<String>> projects = dependents
                    .parallelStream()
                    .collect(toMap(p -> p, project -> {
                        try {
                            return gitProjectQuery.findLatestVersionWithDependency(project, dependency);
                        } catch (Exception e) {
                            return Optional.empty();
                        }
                    }));

            dependents = projects.entrySet()
                    .stream()
                    .filter(entry -> {
                        Optional<String> commit = entry.getValue();
                        if (commit.isPresent()) {
                            GitRepository repo = entry.getKey();
                            try {
                                repo.checkout(commit.get());
                            } catch (Exception e) {
                                return false;
                            }
                            return true;
                        } else {
                            return false;
                        }
                    })
                    .map(Map.Entry::getKey)
                    .toList();


            //get most used methods
            ProjectQuery projectQuery = new JavaParserProjectQuery();
            parentProject.checkout(startCommitId);
            List<MethodUsages> usages = projectQuery.mostUsedMethods(parentProject, dependents);
            Set<String> usedMethodsRefactored = usages.stream().filter(method -> method.usages() > 0 && allRefactoredMethods.contains(method.fullyQualifiedSignature())).map(MethodUsages::fullyQualifiedSignature).collect(Collectors.toSet());

            LocalFileStorageService pipelineResultStorage = new LocalFileStorageService(CacheLocation.PIPELINE_RESULTS + File.separator + parentArtifact + "-" + System.currentTimeMillis(), true);
            SerializationService serializationService = new JacksonSerializationService();
            pipelineResultStorage.write(new String(serializationService.serialize(new PipelineResult(usages, usedMethodsRefactored))));
        }
    }

    private record PipelineResult(List<MethodUsages> methodUsages, Set<String> refactoredMethodsUsedByDependents) {
    }

    @JsonIgnoreProperties({"id"})
    private record ProjectRefactoring(String startCommitId, String endCommitId,
                                      Set<String> refactoredMethods) implements Identifiable {

        @Override
        public String getId() {
            return startCommitId + endCommitId;
        }
    }

    private static Dependency toDependency(Artifact.Coordinate artifactCoordinate) {
        return new Dependency(artifactCoordinate.groupId(), artifactCoordinate.artifactId(), artifactCoordinate.version());
    }

    private static Artifact.Coordinate toCoordinate(ProjectCoordinate projectCoordinate) {
        return new Artifact.Coordinate(projectCoordinate.groupId(), projectCoordinate.artifactId(), projectCoordinate.version());
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
        return new File(CacheLocation.DEPENDENTS + File.separator + coordinate.toString());
    }

    private static File createProjectLocation(Dependency dependency) {
        return new File(CacheLocation.DEPENDENTS + File.separator + dependency.toString());
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

        if (!repositories.isEmpty()) {
            return repositories;
        }

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
