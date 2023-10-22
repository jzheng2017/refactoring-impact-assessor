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
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        /**
         * ALGORITHM STEPS:
         * 1: Use RefactoringMiner to get all method-related refactorings (all projects in parallel)
         * 2: For each library project
         * 2.1: Compute all public methods of library project
         * 2.2: Fetch 100 projects depending on library project
         * 2.3: Compute library public method usages by dependent projects
         * 2.4: Compute correlation between popular api methods and refactored methods
         * 2.5: Write results to file
         * 3: Display results to console
         */

        long startTimeScript = System.currentTimeMillis();
        LocalFileStorageService projectsToAnalyzeStorage = new LocalFileStorageService(ApplicationConfiguration.applicationAssetsBaseDirectory() + File.separator + "projects_to_analyze.txt", false);
        List<String> projectsToAnalyze = projectsToAnalyzeStorage.read().toList();
        AtomicInteger projectsRefactoringFinishedComputing = new AtomicInteger();
        Map<String, ProjectRefactoring> projectRefactorings = projectsToAnalyze
                .parallelStream()
                .collect(
                        toConcurrentMap(
                                project -> project,
                                project -> {
                                    String[] split = project.split(";");
                                    Artifact.Coordinate parentArtifact = Artifact.Coordinate.read(split[1]);
                                    String startCommitId = split[2];
                                    String endCommitId = split[3];
                                    try {
                                        GitRepository parentProject = new JGitRepositoryFactory().createProject(split[0], new File(ApplicationConfiguration.applicationAllProjectsLocation() + File.separator + parentArtifact));

                                        CacheService<ProjectRefactoring> projectRefactoringCacheService = new MultiFileCacheService<>(CacheLocation.PROJECT_REFACTORINGS, new JacksonSerializationService(), ProjectRefactoring.class);
                                        String projectRefactoringIdentifier = createProjectRefactoringIdentifier(parentArtifact.toString(), startCommitId, endCommitId);
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
                                                    ProjectRefactoring refactoring = new ProjectRefactoring(parentArtifact.toString(), startCommitId, endCommitId, methodDeclarations);
                                                    projectRefactoringCacheService.write(refactoring);
                                                    return refactoring;
                                                })
                                                .refactoredMethods();
                                        LOGGER.info("Finished computing refactored methods for project {} between commits {} and {}", parentArtifact, startCommitId, endCommitId);
                                        LOGGER.info("{} out of {} projects finished", projectsRefactoringFinishedComputing.incrementAndGet(), projectsToAnalyze.size());
                                        return new ProjectRefactoring(parentArtifact.toString(), startCommitId, endCommitId, allRefactoredMethods);
                                    } catch (Exception e) {
                                        LOGGER.error("Could not compute refactored methods for project {}", parentArtifact, e);
                                        LOGGER.info("{} out of {} projects finished", projectsRefactoringFinishedComputing.incrementAndGet(), projectsToAnalyze.size());
                                        return new ProjectRefactoring(parentArtifact.toString(), startCommitId, endCommitId, new HashSet<>());
                                    }
                                }));

        LOGGER.info("Finished computing refactored methods for all projects");

        for (Map.Entry<String, ProjectRefactoring> projectToAnalyze : projectRefactorings.entrySet()) {
            long startTime = System.currentTimeMillis();
            String projectCoordinate = projectToAnalyze.getKey();
            String[] split = projectCoordinate.split(";");
            String startCommitId = split[2];
            Artifact.Coordinate parentArtifact = Artifact.Coordinate.read(split[1]);
            LOGGER.info("Starting to analyze project {}", parentArtifact);
            GitRepository parentProject = new JGitRepositoryFactory().createProject(split[0], new File(ApplicationConfiguration.applicationAllProjectsLocation() + File.separator + parentArtifact));

            // refactoring between two commits
            Set<String> allRefactoredMethods = projectToAnalyze.getValue().refactoredMethods();

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
            long endTime = System.currentTimeMillis();
            LocalFileStorageService pipelineResultStorage = new LocalFileStorageService(CacheLocation.PIPELINE_RESULTS + File.separator + parentArtifact + "-" + System.currentTimeMillis(), true);
            SerializationService serializationService = new JacksonSerializationService();
            long analysisDurationMs = endTime - startTime;
            pipelineResultStorage.write(new String(serializationService.serialize(new PipelineResult(usages, usedMethodsRefactored, dependents.size(), analysisDurationMs))));
            LOGGER.info("Finished analyzing project {}", parentArtifact);
            LOGGER.info("It took {} minutes to analyze the project", analysisDurationMs / 60000);
        }

        long endTimeScript = System.currentTimeMillis();
        LOGGER.info("Script finished in {} minutes", (endTimeScript - startTimeScript) / 60000);
    }

    private static String createProjectRefactoringIdentifier(String projectId, String startCommitId, String endCommitId) {
        return projectId + "_" + startCommitId + endCommitId;
    }

    private record PipelineResult(List<MethodUsages> methodUsages, Set<String> refactoredMethodsUsedByDependents,
                                  int projectsAnalyzed, long analysisDurationMs) {
    }

    @JsonIgnoreProperties({"id"})
    private record ProjectRefactoring(String projectId, String startCommitId, String endCommitId,
                                      Set<String> refactoredMethods) implements Identifiable {

        @Override
        public String getId() {
            return createProjectRefactoringIdentifier(projectId, startCommitId, endCommitId);
        }
    }

    private static Dependency toDependency(Artifact.Coordinate artifactCoordinate) {
        return new Dependency(artifactCoordinate.groupId(), artifactCoordinate.artifactId(), artifactCoordinate.version());
    }

    private static Artifact.Coordinate toCoordinate(ProjectCoordinate projectCoordinate) {
        return new Artifact.Coordinate(projectCoordinate.groupId(), projectCoordinate.artifactId(), projectCoordinate.version());
    }

    private static List<MethodDeclaration> findAllRefactoredMethods(GitRepository gitRepository, String startCommitId, String endCommitId) {
        LOGGER.info("Finding all refactored methods");
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
        LOGGER.info("Computing refactored classes");
        return refactoringsByCommitMap
                .entrySet()
                .stream()
                .flatMap(entry -> {
                    try {
                        gitRepository.checkout(entry.getKey());
                        return JavaParserUtil.getClasses(gitRepository, entry.getValue().stream().map(Refactoring::filePath).toList()).stream();
                    } catch (Exception e) {
                        LOGGER.error("Could not checkout commit {}", entry.getKey(), e);
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private static List<MethodDeclaration> getRefactoredMethods(Collection<Refactoring> refactorings, List<ClassOrInterfaceDeclaration> refactoredClasses) {
        LOGGER.info("Computing refactored methods");
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
        LOGGER.info("Fetching {} dependent repositories of project {}", desiredRepositories, parentArtifact);
        ArtifactRepository artifactRepository = new MavenCentralRepository();
        JGitRepositoryFactory factory = new JGitRepositoryFactory();

        int page = 0;
        int pageSize = 200;
        File artifactLocation = createProjectLocation(parentArtifact);
        ProjectDiscovery projectDiscovery = new LocalFileProjectDiscovery(artifactLocation.getAbsolutePath());
        List<GitRepository> repositories = projectDiscovery
                .discover()
                .filter(GitRepository.class::isInstance)
                .map(GitRepository.class::cast)
                .collect(Collectors.toList());

        if (repositories.size() >= desiredRepositories) {
            return repositories;
        }

        int retries = 0;
        Random randomDelay = new Random();
        while (repositories.size() <= desiredRepositories) {
            List<Artifact> artifacts = artifactRepository.getArtifactUsages(
                    parentArtifact,
                    new ArtifactRepository.PageOptions(page, pageSize),
                    new ArtifactRepository.FilterOptions(
                            true,
                            repositories
                                    .stream()
                                    .map(
                                            repo -> {
                                                try {
                                                    return toCoordinate(repo.getProjectVersion().coordinate());
                                                } catch (Exception e) {
                                                    return null;
                                                }
                                            })
                                    .filter(Objects::nonNull)
                                    .collect(toSet())));
            if (artifacts.isEmpty()) {
                LOGGER.info("No more artifacts could be found at page {}. Total usable projects: {}.", page, repositories.size());
                if (retries >= 3) {
                    if (pageSize <= 10) {
                        break;
                    } else { //try whether smaller page size will be accepted
                        page -= 3;
                        pageSize /= 2;
                        retries = 0;
                    }
                } else {
                    retries++;
                }
            } else {
                retries = 0;
            }

            artifacts
                    .parallelStream()
                    .filter(distinctByKey(artifact -> artifact.coordinate().groupId() + "-" + artifact.coordinate().artifactId()))
                    .map(artifact -> {
                        File directory = new File(artifactLocation.getAbsolutePath() + File.separator + artifact.getId());
                        try {
                            if (artifact.sourceControlUrl() != null) {
                                return factory.createProject(artifact.sourceControlUrl(), directory);
                            } else {
                                LOGGER.warn("No source control url for artifact {}", artifact);
                                return null;
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Could not create project '{}'", artifact.getId(), e);
                            try {
                                LOGGER.info("Project '{}' is unusable and will be removed", directory.getName());
                                FileUtils.deleteDirectory(directory);
                            } catch (IOException ioe) {
                                LOGGER.info("Could not remove project '{}'", directory.getName(), ioe);
                            }
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(repositories::add);
            LOGGER.info("{} projects collected so far", repositories.size());
            page++;
            try {
                int delayInMs = randomDelay.nextInt(2000) + 1000;
                LOGGER.info("Sleeping for {} ms", delayInMs);
                Thread.sleep(delayInMs);
            } catch (InterruptedException e) {
                LOGGER.warn("Could not sleep for {} ms", randomDelay);
            }
        }

        LOGGER.info("Finished fetching dependent repositories. Found {} dependent projects", repositories.size());
        return repositories;
    }

}
