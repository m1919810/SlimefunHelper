package me.matl114.events;

import com.google.common.collect.Queues;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import me.matl114.accessors.events.ClientConnectionAccess;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.annotations.Cancelable;
import me.matl114.events.annotations.ExtraArgs;
import me.matl114.events.channels.EventChannel;
import me.matl114.events.channels.ListenerPoint;
import me.matl114.events.channels.PacketEventChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.world.World;

public class PacketManager {

    public static final ConcurrentLinkedQueue<PacketStorage> packetQueueIn = Queues.newConcurrentLinkedQueue();
    public static final ConcurrentLinkedQueue<PacketStorage> packetQueueOut = Queues.newConcurrentLinkedQueue();

    public static final WeakHashMap<Packet<?>, List<Consumer<Event<Packet<?>>>>> postSendQueue = new WeakHashMap<>();

    public static void schedulePostSendPacket(Packet<?> post, Packet<?> packet) {
        schedulePostCallback(post, (ev) -> {
            ClientConnection conn = ev.getArgs(0);
            conn.send(packet);
        });
    }

    public static <T extends Packet<?>> void schedulePostCallback(T post, Runnable packet) {
        schedulePostCallback(post, (ev) -> {
            packet.run();
        });
    }

    public static <T extends Packet<?>> void schedulePostCallback(T post, Consumer<Event<T>> packet) {
        postSendQueue.computeIfAbsent(post, (kv) -> new ArrayList<>()).add((Consumer) packet);
    }

    public static void onPostPacketSend(Event<Packet<?>> packet) {
        var lst = postSendQueue.remove(packet.context);
        if (lst != null && !lst.isEmpty()) {
            for (var pkt : lst) {
                pkt.accept(packet);
            }
        }
    }

    static {
        Listener.getPacketPostSendPoint().registerHandler(PacketManager::onPostPacketSend);
    }

    private static boolean startFlushIn = false;
    private static boolean startFlushOut = false;
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean handleQueueInPacket(Packet<?> packet, ClientConnection connection) {
        // do not handle flushing packets
        if (startFlushIn) {
            return false;
        }
        // todo: what about BundlePacket
        if (connection.getPacketListener() instanceof ClientPlayPacketListener play) {
            if (packet instanceof DisconnectS2CPacket
                    || (packet instanceof HealthUpdateS2CPacket hl && hl.getHealth() <= 0.0)
                    || packet instanceof PlayerRespawnS2CPacket
                    || packet instanceof EnterReconfigurationS2CPacket) {
                // clear all
                clearAndShutdown();
            } else {
                Event<Packet<?>> queueEvent = new Event<>(packet, true, false, connection);
                packetQueueEvent.handleValue(queueEvent);
                if (queueEvent.isCancelled()) {
                    handleQueueIn(packet);
                    return true;
                }
            }
        }
        return false;
    }

    public static void clearAndShutdown() {
        queueShutdownEvent.broadcast(null);
        flushInBound();
        flushOutBound();
    }

    public static boolean handleQueueOutPacket(Packet<?> packet, ClientConnection connection) {
        if (startFlushOut) {
            return false;
        }
        if (connection.getPacketListener() instanceof ClientPlayPacketListener play) {
            if (packet instanceof AcknowledgeReconfigurationC2SPacket) {
                clearAndShutdown();
            } else {
                Event<Packet<?>> queueEvent = new Event<>(packet, true, false, connection);
                packetQueueEvent.handleValue(queueEvent);
                if (queueEvent.isCancelled()) {
                    handleQueueOut(packet);
                    return true;
                }
            }
        }
        return false;
    }

    public static void flushInBound() {
        try {
            if (mc.getNetworkHandler() != null
                    && mc.getNetworkHandler().getConnection().isOpen()) {
                // flush
                startFlushIn = true;
                ClientConnection connection = mc.getNetworkHandler().getConnection();
                try {
                    for (var packet : packetQueueIn) {
                        try {
                            ClientConnectionAccess.of(connection).handlePacket(packet.packet());
                        } catch (Throwable throwable) {
                        }
                    }
                } finally {
                    startFlushIn = false;
                }
            }
        } finally {
            packetQueueIn.clear();
        }
    }

