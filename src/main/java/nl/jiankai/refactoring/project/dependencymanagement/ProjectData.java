package nl.jiankai.refactoring.project.dependencymanagement;

import java.io.File;

public record ProjectData(String groupId, String artifactId, String version, File pathToProject) {


    @Override
    public String toString() {
        return groupId + "-" + artifactId + "-" + version;
    }
}
