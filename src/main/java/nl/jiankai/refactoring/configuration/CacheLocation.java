package nl.jiankai.refactoring.configuration;

import java.io.File;

public class CacheLocation {
    private static final String cacheBaseLocation = ApplicationConfiguration.cacheDirectory() + File.separator;
    public static final String REFACTORING_IMPACT = cacheBaseLocation + "refactoring-impact";
    public static final String PUBLIC_METHODS = cacheBaseLocation + "public-methods";
    public static final String METHOD_CALLS = cacheBaseLocation + "method-calls";
}
