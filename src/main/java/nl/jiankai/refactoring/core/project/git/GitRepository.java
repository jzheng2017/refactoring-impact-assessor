package nl.jiankai.refactoring.core.project.git;


import nl.jiankai.refactoring.core.project.Project;

import java.util.List;

public interface GitRepository extends Project {

    void checkout(String commitId, List<String> path);
}
