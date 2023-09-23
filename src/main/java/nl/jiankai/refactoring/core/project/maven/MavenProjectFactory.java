package nl.jiankai.refactoring.core.project.maven;


import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectFactory;

import java.io.File;

public class MavenProjectFactory implements ProjectFactory {
    @Override
    public Project createProject(File directory) {
        return new MavenProject(directory);
    }
}
