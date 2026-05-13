package me.matl114.managers;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.managers.task.RepeatTask;
import me.matl114.managers.task.Task;
import me.matl114.managers.task.TimedTask;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.crash.CrashException;

public class Tasks {
    public static void init() {}

    private Tasks() {}

    private static volatile int tickCounter;

    private static volatile int secondCounter;

    @ApiMethod
    public static int getTick() {
        return tickCounter;
    }

    public static boolean isPeriod(int period) {
        return tickCounter % period == 0;
    }

    @ApiMethod
    public static int getSecond() {
        return secondCounter;
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Set<Consumer<ClientPlayerEntity>> gameTasks = new LinkedHashSet<>();

    // run when player is not null
    public static void registerGameTask(Consumer<ClientPlayerEntity> r) {
        gameTasks.add(r);
    }

    public static void doPostTick() {
        var iter = postTaskQueue.iterator();
        while (iter.hasNext()) {
            try {
                var task = iter.next();
                if (task.execute()) {
                    iter.remove();
                }
            } catch (CrashException | StackOverflowError e) {
                // remove exceptional task
                iter.remove();
                throw e;
            } catch (Throwable e) {
                // log exception and remove
                Debug.info("unexpected error while executing TimedTask:");
                Debug.info(e);
                iter.remove();
            }
        }
    }

    public static void doPreTick() {
        var iter = preTaskQueue.iterator();
        while (iter.hasNext()) {
            try {
                var task = iter.next();
                if (task.execute()) {
                    iter.remove();
                }
            } catch (CrashException | StackOverflowError e) {
                // remove exceptional task
                iter.remove();
                throw e;
            } catch (Throwable e) {
                // log exception and remove
                Debug.info("unexpected error while executing TimedTask:");
                Debug.info(e);
                iter.remove();
            }
        }
    }

    public static void doGameTick(ClientPlayerEntity player) {
        gameTasks.forEach(i -> i.accept(player));
    }

    public static void onPreTick(Event<Void> v) {
        // update tick Counter at pre tick
        ++tickCounter;
        if (tickCounter < 0) {
            tickCounter = 0;
        } else if (tickCounter % 20 == 0) {
            ++secondCounter;
        }
        doPreTick();
    }

    public static void onPostTick(Event<Void> v) {
        if (mc.player != null) {
            Tasks.doGameTick(mc.player);
        }
        doPostTick();
    }

    private static final Deque<Task> postTaskQueue = new ConcurrentLinkedDeque<>();

    private static final Deque<Task> preTaskQueue = new ConcurrentLinkedDeque<>();

    @ApiMethod
    public static void scheduleTask(Task task) {
        postTaskQueue.addLast(task);
    }

    @ApiMethod
    public static void scheduleDelayed(Runnable task, int delay) {
        postTaskQueue.addLast(new TimedTask.Impl(task, delay));
    }

    @ApiMethod
    public static void scheduleRepeated(BooleanSupplier task, int delay, int period) {
        postTaskQueue.addLast(new RepeatTask.Impl(task, delay, period));
    }

    @ApiMethod
    public static void scheduleTaskPre(Task task) {
        preTaskQueue.addLast(task);
    }

    @ApiMethod
    public static void scheduleDelayedPre(Runnable task, int delay) {
        preTaskQueue.addLast(new TimedTask.Impl(task, delay));
    }

    @ApiMethod
    public static void scheduleRepeatedPre(BooleanSupplier task, int delay, int period) {
        preTaskQueue.addLast(new RepeatTask.Impl(task, delay, period));
    }

    static {
        Listener.getPostTick().registerHandler(Tasks::onPostTick);
        Listener.getPreTick().registerHandler(Tasks::onPreTick);
    }
}
