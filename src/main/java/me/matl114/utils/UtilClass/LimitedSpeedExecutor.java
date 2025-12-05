package me.matl114.utils.UtilClass;

import me.matl114.managers.Config;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class LimitedSpeedExecutor implements Executor {
    private Deque<Runnable> queue;
    private AtomicInteger size;
    private Config.IntRef count;
    public LimitedSpeedExecutor(Config.IntRef count) {
        this.count = count;

        this.size=new AtomicInteger(0);
        this.queue = new ArrayDeque<>();
    }
    //execute when next "execute" or "reset" method is called
    public void addDelayedExecuteTask(Runnable runnable){
        queue.add(runnable);
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
