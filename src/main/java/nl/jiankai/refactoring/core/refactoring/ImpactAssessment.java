package nl.jiankai.refactoring.core.refactoring;

import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectData;

import java.util.List;
import java.util.Map;

public record ImpactAssessment(Map<ProjectData, List<RefactoringImpact>> refactoringImpacts, RefactoringStatistics refactoringStatistics) {
}
