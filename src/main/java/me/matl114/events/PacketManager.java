package me.matl114.events;

import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
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
import me.matl114.events.channels.EventChannelDispatcher;
import me.matl114.events.channels.ListenerPoint;
import me.matl114.events.packets.PacketStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.CommonPackets;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.PlayPackets;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.world.World;

public class PacketManager {
    // this queue should be accessed only in event loop
    public static final ConcurrentLinkedQueue<PacketStorage> packetQueueIn = Queues.newConcurrentLinkedQueue();
    // this queue can be accessed async
    public static final ConcurrentLinkedQueue<PacketStorage> packetQueueOut = Queues.newConcurrentLinkedQueue();

    public static final WeakHashMap<Packet<?>, List<Consumer<Event<Packet<?>>>>> postSendQueue = new WeakHashMap<>();

    public static final WeakHashMap<Packet<?>, List<Consumer<Event<Packet<?>>>>> postScheduleSendQueue =
            new WeakHashMap<>();

    public static void schedulePostSendPacket(Packet<?> post, Packet<?> packet) {
        if (packet == null) return;
        schedulePostCallback(post, (ev) -> {
            ClientConnection conn = ev.getArgs(0);
            conn.send(packet);
        });
    }

    public static <T extends Packet<?>> void schedulePostCallback(T post, Runnable packet) {
        if (packet == null) return;
        schedulePostCallback(post, (ev) -> {
            packet.run();
        });
    }

    public static <T extends Packet<?>> void schedulePostCallback(T post, Consumer<Event<T>> packet) {
        if (packet == null) return;
        postSendQueue.computeIfAbsent(post, (kv) -> new ArrayList<>()).add((Consumer) packet);
    }

    public static <T extends Packet<?>> void schedulePostScheduleCallback(T post, Runnable packet) {
        if (packet == null) return;
        schedulePostScheduleCallback(post, (ev) -> {
            packet.run();
        });
    }

    public static <T extends Packet<?>> void schedulePostScheduleCallback(T post, Consumer<Event<T>> packet) {
        if (packet == null) return;
        postScheduleSendQueue.computeIfAbsent(post, (kv) -> new ArrayList<>()).add((Consumer) packet);
    }

    public static void onPostPacketSend(Event<Packet<?>> packet) {
        var lst = postSendQueue.remove(packet.context);
        if (lst != null && !lst.isEmpty()) {
            for (var pkt : lst) {
                pkt.accept(packet);
            }
        }
    }

    public static void onPostPacketScheduleSend(Event<Packet<?>> packet) {
        var lst = postScheduleSendQueue.remove(packet.context);
        if (lst != null && !lst.isEmpty()) {
            for (var pkt : lst) {
                pkt.accept(packet);
            }
        }
    }

