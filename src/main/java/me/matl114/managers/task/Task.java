package me.matl114.managers.task;

public interface Task {
    // only called in game tick
    public boolean execute();
}
