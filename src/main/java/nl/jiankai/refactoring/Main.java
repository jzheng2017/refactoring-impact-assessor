package nl.jiankai.refactoring;

import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.query.JavaParserProjectQuery;
import nl.jiankai.refactoring.core.project.query.MethodUsages;
import nl.jiankai.refactoring.core.project.query.ProjectQuery;
import nl.jiankai.refactoring.core.refactoring.*;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;

import java.util.List;

public class Main {
    public static void main(String[] args) {
//        RefactoringImpactAssessor assessor = new CachedRefactoringImpactAssessor(new JavaParserRefactoringImpactAssessor());
//
//
//        ImpactAssessment assessment = assessor.assesImpact(new RefactoringData("java.io.PrintStream", "println", "java.io.PrintStream.println(java.lang.String)", RefactoringType.METHOD_NAME));
//        System.out.println(assessment.refactoringStatistics());
//
//        ProjectsToScan projectManager = new ProjectsToScan();
//
//        ProjectQuery projectQuery = new JavaParserProjectQuery();
//        List<Project> projects = projectManager.projects();
//        for (int i = 0; i < 10; i++) {
//            List<MethodUsages> usages = projectQuery.mostUsedMethods(projects.get(1), List.of(projects.get(0), projects.get(2)));
//            System.out.println(usages.get(0));
//        }

    }
}
