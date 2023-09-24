package nl.jiankai.refactoring.util;

import nl.jiankai.refactoring.core.project.git.GitOperationException;
import nl.jiankai.refactoring.core.project.git.GitRepository;
import nl.jiankai.refactoring.core.project.git.JGitRepositoryFactory;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;

public class GitUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitUtil.class);

    public static boolean validGitRepository(String url) {
        try {
            return new URIish(url).isRemote();
        } catch (URISyntaxException e) {
            LOGGER.warn("Invalid url: {}", url);
            throw new GitOperationException("Invalid url: %s".formatted(url), e);
        }
    }

    public static GitRepository clone(String url, File repositoryDirectory) {
        if (validGitRepository(url)) {
            CloneCommand cloneCommand = new CloneCommand();
            cloneCommand.setURI(url);
            cloneCommand.setDirectory(repositoryDirectory);
            try (Git git = cloneCommand.call()) {
                return new JGitRepositoryFactory().createProject(repositoryDirectory);
            } catch (GitAPIException e) {
                LOGGER.warn("Could not clone the git repository: {}", e.getMessage());
                throw new GitOperationException("Could not clone the git repository", e);
            }
        } else {
            throw new GitOperationException("Could not clone the git repository with url '%s'".formatted(url));
        }
    }
}
