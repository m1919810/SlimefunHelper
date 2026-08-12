package me.matl114.hacks.utils.tasks;

import me.matl114.managers.Tasks;

public class TimerExecutor {
    int lastActionTime = 0;

    public TimerExecutor() {}

    public boolean canRun(int cd) {
        return Tasks.getTick() >= cd + lastActionTime;
    }

    public boolean run(int cd, Runnable r) {
        if (Tasks.getTick() >= cd + lastActionTime) {
            lastActionTime = Tasks.getTick();
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public boolean run(int cd) {
        if (Tasks.getTick() >= cd + lastActionTime) {
            lastActionTime = Tasks.getTick();
            return true;
        } else {
            return false;
        }
    }

    public boolean runIf(int cd, Runnable r) {
        if (Tasks.getTick() >= cd + lastActionTime) {
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public boolean runIfIn(int cd, Runnable r) {
        if (Tasks.getTick() < cd + lastActionTime) {
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public void mark() {
        lastActionTime = Tasks.getTick();
    }

    public void mark(int extra) {
        lastActionTime = Tasks.getTick() + extra;
    }
}
