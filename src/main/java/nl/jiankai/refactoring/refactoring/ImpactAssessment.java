package nl.jiankai.refactoring.refactoring;

import nl.jiankai.refactoring.project.dependencymanagement.ProjectData;

import java.util.List;
import java.util.Map;

public record ImpactAssessment(Map<ProjectData, List<RefactoringImpact>> refactoringImpacts, RefactoringStatistics refactoringStatistics) {
}