    public static void flushInBound(Function<PacketStorage, FlushAction> pdd) {
        if (mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getConnection().isOpen()) {
            // flush
            startFlushIn = true;
            ClientConnection connection = mc.getNetworkHandler().getConnection();
            var iter = packetQueueIn.iterator();
            try {
                while (iter.hasNext()) {
                    var packet = iter.next();
                    switch (pdd.apply(packet)) {
                        case FLUSH -> {
                            try {
                                ClientConnectionAccess.of(connection).handlePacket(packet.packet());
                            } catch (Throwable throwable) {
                            } finally {
                                iter.remove();
                            }
                        }
                        case DROP -> {
                            iter.remove();
                        }
                    }
                }
            } finally {
                startFlushIn = false;
            }
        } else {
            packetQueueIn.removeIf((v) -> pdd.apply(v) != FlushAction.QUEUE);
        }
    }

    public static void flushOutBound() {
        try {
            if (mc.getNetworkHandler() != null
                    && mc.getNetworkHandler().getConnection().isOpen()) {
                // flush
                startFlushOut = true;
                try {
                    for (var packet : packetQueueOut) {
                        try {
                            // Listener.sendPacketNoEvents(packet);
                            mc.getNetworkHandler().sendPacket(packet.packet());
                        } catch (Throwable throwable) {
                        }
                    }
                } finally {
                    startFlushOut = false;
                }
            }
        } finally {
            packetQueueOut.clear();
        }
    }

    public static void flushOutBound(Function<PacketStorage, FlushAction> pdd) {
        if (mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getConnection().isOpen()) {
            // flush
            startFlushOut = true;
            var iter = packetQueueOut.iterator();
            try {
                while (iter.hasNext()) {
                    var packet = iter.next();
                    switch (pdd.apply(packet)) {
                        case FLUSH -> {
                            try {
                                mc.getNetworkHandler().sendPacket(packet.packet());
                            } catch (Throwable throwable) {
                            } finally {
                                iter.remove();
                            }
                        }
                        case DROP -> {
                            iter.remove();
                        }
                    }
                }
            } finally {
                startFlushOut = false;
            }
        } else {
            packetQueueOut.removeIf((v) -> pdd.apply(v) != FlushAction.QUEUE);
        }
    }

    public static boolean isAsyncOrNotTransactionC2SPacket(Packet<?> pkt) {
        if (pkt instanceof KeepAliveC2SPacket
                || pkt instanceof ClickSlotC2SPacket
                || pkt instanceof CloseHandledScreenC2SPacket
                || pkt instanceof ChatCommandSignedC2SPacket
                || pkt instanceof ChatMessageC2SPacket
                || pkt instanceof CommandExecutionC2SPacket
                || pkt instanceof RequestCommandCompletionsC2SPacket) return true;
        return false;
    }

    public static boolean isAsyncOrNotTransactionS2CPacket(Packet<?> pkt) {
        if (pkt instanceof KeepAliveS2CPacket
                || pkt instanceof ChatMessageS2CPacket
                || pkt instanceof GameMessageS2CPacket
                || pkt instanceof CloseScreenS2CPacket
                || pkt instanceof ChunkDataS2CPacket) return true;
        return false;
    }

    public static void handleQueueIn(Packet<?> packet) {
        packetQueueIn.add(new PacketStorage(packet, System.currentTimeMillis()));
    }

    public static void handleQueueOut(Packet<?> packet) {
        packetQueueOut.add(new PacketStorage(packet, System.currentTimeMillis()));
    }

    @Getter
    @Cancelable
    @ExtraArgs({ClientConnection.class})
    public static PacketEventChannel packetQueueEvent = new PacketEventChannel();

    @Getter
    @Broadcast
    public static EventChannel<Void> queueShutdownEvent = new EventChannel<>();

    public static void onDisconnect(Event<Void> disconnect) {
        clearAndShutdown();
    }

    public static void onWorldSwitch(Event<World> event) {
        clearAndShutdown();
    }

    protected static <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler) {
        listener.registerHandler(handler);
    }

    static {
        registerListener(Listener.getServerLeavePoint(), PacketManager::onDisconnect);
        registerListener(Listener.getWorldSwitchPoint(), PacketManager::onWorldSwitch);
    }

    public static enum FlushAction {
        DROP,
        FLUSH,
        QUEUE;
    }

    public static record PacketStorage(Packet<?> packet, long timestampMS) {}
}
