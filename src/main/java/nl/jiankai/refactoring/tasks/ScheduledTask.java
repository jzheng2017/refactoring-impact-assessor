package nl.jiankai.refactoring.tasks;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ScheduledTask<T> extends Task<T> {
    private int period;
    private TimeUnit periodTimeUnit;
    private int delay;
    private boolean recurring;

    public ScheduledTask(Callable<T> task, int period, TimeUnit periodTimeUnit, int delay, boolean recurring) {
        super(task);

        if (period != 0 && !recurring) {
            throw new IllegalArgumentException("Can not provide period with a task that is not recurring");
        }

        if (period < 0) {
            throw new IllegalArgumentException("Period can not be a negative number");
        }

        this.periodTimeUnit = periodTimeUnit;
        this.period = period;
        this.delay = delay;
        this.recurring = recurring;
    }

    public static <R> Builder<R> builder(Class<R> clazz) {
        return new Builder<>();
    }


    public static class Builder<T> {
        private Callable<T> task;
        private int period;
        private TimeUnit periodTimeUnit;
        private int delay;
        private boolean recurring;

        public Builder<T> task(Callable<T> task) {
            this.task = task;
            return this;
        }

        public Builder<T> period(int period) {
            this.period = period;
            return this;
        }

        public Builder<T> periodTimeUnit(TimeUnit periodTimeUnit) {
            this.periodTimeUnit = periodTimeUnit;
            return this;
        }

        public Builder<T> delay(int delay) {
            this.delay = delay;
            return this;
        }

        public Builder<T> recurring() {
            this.recurring = true;
            return this;
        }

        public ScheduledTask<T> build() {
            return new ScheduledTask<>(task, period, periodTimeUnit, delay, recurring);
        }
    }


    public int delay() {
        return delay;
    }

    public int period() {
        return period;
    }
    public boolean isRecurring() {
        return recurring;
    }

    public TimeUnit periodTimeUnit() {
        return periodTimeUnit;
    }
}
