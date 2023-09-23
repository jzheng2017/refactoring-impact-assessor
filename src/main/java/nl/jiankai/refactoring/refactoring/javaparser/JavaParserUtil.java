package nl.jiankai.refactoring.refactoring.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import nl.jiankai.refactoring.refactoring.RefactoringData;
import nl.jiankai.refactoring.refactoring.RefactoringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static nl.jiankai.refactoring.refactoring.RefactoringType.METHOD_NAME;
import static nl.jiankai.refactoring.refactoring.RefactoringType.METHOD_SIGNATURE;

public class JavaParserUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParserUtil.class);
    public static List<MethodCallExpr> getMethodUsages(CompilationUnit compilationUnit, String qualifiedName) {
        return compilationUnit.findAll(MethodCallExpr.class, methodCall -> {
            try {
                return Objects.equals(qualifiedName, methodCall.resolve().getQualifiedSignature());
            } catch (Exception ex) {
                LOGGER.warn("Something went wrong while computing method usages", ex);
                return false;
            }
        });
    }

    public static boolean isBreakingChange(MethodCallExpr methodCallExpr, RefactoringData refactoringData) {
        RefactoringType refactoringType = refactoringData.refactoringType();

        return switch (refactoringType) {
            case METHOD_NAME -> true;
            case METHOD_SIGNATURE -> computeMethodSignatureIsBreaking(methodCallExpr);
            default -> false;
        };
    }

    private static boolean computeMethodSignatureIsBreaking(MethodCallExpr methodCallExpr) {
        return false;
    }
}
