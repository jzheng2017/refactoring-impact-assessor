package nl.jiankai.refactoring;

import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.git.GitRepository;
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

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
//        RefactoringImpactAssessor assessor = new CachedRefactoringImpactAssessor(new JavaParserRefactoringImpactAssessor());
//
//
//        ImpactAssessment assessment = assessor.assesImpact(new RefactoringData("java.io.PrintStream", "println", "java.io.PrintStream.println(java.lang.String)", RefactoringType.METHOD_NAME));
//        System.out.println(assessment.refactoringStatistics());
////

        //library
//        GitRepository apacheProject = new JGitRepositoryFactory().createProject(new File("/home/jiankai/Documents/ref-plugin/projects/commons-text"));
//        //projects using the library
//        GitRepository test = new JGitRepositoryFactory().createProject(new File("/home/jiankai/Documents/ref-plugin/projects/plugin-test-repo"));
//        GitRepository test2 = new JGitRepositoryFactory().createProject(new File("/home/jiankai/Documents/ref-plugin/projects/plugin-test-repo-2"));
//
//
//        ProjectQuery projectQuery = new JavaParserProjectQuery();
//        List<MethodUsages> usages = projectQuery.mostUsedMethods(apacheProject, List.of(test, test2));
//        usages.stream().limit(5).forEach(System.out::println);


        //refactoring between two commits
        GitRepository gitRepository = new JGitRepositoryFactory().createProject(new File("/home/jiankai/Documents/ref-plugin/projects/commons-text"));
        RefactoringDetector refactoringDetector = new RefactoringMinerRefactoringDetector();
        Collection<Refactoring> refactorings = refactoringDetector.detectRefactoringBetweenCommit(gitRepository, "59b6954", "606b568", Set.of(RefactoringType.METHOD_NAME, RefactoringType.METHOD_SIGNATURE));
        refactorings.forEach(System.out::println);
    }
}
