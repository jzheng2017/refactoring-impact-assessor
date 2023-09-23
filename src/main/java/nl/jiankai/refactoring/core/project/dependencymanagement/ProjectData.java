package nl.jiankai.refactoring.core.project.dependencymanagement;

import java.io.File;

public record ProjectData(ProjectCoordinate coordinate, File pathToProject) {

    @Override
    public String toString() {
        return coordinate.toString();
    }
}
