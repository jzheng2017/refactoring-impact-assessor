package nl.jiankai.refactoring.core.project;

import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.tasks.ScheduledTask;
import nl.jiankai.refactoring.tasks.ScheduledTaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public final class LocalFileProjectDiscovery implements ProjectDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileProjectDiscovery.class);
    private ScheduledTaskExecutorService<Stream<Project>> executorService = new ScheduledTaskExecutorService<>();
    private ProjectFactory projectFactory;
    private final String locationToScan;

    public LocalFileProjectDiscovery(String locationToScan) {
        projectFactory = new CompositeProjectFactory();
        this.locationToScan = locationToScan;
    }

    @Override
    public Stream<Project> discover() {
        try {
            createDirectoryIfMissing(ApplicationConfiguration.applicationAllProjectsLocation());
            return executorService.executeTask(
                            ScheduledTask
                                    .builder((Class<Stream<Project>>) null)
                                    .task(this::scan)
                                    .build())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Something went wrong while discovering projects", e);
            return Stream.empty();
        }
    }

    private Stream<Project> scan() {
        createDirectoryIfMissing(locationToScan);
        return getAllSubDirectories(locationToScan)
                .stream()
                .filter(file -> file.isDirectory() && !file.getName().startsWith("."))
                .map(dir -> {
                    try {
                        return projectFactory.createProject(dir);
                    } catch (Exception e) {
                        LOGGER.warn("Could not create project {}", dir, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }

    private List<File> getAllSubDirectories(String directory) {
        return Arrays
                .stream(
                        Objects.requireNonNull(new File(directory).listFiles(File::isDirectory), "The project directory '%s' does not exist..".formatted(directory))
                ).toList();
    }

    private void createDirectoryIfMissing(String location) {
        File projectDirectory = new File(location);

        if (!projectDirectory.exists()) {
            String projectPath = projectDirectory.getAbsolutePath();
            LOGGER.warn("Project directory missing at {}", projectPath);
            if (projectDirectory.mkdirs()) {
                LOGGER.info("Project directory has been created at {}", projectPath);
            } else {
                LOGGER.warn("Could not create a directory for projects at {}", projectPath);
            }
        }
    }
}
