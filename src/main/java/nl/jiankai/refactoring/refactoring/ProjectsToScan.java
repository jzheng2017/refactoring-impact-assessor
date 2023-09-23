package nl.jiankai.refactoring.refactoring;

import nl.jiankai.refactoring.configuration.ApplicationConfiguration;
import nl.jiankai.refactoring.project.Project;
import nl.jiankai.refactoring.storage.api.StorageListener;
import nl.jiankai.refactoring.storage.filestorage.LocalFileStorageService;
import nl.jiankai.refactoring.storage.filestorage.repository.FileProjectStorageService;
import nl.jiankai.refactoring.storage.filestorage.repository.ProjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectsToScan implements StorageListener<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectsToScan.class);
    private Set<Project> projects = new HashSet<>();
    private final ProjectStorageService<String> projectStorageService;

    public ProjectsToScan() {
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        projectStorageService = new FileProjectStorageService(new LocalFileStorageService(applicationConfiguration.applicationProjectsToScanLocation(), false));
        projectStorageService.addListener(this);
    }

    public List<Project> projects() {
        if (projects.isEmpty()) {
            projects = projectStorageService.read().collect(Collectors.toSet());
        }

        return List.copyOf(projects);
    }

    @Override
    public void onAdded(StorageEvent<Project> event) {
        Project project = event.affected();
        if (this.projects.contains(project)) {
            LOGGER.warn("Project '{}' has already been added", project);
        } else {
            this.projects.add(project);
        }
    }

    @Override
    public void onUpdated(StorageEvent<Project> event) {

    }

    @Override
    public void onRemoved(StorageEvent<Project> event) {
        Project project = event.affected();
        if (this.projects.contains(project)) {
            this.projects.remove(project);
        } else {
            LOGGER.warn("Project '{}' does not exist", project);
        }
    }
}
