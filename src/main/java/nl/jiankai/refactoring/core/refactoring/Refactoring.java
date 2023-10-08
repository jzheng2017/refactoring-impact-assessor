package nl.jiankai.refactoring.core.refactoring;

public record Refactoring(String commitId, String elementName, RefactoringType refactoringType, String packagePath, Position position, String filePath) {
}
