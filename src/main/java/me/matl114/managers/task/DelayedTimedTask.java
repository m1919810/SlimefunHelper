package me.matl114.managers.task;

public class DelayedTimedTask extends TimedTask {
    Runnable task;

    public DelayedTimedTask(Runnable runnable, int delay) {
        super(delay);
        this.task = runnable;
    }

    @Override
    public boolean runTask() {
        task.run();
        return true;
    }
}