    static {
        Listener.getPacketPostSendPoint().registerHandler(PacketManager::onPostPacketSend);
        Listener.getPacketPostScheduleSendPoint().registerHandler(PacketManager::onPostPacketScheduleSend);
    }
    // must visit in eventLoop
    public static boolean startFlushIn = false;
    public static boolean startFlushOut = false;
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
                Event<PacketStorage> queueEvent = new Event<>(
                        new PacketStorageImpl(packet, System.currentTimeMillis(), connection), true, false, connection);
                packetQueueEvent.handleValue(queueEvent);
                if (queueEvent.isCancelled()) {
                    handleQueueIn(queueEvent.context());
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
                Event<PacketStorage> queueEvent = new Event<>(
                        new PacketStorageImpl(packet, System.currentTimeMillis(), connection), true, false, connection);
                packetQueueEvent.handleValue(queueEvent);
                if (queueEvent.isCancelled()) {
                    handleQueueOut(queueEvent.context());
                    return true;
                }
            }
        }
        return false;
    }

    public static void flushInBound() {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().channel.eventLoop().execute(() -> {
                try {
                    if (mc.getNetworkHandler() != null
                            && mc.getNetworkHandler().getConnection().isOpen()) {
                        // flush
                        startFlushIn = true;
                        try {
                            for (var packet : packetQueueIn) {
                                packet.handle();
                            }
                        } finally {
                            startFlushIn = false;
                            // clear async
                            packetQueueIn.clear();
                        }
                    } else {
                        packetQueueIn.clear();
                    }
                } catch (Throwable e) {
                    packetQueueIn.clear();
                }
            });
        } else {
            packetQueueIn.clear();
        }
    }

    public static void flushInBound(Function<PacketStorage, FlushAction> pdd) {

        // flush
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().channel.eventLoop().execute(() -> {
                if (mc.getNetworkHandler() != null
                        && mc.getNetworkHandler().getConnection().isOpen()) {
                    startFlushIn = true;
                    var iter = packetQueueIn.iterator();
                    try {
                        while (iter.hasNext()) {
                            var packet = iter.next();
                            switch (pdd.apply(packet)) {
                                case FLUSH -> {
                                    packet.handle();
                                    iter.remove();
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
            });
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
                        packet.send();
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
                            packet.send();
                            iter.remove();
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
                || pkt instanceof ChatCommandSignedC2SPacket
                || pkt instanceof ChatMessageC2SPacket
                || pkt instanceof CommandExecutionC2SPacket
                || pkt instanceof RequestCommandCompletionsC2SPacket) return true;
        return false;
    }

    private static final ReferenceSet<PacketType<?>> packetSet1 = new ReferenceArraySet<>();

    static {
        packetSet1.add(CommonPackets.KEEP_ALIVE_C2S);
        packetSet1.add(PlayPackets.CHAT_COMMAND_SIGNED);
        packetSet1.add(PlayPackets.CHAT_COMMAND);
        packetSet1.add(PlayPackets.CHAT);
        packetSet1.add(PlayPackets.COMMAND_SUGGESTION);
    }

    public static boolean isAsyncOrNotTransactionC2SPacket(PacketType<?> pkt) {
        return pkt != null && packetSet1.contains(pkt);
    }

    private static final ReferenceSet<PacketType<?>> packetSet2 = new ReferenceArraySet<>();

    static {
        packetSet2.add(CommonPackets.KEEP_ALIVE_S2C);
        packetSet2.add(PlayPackets.PLAYER_CHAT);
        packetSet2.add(PlayPackets.SYSTEM_CHAT);
        packetSet2.add(PlayPackets.CONTAINER_CLOSE_S2C);
        packetSet2.add(PlayPackets.LEVEL_CHUNK_WITH_LIGHT);
        packetSet2.add(PlayPackets.CHUNKS_BIOMES);
    }

    public static boolean isInventoryPacket(Packet<?> pkt) {
        return pkt instanceof ClickSlotC2SPacket || pkt instanceof CloseHandledScreenC2SPacket;
    }

    public static boolean isInventoryPacket(PacketType<?> pkt) {
        return pkt == PlayPackets.CONTAINER_CLICK || pkt == PlayPackets.CONTAINER_CLOSE_C2S;
    }

    public static boolean isAsyncOrNotTransactionS2CPacket(Packet<?> pkt) {
        if (pkt instanceof KeepAliveS2CPacket
                || pkt instanceof ChatMessageS2CPacket
                || pkt instanceof GameMessageS2CPacket
                || pkt instanceof CloseScreenS2CPacket
                || pkt instanceof ChunkDataS2CPacket) return true;
        return false;
    }

    public static boolean isAsyncOrNotTransactionS2CPacket(PacketType<?> pkt) {
        return pkt != null && packetSet2.contains(pkt);
    }

    public static void handleQueueIn(PacketStorage packet) {
        packetQueueIn.add(packet);
    }

    public static void handleQueueOut(PacketStorage packet) {
        packetQueueOut.add(packet);
    }

    @Getter
    @Cancelable
    @ExtraArgs({ClientConnection.class})
    public static EventChannelDispatcher<PacketStorage> packetQueueEvent =
            new EventChannelDispatcher<>(PacketStorage::side);

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

    public static record PacketStorageImpl(Packet<?> packet, long timestampMS, ClientConnection connection)
            implements PacketStorage {
        @Override
        public PacketType<?> packetType() {
            return packet.getPacketId();
        }

        @Override
        public NetworkSide side() {
            return packet.getPacketId().side();
        }

        @Override
        public void send() {
            try {
                connection.send(packet);
            } catch (Throwable throwable) {
            }
        }

        @Override
        public void handle() {
            try {
                ClientConnectionAccess.of(connection).handlePacket(packet);
            } catch (Throwable throwable) {
            }
        }
    }
}
