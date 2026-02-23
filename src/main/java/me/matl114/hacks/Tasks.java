package me.matl114.hacks;

import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.config.ConfigurateNewStyleScreen;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.task.ConfigSystem;
import me.matl114.managers.config.Config;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Saddleable;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.*;

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
    // private static final AtomicInteger tickRandomSource = new AtomicInteger(0);
    // todo find how to dupe with ITEM
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

    public static void onPostTick(Event<Void> v) {
        if (mc.player != null) {
            Tasks.doGameTick(mc.player);
            Listener.getGameTick().broadcast(mc.player);
        }
        Tasks.doTick();
    }

    public static List<String> getSpecialTaskName() {
        return List.of("xray_demo", "writable_book_generate", "strider_fix", "client_crash");
    }

    @ApiMethod
    public static void runSpecialTask(String taskId, String[] args) {
        try {
            switch (taskId) {
                case "xray_demo" -> {
                    int a = Integer.parseInt(args[0]);
                    int b = Integer.parseInt(args[1]);
                    int c = Integer.parseInt(args[2]);
                    MineTasks.getAntiAXray().onAntiXrayDemoTest(new BlockPos(a, b, c));
                }
                case "writable_book_generate" -> {
                    generateWritableBookContent(args);
                }
                case "strider_fix" -> {
                    versionedStriderFix(args);
                }
                case "client_crash" -> {
                    clientCrash(args);
                }
                case "client_lite_crash"->{
                    clientLiteCrash(args);
                }
            }
        } catch (Throwable e) {
            Debug.info(e);
        }
    }

    public static void generateWritableBookContent(String[] args) {
        if (mc.player != null) {
            if (mc.player.getMainHandStack().getItem() == Items.WRITABLE_BOOK) {
                Debug.chat("生成了书内容");
                String generatedContent = "§b§k" + ("1a锕β".repeat(250));
                mc.getNetworkHandler()
                        .sendPacket(new BookUpdateC2SPacket(
                                mc.player.getInventory().selectedSlot,
                                Collections.nCopies(100, generatedContent),
                                args.length > 0 ? Optional.of(String.join("\n", args)) : Optional.empty()));
            } else {
                Debug.chat("手持物品不是书");
            }
        }
    }

    public static void versionedStriderFix(String[] args) {
        if (mc.player != null) {
            if (mc.player.getVehicle() instanceof Saddleable striderEntity) {
                Debug.chat("Set saddle for entity");
                striderEntity.saddle(new ItemStack(Items.SADDLE), null);
            } else {
                Debug.chat("No vehicle");
            }
        }
    }

    // store the crash exception

    public static void clientCrash(String[] args) {
        mc.world = null;
        CompletableFuture.runAsync(() -> {
            mc.execute(() -> {
                throw new CrashException(new CrashReport("test crash", new NullPointerException()));
            });
        });
    }

    public static void clientLiteCrash(String[] args) {
        Tasks.scheduleDelayed(() -> {
            throw new CrashException(new CrashReport("test crash", new NullPointerException()));
        }, 1);
    }
    // todo: delay tp

    public static void fillFakeSubChunkWithStone() {}

    private static final Map<Class<?>, ArrayDeque<TimedPacketCatcher<?>>> maped = new ConcurrentHashMap<>();

    public static void addSequencePacketUpdateCallback(int sequence, IntSupplier callback) {
        addPacketCatcher(
                new TimedPacketCatcher<PlayerActionResponseS2CPacket>(PlayerActionResponseS2CPacket.class, 20) {
                    @Override
                    public int catchPacket(PlayerActionResponseS2CPacket packet) {
                        Debug.info(packet.sequence(), sequence);
                        if (packet.sequence() == sequence) {
                            return callback.getAsInt();
                        }
                        return (~REMOVAL & ~CANCEL);
                    }
                });
    }

    public static <T extends Packet<?>> void addPacketCatcher(TimedPacketCatcher<T> packet) {
        var re = maped.computeIfAbsent(packet.clazz, k -> new ArrayDeque<>());
        synchronized (re) {
            re.addLast(packet);
        }
    }
    // todo: turn to predicate use Task.getTick() as timer
    @AllArgsConstructor
    public abstract static class TimedPacketCatcher<T extends Packet<?>> {
        Class<T> clazz;
        int waitTick;

        public boolean count() {
            return --waitTick < 0;
        }

        public int catchPkt(Packet<?> packet) {
            // Debug.info("catch pkt?");
            if (clazz.isInstance(packet)) {
                return catchPacket((T) packet);
            } else {
                return (~REMOVAL & ~CANCEL);
            }
        }

        public static final int REMOVAL = 1;
        public static final int CANCEL = 2;

        public void timeoutCallback() {
            Debug.info("timeout waiting for packet ", clazz.getSimpleName());
        }
        // return if removal at mask 1, cancel at mask 2
        public abstract int catchPacket(T packet);
    }

    @AllArgsConstructor
    public abstract static class TimedTask {
        abstract boolean runTask();

        int delay;

        boolean execute() {
            if (--delay <= 0) {
                return runTask();
            }
            return false;
        }
    }

    public static class DelayedTimedTask extends TimedTask {
        Runnable task;

        public DelayedTimedTask(Runnable runnable, int delay) {
            super(delay);
            this.task = runnable;
        }

        @Override
        boolean runTask() {
            task.run();
            return true;
        }
    }

    public abstract static class RepeatTimedTask extends TimedTask {
        public RepeatTimedTask(int delay, int period) {
            super(delay);
            this.period = period;
        }

        protected boolean isCancelled = false;
        protected int period;

        public abstract boolean runTask0();

        @Override
        protected boolean runTask() {
            if (isCancelled) {
                return true;
            }
            if (runTask0()) {
                cancel();
                return true;
            } else {
                delay = period;
                return false;
            }
        }

        public void cancel() {
            isCancelled = true;
        }
    }

    public static class RepeatTimedTaskImpl extends RepeatTimedTask {

        BooleanSupplier task;

        public RepeatTimedTaskImpl(BooleanSupplier shouldStop, int delay, int period) {
            super(delay, period);
            this.task = shouldStop;
        }

        public boolean runTask0() {
            return task.getAsBoolean();
        }
    }

    private static final Deque<TimedTask> taskQueue = new ConcurrentLinkedDeque<>();

    @ApiMethod
    public static void scheduleDelayed(Runnable task, int delay) {
        taskQueue.addLast(new DelayedTimedTask(task, delay));
    }

    @ApiMethod
    public static void scheduleRepeated(BooleanSupplier task, int delay, int period) {
        taskQueue.addLast(new RepeatTimedTaskImpl(task, delay, period));
    }

    @ApiMethod
    public static void scheduleTask(TimedTask task) {
        taskQueue.addLast(task);
    }

    @ApiMethod
    public static void openConfigNewStyleScreen() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            ScreenAccess.of(new ConfigurateNewStyleScreen(
                            Config.getConfigs().stream().toList()))
                    .openFromCurrent();
        }
    }

    @ApiMethod
    public static void openConfigScreen(Config config) {
        ConfigurateNewStyleScreen newStyleScreen =
                new ConfigurateNewStyleScreen(Config.getConfigs().stream().toList());
        newStyleScreen.setGlobal(config);
        ScreenAccess.of(newStyleScreen).openFromCurrent();
    }

    @Getter
    private static final ModuleGroup moduleManager = new ModuleGroup("Tasks");

    @Getter
    public static ConfigSystem configSystem;

    private static void initModule(ModuleManager m) {
        configSystem = new ConfigSystem().register(m);
    }

    static {
        // todo:
        moduleManager.registerFactories(Tasks::initModule);
        HackModules.registerModuleGroup(moduleManager);
        MineTasks.init();
        ChatTasks.init();
        RenderTasks.init();
        InvTasks.init();
        CombatTasks.init();
        MovTasks.init();
        NetworksTasks.init();
        InteractionTasks.init();
        SlimefunTasks.init();
        ModelTasks.init();
        ACPostTasks.init();
        ExtraTasks.init();
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
                    throw e;
                } catch (Throwable e) {
                    Debug.info("unexpected error while executing TimedTask:");
                    Debug.info(e);
                    iter.remove();
                }
            }
        });
        // todo: remove this, use ListenerPoint instead
        Predicate<Packet<?>> catcher = (packet -> {
            var identifier = Listener.getMappedPacketClass(packet.getClass());
            var handlers = maped.get(identifier);
            if (handlers != null && !handlers.isEmpty()) {
                synchronized (handlers) {
                    var iter = handlers.iterator();
                    while (iter.hasNext()) {
                        var handler = iter.next();
                        int code = handler.catchPkt(packet);
                        if ((code & TimedPacketCatcher.REMOVAL) != 0) {
                            iter.remove();
                        }
                        if ((code & TimedPacketCatcher.CANCEL) != 0) {
                            return false;
                        }
                    }
                }
            }

            return true;
        });
        // Listener.registerPacketListener(catcher, true);
        Listener.getPacketPreHandlePoint().registerHandler((ev) -> {
            if (!catcher.test(ev.context())) {
                ev.cancel();
            }
        });
        Listener.registerPacketListener(catcher, false);
        registerTickTask(() -> {
            for (Map.Entry<Class<?>, ArrayDeque<TimedPacketCatcher<?>>> entry : maped.entrySet()) {
                // remove when timeout ,(TimeUnit:tick)
                var handlers = entry.getValue();
                synchronized (handlers) {
                    var iter2 = handlers.iterator();
                    while (iter2.hasNext()) {
                        var handler = iter2.next();
                        if (handler.count()) {
                            handler.timeoutCallback();
                            iter2.remove();
                        }
                    }
                }
            }
        });
        Listener.getPostTick().registerHandler(Tasks::onPostTick);
    }
}
