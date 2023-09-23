package nl.jiankai.refactoring.core.refactoring.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import nl.jiankai.refactoring.core.refactoring.RefactoringData;
import nl.jiankai.refactoring.core.refactoring.RefactoringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class JavaParserUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaParserUtil.class);

    public static List<MethodCallExpr> getMethodUsages(CompilationUnit compilationUnit, String qualifiedName, String methodName) {

        AtomicLong failedResolves = new AtomicLong();
        AtomicLong totalResolveAttempts = new AtomicLong();
        List<MethodCallExpr> methodsUsages = compilationUnit.findAll(MethodCallExpr.class, methodCall -> {
            try {
                if (Objects.equals(methodCall.getNameAsString(), methodName)) {
                    totalResolveAttempts.getAndIncrement();
                    return Objects.equals(qualifiedName, methodCall.resolve().getQualifiedSignature());
                }
                return false;
            } catch (Exception ex) {
                failedResolves.getAndIncrement();
                return false;
            }
        });

        if (failedResolves.get() > 0) {
            LOGGER.warn("{} out of {} methods that matched the pattern {} could not be resolved correctly", failedResolves.get(), totalResolveAttempts, qualifiedName);
        }

        return methodsUsages;
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
