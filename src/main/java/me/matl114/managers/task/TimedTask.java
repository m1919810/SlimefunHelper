package me.matl114.managers.task;

import me.matl114.managers.Tasks;

public abstract class TimedTask implements Task {
    abstract boolean runTask();

    public TimedTask(int delay) {
        this.expireTicks = Tasks.getTick() + delay;
    }

    int expireTicks;

    public boolean execute() {
        if (Tasks.getTick() >= expireTicks) {
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
