package me.matl114.utils.algorithms;

import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SerialExecutor implements Runnable, Executor {
    final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    Consumer<Runnable> asyncRunner;
    public SerialExecutor(Consumer<Runnable> asyncRunner) {
        this.asyncRunner = asyncRunner;
    }
    final AtomicBoolean running = new AtomicBoolean(false);

    public void submit(Runnable task) {
        tasks.add(task);
        asyncRunner.accept(this);
    }

    @Override
    public void run() {
        if(running.compareAndSet(false, true)) {
            try{
                while(!tasks.isEmpty()) {
                    Runnable task = tasks.poll();
                    task.run();
                }
            }finally {
                running.set(false);
            }
        }
    }

    @Override
    public void execute(@NotNull Runnable runnable) {
        submit(runnable);
    }
}
