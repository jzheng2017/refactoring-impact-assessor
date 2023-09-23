package nl.jiankai.refactoring.refactoring;

public record RefactoringData(String packageLocation, String elementName, String fullyQualifiedSignature, RefactoringType refactoringType) {
}
