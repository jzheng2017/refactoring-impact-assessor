package nl.jiankai.refactoring.project.git;

public class GitOperationException extends RuntimeException {

    public GitOperationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
