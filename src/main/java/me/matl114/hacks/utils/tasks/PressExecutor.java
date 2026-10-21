package me.matl114.hacks.utils.tasks;

import me.matl114.managers.Tasks;

public class PressExecutor {
    int counter = 0;
    int lastPressedTick = 0;

    public void press() {
        if (lastPressedTick == Tasks.getTick() - 1) {
            counter += 1;
            lastPressedTick = Tasks.getTick();
        } else if (lastPressedTick == Tasks.getTick()) {
            return;
        } else {
            lastPressedTick = Tasks.getTick();
            counter = 1;
        }
    }

    public boolean canRun(int pressTime) {
        if (lastPressedTick >= Tasks.getTick() - 1) {
            return counter >= pressTime;
        } else {
            return false;
        }
    }

    public void reset() {
        counter = 0;
        lastPressedTick = 0;
    }
}
