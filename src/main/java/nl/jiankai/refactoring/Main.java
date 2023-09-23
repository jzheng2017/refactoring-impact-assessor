package nl.jiankai.refactoring;

import nl.jiankai.refactoring.refactoring.*;
import nl.jiankai.refactoring.refactoring.javaparser.JavaParserRefactoringImpactAssessor;

public class Main {
    public static void main(String[] args) {
        RefactoringImpactAssessor assessor = new CachedRefactoringImpactAssessor(new JavaParserRefactoringImpactAssessor());


        ImpactAssessment assessment = assessor.assesImpact(new RefactoringData("java.io.PrintStream", "println", "java.io.PrintStream.println(java.lang.String)", RefactoringType.METHOD_NAME));
        System.out.println(assessment.refactoringStatistics());
    }
}
