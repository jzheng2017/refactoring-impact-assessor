package nl.jiankai.refactoring.tasks;

import java.util.concurrent.Callable;

public abstract class Task<T> {
    private Callable<T> task;
    public Task(Callable<T> task) {
        this.task = task;
    }
    public Callable<T> getTask() {
        return task;
    }
}
