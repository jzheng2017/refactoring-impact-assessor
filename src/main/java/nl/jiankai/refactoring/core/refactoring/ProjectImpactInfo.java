package nl.jiankai.refactoring.core.refactoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nl.jiankai.refactoring.core.project.dependencymanagement.ProjectData;
import nl.jiankai.refactoring.core.storage.api.Identifiable;

import java.util.List;

@JsonIgnoreProperties(value = {"id"})
public record ProjectImpactInfo(ProjectData projectData, RefactoringData refactoringData, List<RefactoringImpact> refactoringImpacts) implements Identifiable {

    @Override
    public String getId() {
        return projectData.toString() + "-" + refactoringData.fullyQualifiedSignature() + "-" + refactoringData.refactoringType();
    }

    @Override
    public String toString() {
        return getId();
    }
}
