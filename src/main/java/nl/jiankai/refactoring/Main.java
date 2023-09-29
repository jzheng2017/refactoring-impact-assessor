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
import nl.jiankai.refactoring.core.refactoring.*;
import nl.jiankai.refactoring.core.refactoring.javaparser.JavaParserRefactoringImpactAssessor;
import nl.jiankai.refactoring.core.refactoring.refactoringminer.RefactoringMinerRefactoringDetector;
import nl.jiankai.refactoring.util.JavaParserUtil;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.jgit.api.Git;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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

        try {
            Document document = Jsoup.connect("https://central.sonatype.com/artifact/ca.uhn.hapi.fhir/hapi-fhir-server-mdm/6.8.3").get();
            String githubLink = document.getElementsByAttributeValue("data-test", "scm-url").get(0).attr("href");
            JGitRepository jGitRepository = new JGitRepositoryFactory().createProject(githubLink, new File(baseLocation + "/wow"));
            System.out.println(jGitRepository.getProjectVersion());
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}
