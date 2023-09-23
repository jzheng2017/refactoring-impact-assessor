package nl.jiankai.refactoring.project.dependencymanagement;

import nl.jiankai.refactoring.refactoring.javaparser.Dependency;
import nl.jiankai.refactoring.util.FileUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.shared.invoker.*;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MavenProjectDependencyResolver implements ProjectDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenProjectDependencyResolver.class);

    @Override
    public Collection<Dependency> resolve(File projectRootPath) {
        try {
            Model model = parsePomFile(projectRootPath);
            Properties properties = model.getProperties();
            return model
                    .getDependencies()
                    .stream()
                    .map(dependency -> new Dependency(dependency.getGroupId(), dependency.getArtifactId(), resolveProperty(properties, dependency.getVersion())))
                    .toList();
        } catch (FileUtil.FileNotFoundException | IOException | XmlPullParserException e) {
            LOGGER.warn("Could not resolve project dependencies for project path '{}'", projectRootPath, e);
            return new ArrayList<>();
        }
    }

    @Override
    public Collection<File> jars(File projectRootPath) {
        return findProjectDependencyJars(resolve(projectRootPath));
    }

    private List<File> findProjectDependencyJars(Collection<Dependency> projectDependencies) {
        File repositoryLocation = getMavenRepositoryLocation();
        Set<Dependency> dependencies = new HashSet<>(projectDependencies);
        Set<String> fileNames = dependencies.stream().map(this::createJarName).collect(Collectors.toSet());

        List<File> foundJars = findJarsRecursive(repositoryLocation, fileNames).toList();
        if (foundJars.size() != dependencies.size()) {
            List<String> jarsNotFound = getMissingJars(fileNames, foundJars.stream().map(File::getName).collect(Collectors.toSet()));
            LOGGER.warn("Not all jars were found. {} jars were found of the {} dependencies", foundJars.size(), dependencies.size());
            LOGGER.warn("The following jars are missing: {}", jarsNotFound);
        }
        return foundJars;
    }

    @Override
    public void install(File projectRootPath) {
        if (dependenciesAlreadySatisfied(projectRootPath)) {
            LOGGER.info("[{}]: Installing project dependencies is not necessary. All dependencies have already been satisfied.", projectRootPath);
        } else {
            File file = FileUtil.findPomFile(projectRootPath);
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(file);
            request.setGoals(Collections.singletonList("compile"));
            Invoker invoker = new DefaultInvoker();

            try {
                invoker.execute(request);
            } catch (MavenInvocationException e) {
                LOGGER.warn("Could not install dependencies for project on path '{}'", projectRootPath.getPath(), e);
            }
        }
    }

    @Override
    public ProjectData getProjectVersion(File projectRootPath) {
        try {
            Model pom = parsePomFile(projectRootPath);
            return new ProjectData(pom.getGroupId(), pom.getArtifactId(), pom.getVersion(), projectRootPath);
        } catch (IOException | XmlPullParserException e) {
            LOGGER.warn("Could not get project version. Reason {}", e.getMessage(), e);
            throw new ProjectResolveException(e.getMessage(), e);
        }
    }

    private Model parsePomFile(File projectRootPath) throws IOException, XmlPullParserException {
        File file = FileUtil.findPomFile(projectRootPath);
        MavenXpp3Reader reader = new MavenXpp3Reader();
        return reader.read(new FileReader(file));
    }

    private boolean dependenciesAlreadySatisfied(File projectRootPath) {
        Collection<Dependency> projectDependencies = resolve(projectRootPath);

        return projectDependencies.size() == findProjectDependencyJars(projectDependencies).size();
    }

    private List<String> getMissingJars(Set<String> fileNames, Set<String> foundJars) {
        List<String> missingJars = new ArrayList<>();

        for (String fileName : fileNames) {
            if (!foundJars.contains(fileName)) {
                missingJars.add(fileName);
            }
        }

        return missingJars;
    }

    private Stream<File> findJarsRecursive(File directory, Set<String> fileNames) {
        return FileUtils
                .listFiles(directory, FileFilterUtils.suffixFileFilter(".jar"), TrueFileFilter.INSTANCE)
                .stream()
                .filter(file -> fileNames.contains(file.getName()));
    }

    private String createJarName(Dependency dependency) {
        return dependency.artifactId() + "-" + dependency.version() + ".jar";
    }

    private String resolveProperty(Properties properties, String property) {
        if (property.startsWith("${") && property.endsWith("}")) {
            return (String)properties.getOrDefault(property.substring(2, property.length() - 1), property);
        }

        return property;
    }

    private File getMavenRepositoryLocation() {
        String location = System.getenv("MAVEN_HOME");

        if (location == null) {
            location = System.getenv("MVN_HOME");
        }

        if (location == null) {
            location = System.getenv("M2_HOME");
        }

        if (location == null) {
            location = System.getenv("HOME") + "/.m2";
        }

        return new File(location);
    }
}
