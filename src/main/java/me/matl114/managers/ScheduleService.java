package me.matl114.managers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import me.matl114.utils.Debug;

public class ScheduleService {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private static final AtomicInteger taskIdGenerator = new AtomicInteger(0);

    public static ScheduledExecutorService getSingleThreadScheduler() {
        return scheduler;
    }

    public static String launchAsyncRepeatTask(Runnable task, long initialDelay, long repeat) {
        String taskId = "repeat-" + taskIdGenerator.incrementAndGet();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        Debug.getLogger().warn("重复任务执行异常: {}", taskId);
                        e.printStackTrace();
                        // 发生异常时取消任务，防止无限重试
                    }
                },
                initialDelay,
                repeat,
                TimeUnit.MILLISECONDS);

        runningTasks.put(taskId, future);
        return taskId;
    }

    public static String launchAsyncDelayedTask(Runnable task, long delay) {
        String taskId = "delayed-" + taskIdGenerator.incrementAndGet();

        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        Debug.getLogger().warn("任务执行异常: {}", taskId);
                        e.printStackTrace();
                    } finally {
                        runningTasks.remove(taskId);
                    }
                },
                delay,
                TimeUnit.MILLISECONDS);

        runningTasks.put(taskId, future);
        return taskId;
    }

    public static boolean stopAsyncTask(String taskId) {
        var task = runningTasks.get(taskId);
        if (task != null) {
            boolean success = task.cancel(true);
            if (success) {
                runningTasks.remove(taskId);
            }
            return success;
        }
        return false;
    }
}
