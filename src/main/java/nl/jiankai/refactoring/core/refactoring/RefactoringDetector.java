package nl.jiankai.refactoring.core.refactoring;

import nl.jiankai.refactoring.core.project.git.GitRepository;

import java.util.Collection;
import java.util.Set;

public interface RefactoringDetector {

    Collection<Refactoring> detectRefactoringBetweenCommit(GitRepository gitRepository, String startCommitId, String endCommitId, Set<RefactoringType> refactoringTypes);
    default Collection<Refactoring> detectRefactoringBetweenCommit(GitRepository gitRepository, String startCommitId, String endCommitId) {
        return detectRefactoringBetweenCommit(gitRepository, startCommitId, endCommitId, Set.of());
    }

}
