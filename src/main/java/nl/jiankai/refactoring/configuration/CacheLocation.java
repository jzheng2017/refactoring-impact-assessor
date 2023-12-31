package nl.jiankai.refactoring.configuration;

import java.io.File;

public class CacheLocation {
    private static final String cacheBaseLocation = ApplicationConfiguration.cacheDirectory() + File.separator;
    public static final String REFACTORING_IMPACT = cacheBaseLocation + "refactoring-impact";
    public static final String PUBLIC_METHODS = cacheBaseLocation + "public-methods";
    public static final String METHOD_CALLS = cacheBaseLocation + "method-calls";
    public static final String ARTIFACTS = cacheBaseLocation + "artifacts";
    public static final String PROJECT_REFACTORINGS = cacheBaseLocation + "refactoring";
    public static final String DEPENDENTS = cacheBaseLocation + "dependents";
    public static final String PIPELINE_RESULTS = cacheBaseLocation + "results";
}
