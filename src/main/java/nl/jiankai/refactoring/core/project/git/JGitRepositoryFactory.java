package nl.jiankai.refactoring.core.project.git;

import nl.jiankai.refactoring.core.project.ProjectType;
import nl.jiankai.refactoring.core.project.maven.MavenProjectFactory;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.ProjectFactory;
import nl.jiankai.refactoring.util.FileUtil;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class JGitRepositoryFactory implements ProjectFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(JGitRepositoryFactory.class);

    @Override
    public GitRepository createProject(File directory) {
        try {
            return new JGitRepository(Git.open(directory), getProject(directory));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JGitRepository createProject(String repositoryUrl, File repositoryCloneDirectory) {
        if (repositoryCloneDirectory.exists()) {
            LOGGER.info("Git repository '{}' already exists locally", repositoryUrl);
            return (JGitRepository) createProject(repositoryCloneDirectory);
        }

        if (validGitRepository(repositoryUrl)) {
            try (Git git = Git.cloneRepository()
                    .setTimeout(30)
                    .setURI(repositoryUrl)
                    .setDirectory(repositoryCloneDirectory)
                    .call()
            ) {
                return (JGitRepository) createProject(repositoryCloneDirectory);
            } catch (GitAPIException e) {
                LOGGER.warn("Could not clone the git repository: {}", e.getMessage());
                throw new GitOperationException("Could not clone the git repository", e);
            }
        } else {
            throw new GitOperationException("Could not clone the git repository with url '%s'".formatted(repositoryUrl));
        }
    }


    private Project getProject(File directory) {
        ProjectType projectType = detectProjectType(directory);

        if (projectType == ProjectType.MAVEN) {
            return new MavenProjectFactory().createProject(directory);
        } else if (projectType == ProjectType.UNKNOWN) {
            LOGGER.warn("Could not construct the git project as it's using an unsupported project type. Supported project types are {}", ProjectType.values());
        }

        throw new IllegalStateException("Could not get the project at file location '%s'".formatted(directory.getAbsolutePath()));
    }

    private ProjectType detectProjectType(File projectRoot) {
        try {
            if (FileUtil.findPomFile(projectRoot).exists()) {
                return ProjectType.MAVEN;
            }
        } catch (Exception e) {
            return ProjectType.UNKNOWN;
        }

        return ProjectType.UNKNOWN;
    }


    public boolean validGitRepository(String url) {
        try {
            return new URIish(url).isRemote();
        } catch (URISyntaxException e) {
            LOGGER.warn("Invalid url: {}", url);
            throw new GitOperationException("Invalid url: %s".formatted(url), e);
        }
    }

}
