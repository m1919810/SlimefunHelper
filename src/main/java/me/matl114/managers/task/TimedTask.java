package me.matl114.managers.task;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public abstract class TimedTask implements Task {
    abstract boolean runTask();

    int delay;

    public boolean execute() {
        if (--delay <= 0) {
            return runTask();
        }
        return false;
    }

    public static class Impl extends TimedTask {
        Runnable task;

        public Impl(Runnable runnable, int delay) {
            super(delay);
            this.task = runnable;
        }

        @Override
        public boolean runTask() {
            task.run();
            return true;
        }
    }
}
