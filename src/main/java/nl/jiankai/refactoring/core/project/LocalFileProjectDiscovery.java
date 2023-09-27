package nl.jiankai.refactoring.core.project;

import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.core.project.git.JGitRepositoryFactory;
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

    public LocalFileProjectDiscovery() {
        projectFactory = new JGitRepositoryFactory();
    }

    @Override
    public Stream<Project> discover() {
        try {
            createProjectDirectoryIfMissing();
            return executorService.executeTask(
                            ScheduledTask
                                    .builder((Class<Stream<Project>>)null)
                                    .task(() -> this.scan(ApplicationConfiguration.applicationAllProjectsLocation()))
                                    .build())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Something went wrong while discovering projects", e);
            return Stream.empty();
        }
    }

    private Stream<Project> scan(String directory) {
        return getAllSubDirectories(directory)
                .stream()
                .filter(file -> file.isDirectory() && !file.getName().startsWith("."))
                .map(dir -> projectFactory.createProject(dir));
    }

    private List<File> getAllSubDirectories(String directory) {
        return Arrays
                .stream(
                        Objects.requireNonNull(new File(directory).listFiles(File::isDirectory), "The project directory '%s' does not exist..".formatted(directory))
                ).toList();
    }

    private void createProjectDirectoryIfMissing() {
        File projectDirectory = new File(ApplicationConfiguration.applicationAllProjectsLocation());

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
