package nl.jiankai.refactoring.project;

public interface ProjectObservable<T> {

    void addListener(ProjectListener<T> listener);
    void removeListener(ProjectListener<T> listener);
}
