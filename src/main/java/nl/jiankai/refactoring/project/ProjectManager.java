package nl.jiankai.refactoring.project;

import nl.jiankai.refactoring.tasks.ScheduledTask;
import nl.jiankai.refactoring.tasks.ScheduledTaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ProjectManager implements ProjectObservable<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectManager.class);
    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private ScheduledTaskExecutorService<Void> executorService = new ScheduledTaskExecutorService<>();
    private ProjectDiscovery projectDiscovery = new LocalFileProjectDiscovery();
    private List<ProjectListener<Project>> listeners = new ArrayList<>();

    public ProjectManager() {
        discoverProjects();
        submitProjectDiscoverTask();
    }

    private void submitProjectDiscoverTask() {
        executorService.executeRecurringTask(
                ScheduledTask.builder(Void.class)
                        .task(() -> {
                            discoverProjects();
                            return null;
                        })
                        .period(10)
                        .periodTimeUnit(TimeUnit.SECONDS)
                        .recurring()
                        .build()
        );
    }

    private void discoverProjects() {
        LOGGER.trace("Discovering projects...");
        try {
            synchronized (projects) {
                Map<String, Project> existingProjects = new ConcurrentHashMap<>(projects);
                projectDiscovery
                        .discover()
                        .parallel()
                        .forEach(project -> {
                            String projectId = project.getId();
                            existingProjects.remove(projectId);

                            if (!projects.containsKey(projectId)) {
                                LOGGER.info("Discovered new project: '{}'", projectId);
                                projects.put(projectId, project);
                                project.install();
                                notifyAdded(project);
                            }
                        });

                existingProjects
                        .values()
                        .forEach(project -> {
                            String projectId = project.getId();
                            projects.remove(projectId);
                            LOGGER.info("Project '{}' has been removed as it's not available anymore", projectId);
                            notifyRemoved(project);
                        });
            }
        } catch (Exception e) {
            LOGGER.warn("Something went wrong while discovering projects: {}", e.getMessage(), e);
        }
    }

    public Map<String, Project> projects() {
        return Map.copyOf(projects);
    }

    @Override
    public void addListener(ProjectListener<Project> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ProjectListener<Project> listener) {
        listeners.remove(listener);
    }

    public void notifyAdded(Project project) {
        listeners.forEach(listener -> listener.onAdded(new ProjectListener.ProjectEvent<>(project)));
    }

    public void notifyRemoved(Project project) {
        listeners.forEach(listener -> listener.onRemoved(new ProjectListener.ProjectEvent<>(project)));
    }
}
