package nl.jiankai.refactoring.core.project;

public interface ProjectObservable<T> {

    void addListener(ProjectListener<T> listener);
    void removeListener(ProjectListener<T> listener);
}
