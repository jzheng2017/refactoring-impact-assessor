package nl.jiankai.refactoring.core.refactoring.refactoringminer;

import gr.uom.java.xmi.diff.*;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.project.git.GitRepository;
import nl.jiankai.refactoring.core.refactoring.Position;
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
                                            .map(r -> new Refactoring(commitId, getElementName(r), convertRefactoringType(r.getRefactoringType()), getPackagePath(r), getPosition(r), getFilePath(r)))
                                            .toList()
                            );
                }
            });

            return detectedRefactorings;
        } catch (Exception e) {
            throw new IllegalArgumentException("Something went wrong with the repository '%s'".formatted(gitRepository.getId()), e);
        }
    }

    private String getFilePath(org.refactoringminer.api.Refactoring refactoring) {
        if (refactoring instanceof ChangeReturnTypeRefactoring crtr) {
            return crtr.getOperationBefore().getLocationInfo().getFilePath();
        } else if (refactoring instanceof AddParameterRefactoring apr) {
            return apr.getOperationBefore().getLocationInfo().getFilePath();
        } else if (refactoring instanceof RemoveParameterRefactoring rpr) {
            return rpr.getOperationBefore().getLocationInfo().getFilePath();
        } else if (refactoring instanceof ChangeVariableTypeRefactoring cvtr) {
            return cvtr.getOperationBefore().getLocationInfo().getFilePath();
        } else if (refactoring instanceof RenameOperationRefactoring ror) {
            return ror.getOriginalOperation().getLocationInfo().getFilePath();
        }

        return "";
    }

    private Position getPosition(org.refactoringminer.api.Refactoring refactoring) {
        CodeRange codeRange = null;
        if (refactoring instanceof ChangeReturnTypeRefactoring crtr) {
            codeRange = crtr.getOperationBefore().codeRange();
        } else if (refactoring instanceof AddParameterRefactoring apr) {
            codeRange = apr.getOperationBefore().codeRange();
        } else if (refactoring instanceof RemoveParameterRefactoring rpr) {
            codeRange = rpr.getOperationBefore().codeRange();
        } else if (refactoring instanceof ChangeVariableTypeRefactoring cvtr) {
            codeRange = cvtr.getOperationBefore().codeRange();
        } else if (refactoring instanceof RenameOperationRefactoring ror) {
            codeRange = ror.getSourceOperationCodeRangeBeforeRename();
        }

        if (codeRange == null) {
            return null;
        } else {
            return new Position(codeRange.getStartColumn(), codeRange.getEndColumn(), codeRange.getStartLine(), codeRange.getEndLine());
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

    private String getPackagePath(org.refactoringminer.api.Refactoring refactoring) {
        if (refactoring instanceof ChangeReturnTypeRefactoring crtr) {
            return crtr.getOperationBefore().getClassName();
        } else if (refactoring instanceof AddParameterRefactoring apr) {
            return apr.getOperationBefore().getClassName();
        } else if (refactoring instanceof RemoveParameterRefactoring rpr) {
            return rpr.getOperationBefore().getClassName();
        } else if (refactoring instanceof ChangeVariableTypeRefactoring cvtr) {
            return cvtr.getOperationBefore().getClassName();
        } else if (refactoring instanceof RenameOperationRefactoring ror) {
            return ror.getOriginalOperation().getClassName();
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
