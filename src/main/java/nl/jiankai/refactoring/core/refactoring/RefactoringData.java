package nl.jiankai.refactoring.core.refactoring;

public record RefactoringData(String packageLocation, String elementName, String fullyQualifiedSignature, RefactoringType refactoringType) {
}
