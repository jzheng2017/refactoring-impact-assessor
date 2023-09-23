package nl.jiankai.refactoring.project;

import java.util.stream.Stream;


public interface ProjectDiscovery {

    /**
     * Discover all projects within a space
     * @return the list of all discovered projects
     */
    Stream<Project> discover();
}
