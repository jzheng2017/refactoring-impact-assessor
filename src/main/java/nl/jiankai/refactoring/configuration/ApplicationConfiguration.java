package nl.jiankai.refactoring.configuration;

public class ApplicationConfiguration {
//    private static final String applicationAssetsBaseDirectory = "/home/jiankai/Documents/refactoring-storage";
    public static String applicationAssetsBaseDirectory = "refactoring-storage";

    public static String applicationAssetsBaseDirectory() {
        return applicationAssetsBaseDirectory;
    }

    public static String applicationProjectsToScanLocation() {
        return applicationAssetsBaseDirectory + "/projects.txt";
    }

    public static String applicationAllProjectsLocation() {
        return applicationAssetsBaseDirectory + "/projects";
    }
    public static String cacheDirectory() {
        return applicationAssetsBaseDirectory + "/cache";
    }
}
