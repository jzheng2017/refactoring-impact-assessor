package nl.jiankai.refactoring.core.refactoring;

public record RefactoringImpact(String filePath, String fileName, String packageLocation, String className, String elementName, Position position, boolean breakingChange) {

    @Override
    public String toString() {
        return "package: %s | class: %s | line %s position %s".formatted(packageLocation, className, position.rowStart(), position.columnStart());
    }
}
