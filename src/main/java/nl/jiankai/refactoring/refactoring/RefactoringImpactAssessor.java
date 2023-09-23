package nl.jiankai.refactoring.refactoring;

import nl.jiankai.refactoring.project.dependencymanagement.ProjectData;

import java.util.List;

public interface RefactoringImpactAssessor {
    /**
     * Asses the impact of the performed refactoring action
     * @param refactoringData all data related to the refactoring action
     * @return the impact of the change to all its dependents
     */
    ImpactAssessment assesImpact(RefactoringData refactoringData);

    List<RefactoringImpact> assesImpact(ProjectData projectData, RefactoringData refactoringData);
}
