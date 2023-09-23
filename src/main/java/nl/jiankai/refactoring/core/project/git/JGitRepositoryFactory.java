package nl.jiankai.refactoring.core.project.git;

import nl.jiankai.refactoring.core.project.maven.MavenProjectFactory;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectFactory;
import nl.jiankai.refactoring.util.FileUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class JGitRepositoryFactory implements ProjectFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(JGitRepositoryFactory.class);
    @Override
    public GitRepository createProject(File directory) {
        try {
            return new JGitRepository(Git.init().setGitDir(directory).call(), getProject(directory));
        } catch (GitAPIException e) {
            throw new GitOperationException("Could not create local git repository", e);
        }
    }


    private Project getProject(File directory) {
        ProjectType projectType = detectProjectType(directory);

        if (projectType == ProjectType.MAVEN) {
            return new MavenProjectFactory().createProject(directory);
        } else if (projectType == ProjectType.UNKNOWN) {
            LOGGER.warn("Could not construct the git project as it's using an unsupported project type. Supported project types are {}", ProjectType.values());
        }

        throw new IllegalStateException("Could not get the project");
    }

    private ProjectType detectProjectType(File projectRoot) {
        if (FileUtil.findPomFile(projectRoot).exists()) {
            return ProjectType.MAVEN;
        }

        return ProjectType.UNKNOWN;
    }

    private enum ProjectType {
        MAVEN,
        UNKNOWN
    }
}
