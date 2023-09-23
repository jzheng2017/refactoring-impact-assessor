package nl.jiankai.refactoring.core.storage.filestorage.repository;

import nl.jiankai.refactoring.core.project.CompositeProjectFactory;
import nl.jiankai.refactoring.core.project.Project;
import nl.jiankai.refactoring.core.storage.api.EntityStorageService;
import nl.jiankai.refactoring.core.storage.api.StorageService;
import nl.jiankai.refactoring.core.storage.api.Mappable;
import nl.jiankai.refactoring.core.storage.api.StorageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ProjectStorageService<T> implements EntityStorageService<Project>, Mappable<Project,T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectStorageService.class);
    private static List<StorageListener<Project>> listeners = new ArrayList<>();
    private StorageService<T> storageService;

    protected ProjectStorageService(StorageService<T> storageService) {
        this.storageService = storageService;
    }

    public void addListener(StorageListener<Project> listener) {
        this.listeners.add(listener);
    }

    public void removeListener(StorageListener<Project> listener) {
        this.listeners.remove(listener);
    }

    @Override
    public Stream<Project> read() {
        return storageService.read().map(this::source);
    }

    @Override
    public Optional<Project> read(String identifier) {
        return read()
                .filter(project -> Objects.equals(identifier, project.getId()))
                .findFirst();
    }

    @Override
    public void write(Project content) {
        writeWithoutNotify(content);
        notifyUpdated(content);
    }

    @Override
    public void write(List<Project> list) {
        writeWithoutNotify(list);
        list.forEach(this::notifyUpdated);
    }

    @Override
    public void append(Project content) {
        storageService.append(target(content));
        notifyAdded(content);
    }

    @Override
    public void append(List<Project> list) {
        storageService.append(list.stream().map(this::target).toList());
        list.forEach(this::notifyAdded);
    }

    @Override
    public void remove(Collection<Project> entities) {
        Set<Project> entitiesSet = new HashSet<>(entities);
        writeWithoutNotify(read().filter(Predicate.not(entitiesSet::contains)).toList());
        entities.forEach(this::notifyRemoved);
    }

    @Override
    public void remove(Project entity) {
        Set<Project> projects = read().collect(Collectors.toSet());
        if (projects.contains(entity)) {
            writeWithoutNotify(projects.stream().filter(project -> !project.equals(entity)).toList());
            notifyRemoved(entity);
        } else {
            LOGGER.warn("Could not remove project {} as it could not be found", entity);
        }
    }

    @Override
    public void remove(String identifier) {
        remove(new CompositeProjectFactory().createProject(new File(identifier)));
    }

    @Override
    public void remove(List<String> identifiers) {
        identifiers.forEach(this::remove);
    }

    @Override
    public boolean exists(String identifier) {
        return read().anyMatch(project -> Objects.equals(identifier, project.getId()));
    }

    @Override
    public void clear() {
        storageService.clear();
    }

    private void notifyAdded(Project project) {
        listeners.forEach(listener -> listener.onAdded(new StorageListener.StorageEvent<>(project)));
    }

    private void notifyUpdated(Project project) {
        listeners.forEach(listener -> listener.onUpdated(new StorageListener.StorageEvent<>(project)));
    }

    private void notifyRemoved(Project project) {
        listeners.forEach(listener -> listener.onRemoved(new StorageListener.StorageEvent<>(project)));
    }

    private void writeWithoutNotify(Project content) {
        storageService.write(target(content));
    }

    private void writeWithoutNotify(List<Project> list) {
        storageService.write(list.stream().map(this::target).toList());
    }
}
