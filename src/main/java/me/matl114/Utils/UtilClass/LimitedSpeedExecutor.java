package me.matl114.Utils.UtilClass;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class LimitedSpeedExecutor implements Executor {
    private Deque<Runnable> queue;
    private AtomicInteger size;
    private AtomicInteger count;
    public LimitedSpeedExecutor(AtomicInteger count) {
        this.count = count;

        this.size=new AtomicInteger(0);
        this.queue = new ArrayDeque<>();
    }

    @Override
    public void execute(@NotNull Runnable runnable) {
        if(size.get()>count.get()) {
            queue.add(runnable);
            return;
        }
        while(!queue.isEmpty()&&size.getAndIncrement()<=count.get()) {
            Runnable r = queue.poll();
            executeInternal(r);
        }
        size.incrementAndGet();
        executeInternal(runnable);
    }
    private void executeInternal(Runnable runnable) {
        runnable.run();
    }
    public void reset(){
        size.set(0);
        while(!queue.isEmpty()&&size.getAndIncrement()<count.get()) {
            Runnable r = queue.poll();
            executeInternal(r);
        }
    }
}
