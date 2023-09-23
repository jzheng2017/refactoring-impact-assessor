package nl.jiankai.refactoring.core.project;

import java.io.File;

public interface ProjectFactory {
    Project createProject(File directory);
}
