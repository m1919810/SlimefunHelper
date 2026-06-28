package me.matl114.managers.task;

import java.util.function.BooleanSupplier;

public abstract class RepeatTask implements Task {
    int delay;
    int period;
    boolean isCancelled;

    public RepeatTask(int period) {
        this(0, period);
    }

    public RepeatTask(int delay, int period) {
        this.delay = delay;
        this.period = period;
    }

    @Override
    public boolean execute() {
        if (--delay <= 0) {
            if (isCancelled) {
                return true;
            }
            if (runTask()) {
                cancel();
                return true;
            } else {
                delay = period;
                return false;
            }
        }
        return false;
    }

    public abstract boolean runTask();

    public void cancel() {
        isCancelled = true;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public static class Impl extends RepeatTask {
        BooleanSupplier task;

        public Impl(BooleanSupplier task, int delay, int period) {
            super(delay, period);
            this.task = task;
        }

        public Impl(Runnable runnable, int delay, int period) {
            super(delay, period);
            this.task = () -> {
                runnable.run();
                return false;
            };
        }

        @Override
        public boolean runTask() {
            return task.getAsBoolean();
        }
    }

    public static class CountDown extends RepeatTask {
        BooleanSupplier task;

        int repeat;

        public CountDown(BooleanSupplier runnable, int delay, int period, int repeat) {
            super(delay, period);
            this.task = runnable;
            this.repeat = repeat;
        }

        public CountDown(Runnable runnable, int delay, int period, int repeat) {
            super(delay, period);
            this.task = () -> {
                runnable.run();
                return false;
            };
        }

        @Override
        public boolean runTask() {
            if (repeat > 0) {
                repeat -= 1;
                return task.getAsBoolean();
            } else {
                return true;
            }
        }
    }
}
