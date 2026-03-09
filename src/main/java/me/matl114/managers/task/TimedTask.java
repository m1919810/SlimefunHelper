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
}
