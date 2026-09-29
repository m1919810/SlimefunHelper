package me.matl114.hacks.utils.tasks;

public class StateExecutor {
    boolean state = false;

    public StateExecutor() {}

    public boolean state(boolean state, Runnable runnable) {
        if (state != this.state) {
            this.state = state;
            runnable.run();
            return true;
        } else {
            return false;
        }
    }

    public boolean state() {
        return state;
    }

    public void stateOrElse(boolean state, Runnable runnable, Runnable orElse) {
        if (!state(state, runnable)) {
            orElse.run();
        }
    }

    public void state(boolean state) {
        this.state = state;
    }
}
