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
    private static final Set<Runnable> tasks = new LinkedHashSet<>();
    private static final Set<Consumer<ClientPlayerEntity>> gameTasks = new LinkedHashSet<>();

    public static void registerTickTask(Runnable r) {
        tasks.add(r);
    }
    // run when player is not null
    public static void registerGameTask(Consumer<ClientPlayerEntity> r) {
        gameTasks.add(r);
    }

    public static void doTick() {
        tasks.forEach(Runnable::run);
    }

    public static void doGameTick(ClientPlayerEntity player) {
        gameTasks.forEach(i -> i.accept(player));
    }

    public static void onPreTick(Event<Void> v) {
        if (mc.player != null) {
            Listener.getPreGameTick().broadcast(mc.player);
        }
    }

    public static void onPostTick(Event<Void> v) {
        if (mc.player != null) {
            Tasks.doGameTick(mc.player);
            Listener.getPostGameTick().broadcast(mc.player);
        }
        Tasks.doTick();
    }

    private static final Deque<Task> taskQueue = new ConcurrentLinkedDeque<>();

    @ApiMethod
    public static void scheduleTask(Task task) {
        taskQueue.addLast(task);
    }

    @ApiMethod
    public static void scheduleDelayed(Runnable task, int delay) {
        taskQueue.addLast(new TimedTask.Impl(task, delay));
    }

    @ApiMethod
    public static void scheduleRepeated(BooleanSupplier task, int delay, int period) {
        taskQueue.addLast(new RepeatTask.Impl(task, delay, period));
    }

    static {
        registerTickTask(() -> {
            ++tickCounter;
            if (tickCounter < 0) {
                tickCounter = 0;
            } else if (tickCounter % 20 == 0) {
                ++secondCounter;
            }
        });

        registerTickTask(() -> {
            var iter = taskQueue.iterator();
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
        });

        Listener.getPostTick().registerHandler(Tasks::onPostTick);
        Listener.getPreTick().registerHandler(Tasks::onPreTick);
    }
}
