package nl.jiankai.refactoring;

import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.core.project.LocalFileProjectDiscovery;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectDiscovery;
import nl.jiankai.refactoring.core.project.git.GitRepository;
import nl.jiankai.refactoring.core.project.git.JGitRepository;
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
import nl.jiankai.refactoring.core.refactoring.javaparser.JavaParserRefactoringImpactAssessor;
import nl.jiankai.refactoring.core.refactoring.refactoringminer.RefactoringMinerRefactoringDetector;
import nl.jiankai.refactoring.core.storage.api.Identifiable;
import nl.jiankai.refactoring.util.JavaParserUtil;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toMap;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String baseLocation = ApplicationConfiguration.applicationAllProjectsLocation();

    public static void main(String[] args) throws URISyntaxException, MalformedURLException {
//        RefactoringImpactAssessor assessor = new CachedRefactoringImpactAssessor(new JavaParserRefactoringImpactAssessor());
//
//
//        ImpactAssessment assessment = assessor.assesImpact(new RefactoringData("java.io.PrintStream", "println", "java.io.PrintStream.println(java.lang.String)", RefactoringType.METHOD_NAME));
//        System.out.println(assessment.refactoringStatistics());
////

//        library
//        GitRepository apacheProject = new JGitRepositoryFactory().createProject(new File("/Users/Jiankai/IdeaProjects/commons-text"));
//        //projects using the library
//        GitRepository test = new JGitRepositoryFactory().createProject(new File("/Users/Jiankai/ref-plugin/projects/jzheng2017-plugin-test-repo"));
//        GitRepository test2 = new JGitRepositoryFactory().createProject(new File("/Users/Jiankai/ref-plugin/projects/jzheng2017-plugin-test-repo-2"));
//
//
//        ProjectQuery projectQuery = new JavaParserProjectQuery();
//        List<MethodUsages> usages = projectQuery.mostUsedMethods(apacheProject, List.of(test, test2));
//        usages.stream().filter(methodUsages -> methodUsages.usages() > 0).limit(5).forEach(System.out::println);
//

        //refactoring between two commits
//        GitRepository gitRepository = new JGitRepositoryFactory().createProject(new File("/Users/Jiankai/IdeaProjects/commons-text"));
//        RefactoringDetector refactoringDetector = new RefactoringMinerRefactoringDetector();
//        Collection<Refactoring> refactorings = refactoringDetector.detectRefactoringBetweenCommit(gitRepository, "59b6954", "606b568", Set.of(RefactoringType.METHOD_NAME, RefactoringType.METHOD_SIGNATURE));
//        refactorings.forEach(System.out::println);

//        List<GitRepository> repositories = getDependentRepositories(new Artifact.Coordinate("org.apache.commons", "commons-text", "1.10.0"), 100);
//        repositories.forEach(System.out::println);

        Artifact.Coordinate parentArtifact = new Artifact.Coordinate("org.apache.commons", "commons-text", "1.10.0");
        Dependency dependency = new Dependency("org.apache.commons", "commons-text", "1.10.0");
        JGitProjectQuery gitProjectQuery = new JGitProjectQuery();
        ProjectDiscovery projectDiscovery = new LocalFileProjectDiscovery(createProjectLocation(parentArtifact).getAbsolutePath());
        Map<Project, Optional<String>> projects = projectDiscovery
                .discover()
                .parallel()
                .collect(toMap(p->p, project -> {
                    try {
                        return gitProjectQuery.findLatestVersionWithDependency(project, dependency);
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                }));

        projects.forEach((key, value) -> System.out.println(key + "- hash: " + value.orElse("no hash found")));
        System.out.println(projects.values().stream().filter(Optional::isEmpty).count());
        System.out.println(projects.size());
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
                .filter(p -> p instanceof GitRepository)
                .map(p -> (GitRepository) p)
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
