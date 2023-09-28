package nl.jiankai.refactoring.core.refactoring.refactoringminer;

import gr.uom.java.xmi.diff.*;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.git.GitRepository;
import nl.jiankai.refactoring.core.refactoring.Refactoring;
import nl.jiankai.refactoring.core.refactoring.RefactoringDetector;
import nl.jiankai.refactoring.core.refactoring.RefactoringType;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class RefactoringMinerRefactoringDetector implements RefactoringDetector {
    @Override
    public Collection<Refactoring> detectRefactoringBetweenCommit(GitRepository gitRepository, String startCommitId, String endCommitId, Set<RefactoringType> refactoringTypes) {
        List<Refactoring> detectedRefactorings = new ArrayList<>();
        GitService gitService = new GitServiceImpl();
        GitHistoryRefactoringMiner gitHistoryRefactoringMiner = new GitHistoryRefactoringMinerImpl();
        try (Repository repository = gitService.openRepository(gitRepository.getLocalPath().getAbsolutePath())) {

            gitHistoryRefactoringMiner.detectBetweenCommits(repository, startCommitId, endCommitId, new RefactoringHandler() {
                @Override
                public void handle(String commitId, List<org.refactoringminer.api.Refactoring> refactorings) {
                    detectedRefactorings
                            .addAll(
                                    refactorings
                                            .stream()
                                            .filter(r -> refactoringTypes.contains(convertRefactoringType(r.getRefactoringType())))
                                            .map(r -> new Refactoring(getElementName(r), convertRefactoringType(r.getRefactoringType())))
                                            .toList()
                            );
                }
            });

            return detectedRefactorings;
        } catch (Exception e) {
            throw new IllegalArgumentException("Something went wrong with the repository '%s'".formatted(gitRepository.getId()), e);
        }
    }

    private String getElementName(org.refactoringminer.api.Refactoring refactoring) {
        if (refactoring instanceof ChangeReturnTypeRefactoring crtr) {
            return crtr.getOperationBefore().getName();
        } else if (refactoring instanceof AddParameterRefactoring apr) {
            return apr.getOperationBefore().getName();
        } else if (refactoring instanceof RemoveParameterRefactoring rpr) {
            return rpr.getOperationBefore().getName();
        } else if (refactoring instanceof ChangeVariableTypeRefactoring cvtr) {
            return cvtr.getOperationBefore().getName();
        } else if (refactoring instanceof RenameOperationRefactoring ror) {
            return ror.getOriginalOperation().getName();
        }

        return "";
    }


    private RefactoringType convertRefactoringType(org.refactoringminer.api.RefactoringType type) {
        return switch (type) {
            case RENAME_METHOD -> RefactoringType.METHOD_NAME;
            case CHANGE_RETURN_TYPE, CHANGE_PARAMETER_TYPE, REMOVE_PARAMETER, ADD_PARAMETER ->
                    RefactoringType.METHOD_SIGNATURE;
            default -> RefactoringType.UNKNOWN;
        };
    }
}
