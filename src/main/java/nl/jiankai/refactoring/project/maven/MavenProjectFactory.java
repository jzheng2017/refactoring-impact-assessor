package nl.jiankai.refactoring.project.maven;


import nl.jiankai.refactoring.project.Project;
import nl.jiankai.refactoring.project.ProjectFactory;

import java.io.File;

public class MavenProjectFactory implements ProjectFactory {
    @Override
    public Project createProject(File directory) {
        return new MavenProject(directory);
    }
}
