package nl.jiankai.refactoring.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A service that allows you to schedule the execution of tasks
 */
public class ScheduledTaskExecutorService<T> implements TaskExecutorService<ScheduledTask<T>, T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskExecutorService.class);
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();


    @Override
    public Future<T> executeTask(ScheduledTask<T> task) {
        LOGGER.trace("Scheduling non-recurring task");
        return executorService.schedule(task.getTask(), task.delay(), TimeUnit.SECONDS);
    }

    public Future<?> executeRecurringTask(ScheduledTask<T> task) {
        if (task.isRecurring()) {
            LOGGER.info("Scheduling recurring task with period of {} {} with a delay of {} seconds", task.period(), task.periodTimeUnit().toString(), task.delay());
            return executorService.scheduleWithFixedDelay(() -> {
                try {
                    task.getTask().call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, task.delay(), task.period(), TimeUnit.SECONDS);
        } else {
            throw new IllegalArgumentException("A non recurring task was provided to a recurring task call");
        }
    }
}
