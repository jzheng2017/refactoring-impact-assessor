package nl.jiankai.refactoring.project;

import nl.jiankai.refactoring.project.dependencymanagement.ProjectData;
import nl.jiankai.refactoring.storage.api.Identifiable;
import nl.jiankai.refactoring.refactoring.javaparser.Dependency;

import java.io.File;
import java.util.Collection;

public interface Project extends Identifiable {
    /**
     * @return the local file path of the git repository
     */
    File getLocalPath();

    /**
     * Resolves all the dependencies of a project
     * @return all dependencies
     */
    Collection<Dependency> resolve();

    /**
     * Resolves the file locations of all the jars of the dependencies that the project depends on
     * @return list of file locations of the jars
     */
    Collection<File> jars();

    /**
     * Download all dependencies of the project to a local repository
     */
    void install();

    /**
     * @return the project details containing group id, artifact id and the version
     */
    ProjectData getProjectVersion();
}
