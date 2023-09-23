package nl.jiankai.refactoring.project;

import java.io.File;

public interface ProjectFactory {
    Project createProject(File directory);
}
