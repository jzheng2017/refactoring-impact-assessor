package nl.jiankai.refactoring;

import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.git.GitRepository;
import nl.jiankai.refactoring.core.project.git.JGitRepository;
import nl.jiankai.refactoring.core.project.git.JGitRepositoryFactory;
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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main {
    public static void main(String[] args) {
        String baseLocation = ApplicationConfiguration.applicationAllProjectsLocation();
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


//        ArtifactRepository artifactRepository = new MavenCentralRepository();
//        List<Artifact> artifacts = artifactRepository.getArtifactUsages(new Artifact.Coordinate("org.apache.commons", "commons-text", "1.10.0"), new ArtifactRepository.PageOptions(0, 200));
//        artifacts
//                .stream().filter(distinctByKey(artifact -> artifact.coordinate().groupId() + "-" + artifact.coordinate().artifactId()))
//                .forEach(System.out::println);

        Dependency dependency = new Dependency("org.apache.commons", "commons-text", "1.10.0");
//        JGitRepository gitRepository = new JGitRepositoryFactory().createProject("https://github.com/apache/commons-text", new File("/home/jiankai/Documents/refactoring-storage/projects/commons-text"));
        JGitRepository gitRepository = new JGitRepositoryFactory().createProject("https://github.com/limaofeng/jfantasy-framework", new File("/home/jiankai/Documents/refactoring-storage/projects/jfantasy-framework"));
//        System.out.println(gitRepository.hasDependency(new Dependency("org.apache.commons", "commons-text", "1.10.0")));
        try {
            Git git = gitRepository.getGit();
            Repository repository = git.getRepository();
            ObjectId head = repository.resolve(Constants.HEAD);
            Iterable<RevCommit> iterable = git.log().add(head).addPath("pom.xml").call();
            int i = 1;
            for (RevCommit rev : iterable) {
                System.out.println("iteration " + i++);
                git
                        .checkout()
                        .addPath("pom.xml")
                        .setStartPoint(rev)
                        .call();
                if (gitRepository.hasDependency(dependency)) {
                    System.out.println("dependency found in pom");
                    break;
                }
            }
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
