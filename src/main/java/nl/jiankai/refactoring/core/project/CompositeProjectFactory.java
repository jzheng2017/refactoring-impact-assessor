package nl.jiankai.refactoring.core.project;

import nl.jiankai.refactoring.core.project.git.JGitRepositoryFactory;
import nl.jiankai.refactoring.core.project.maven.MavenProjectFactory;
import nl.jiankai.refactoring.util.FileUtil;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

import java.io.File;

public class CompositeProjectFactory implements ProjectFactory {
    @Override
    public Project createProject(File directory) {
        if (isGitProject(directory)) {
            return new JGitRepositoryFactory().createProject(directory);
        } else if (isMavenProject(directory)) {
            return new MavenProjectFactory().createProject(directory);
        }

        throw new IllegalArgumentException("Unsupported project type at location %s".formatted(directory.getAbsolutePath()));
    }


    private boolean isGitProject(File directory) {
        return RepositoryCache.FileKey.isGitRepository(directory, FS.DETECTED) || RepositoryCache.FileKey.isGitRepository(new File(directory.getAbsolutePath() + File.separator + ".git"), FS.DETECTED);
    }

    private boolean isMavenProject(File file) {
        try {
            return FileUtil.findPomFile(file).exists();
        } catch (FileUtil.FileNotFoundException ex) {
            return false;
        }
    }
}
