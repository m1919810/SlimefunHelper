package me.matl114.hacks.utils.tasks;

public class CounterExecutor {
    int counter = 0;

    public boolean countdown(int limit, Runnable runnable) {
        if (++counter >= limit) {
            runnable.run();
            counter = 0;
            return true;
        } else {
            return false;
        }
    }

    public boolean countdown(int limit) {
        if (++counter >= limit) {
            counter = 0;
            return true;
        } else {
            return false;
        }
    }

    public boolean execute(int limit, Runnable runnable) {
        if (counter >= limit) {
            runnable.run();
            counter = 0;
            return true;
        } else {
            return false;
        }
    }

    public boolean executeIf(int limit) {
        return counter >= limit;
    }

    public boolean executeIf(int limit, Runnable runnable) {
        if (counter >= limit) {
            runnable.run();
            return true;
        } else {
            return false;
        }
    }

    public void count() {
        ++this.counter;
    }

    public void count(int count) {
        this.counter += count;
    }

    public void reset() {
        this.counter = 0;
    }
}
