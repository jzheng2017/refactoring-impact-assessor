package nl.jiankai.refactoring.core.project.query;

import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.git.GitRepository;
import nl.jiankai.refactoring.core.project.git.JGitRepository;
import nl.jiankai.refactoring.core.refactoring.javaparser.Dependency;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class JGitProjectQuery implements ProjectQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(JGitProjectQuery.class);

    @Override
    public List<MethodUsages> mostUsedMethods(Project provider, Collection<? extends Project> users) {
        return new JavaParserProjectQuery().mostUsedMethods(provider, users);
    }

    @Override
    public Optional<String> findLatestVersionWithDependency(Project project, Dependency dependency) {
        if (project instanceof JGitRepository gitRepository) {
            try {
                Git git = gitRepository.getGit();
                Repository repository = git.getRepository();
                ObjectId head = repository.resolve(Constants.HEAD);
                Iterable<RevCommit> iterable = git.log().add(head).addPath("pom.xml").call();
                for (RevCommit rev : iterable) {
                    gitRepository.checkout(rev.getName(), List.of("pom.xml"));
                    if (gitRepository.hasDependency(dependency)) {
                        LOGGER.info("Dependency '{}' found in latest commit '{}' of project '{}'", dependency, rev.getName(), project.getId());
                        return Optional.of(rev.getName());
                    }
                }

                LOGGER.info("Dependency '{}' could not be found in any revision of the project '{}'", dependency, project.getId());
                return Optional.empty();
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Not a JGit project provided");
        }
    }
}
