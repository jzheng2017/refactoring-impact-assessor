package nl.jiankai.refactoring.tasks;

import java.util.concurrent.Future;

/**
 * A service that allows you to execute tasks
 */
public interface TaskExecutorService<T extends Task<R>, R> {

    /**
     * Execute the provided task
     *
     * @param task the task you want to be executed
     */
    Future<R> executeTask(T task);
}
