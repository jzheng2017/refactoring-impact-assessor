package nl.jiankai.refactoring.configuration;

public class ApplicationConfiguration {
    private final String applicationAssetsBaseDirectory = "/home/jiankai/Documents/ref-plugin";

    public String applicationAssetsBaseDirectory() {
        return applicationAssetsBaseDirectory;
    }

    public String applicationProjectsToScanLocation() {
        return applicationAssetsBaseDirectory + "/projects.txt";
    }

    public String applicationAllProjectsLocation() {
        return applicationAssetsBaseDirectory + "/projects";
    }
    public String cacheDirectory() {
        return applicationAssetsBaseDirectory + "/cache";
    }
}
