package nl.jiankai.refactoring.core.project.query;

import nl.jiankai.refactoring.core.project.Project;

import java.util.Collection;
import java.util.List;

/**
 * An interface allowing you to query information about the project
 */
public interface ProjectQuery {

    /**
     * Computes the most used public methods from the provider project by the user projects
     * @param provider the project that provides the methods
     * @param users the projects that uses the methods from the provider project
     * @return an ordered list of methods from highest to lowest
     */
    List<MethodUsages> mostUsedMethods(Project provider, Collection<Project> users);
}
