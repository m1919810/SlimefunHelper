package me.matl114.utils.tasks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import me.matl114.managers.config.DoubleRef;
import org.jetbrains.annotations.NotNull;

public class LimitedSpeedExecutor implements Executor {
    private Deque<BooleanSupplier> queue;
    private double counter;
    private final DoubleRef count;

    public LimitedSpeedExecutor(DoubleRef count) {
        this.count = count;

        this.counter = count.get();
        this.queue = new ArrayDeque<>();
    }
    // execute when next "execute" or "reset" method is called
    public void addDelayedExecuteTask(Runnable runnable) {
        queue.add(() -> {
            runnable.run();
            return true;
        });
    }

    public void addDelayedExecuteTask(BooleanSupplier runnable) {
        queue.add(runnable);
    }

    public void execute(@NotNull BooleanSupplier runnable) {

        while (!queue.isEmpty() && counter >= 1) {
            BooleanSupplier r = queue.poll();
            executeInternal(r);
        }
        if (counter < 1) {
            queue.add(runnable);
            return;
        } else {
            executeInternal(runnable);
        }
    }

    @Override
    public void execute(@NotNull Runnable runnable) {
        execute(() -> {
            runnable.run();
            return true;
        });
    }

    private void executeInternal(BooleanSupplier runnable) {
        if (runnable.getAsBoolean()) {
            counter -= 1.0D;
        }
    }

    public void reset() {
        counter = Math.clamp(counter, 0.0D, 0.99D);
        counter += count.get();
        while (!queue.isEmpty() && counter >= 1) {
            BooleanSupplier r = queue.poll();
            executeInternal(r);
        }
    }
}
