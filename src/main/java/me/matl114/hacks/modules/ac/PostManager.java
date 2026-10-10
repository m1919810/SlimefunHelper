package me.matl114.hacks.modules.ac;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;

public class PostManager extends BaseModule {
    public static PostManager INSTANCE;

    public PostManager() {
        super("PostManager");
        INSTANCE = this;
    }

    private int peekPingRequest;
    private int lastPingTick;
    private final Deque<Consumer<ClientPlayNetworkHandler>> postTickHandlers = new ArrayDeque<>(33);
    private final Deque<Consumer<ClientPlayNetworkHandler>> queuePackets = new ArrayDeque<>(33);

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(CommonPingS2CPacket.class), this::peekPingPacketIn);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(CommonPingS2CPacket.class), this::postPongPacketOut);
        registerListener(Listener.getPostGameTick(), this::postTickEnd);
        registerListener(Listener.getServerLeavePoint(), this::onDisconnectReset);
        registerListener(Listener.getPostGameTick(), this::onWatchPingLongTimeNoSent);
        registerListener(Listener.getPreTick(), this::onPreTick);
    }

    public void addNextTransactionAction(Consumer<ClientPlayNetworkHandler> packet) {
        queuePackets.addLast(packet);
    }

    public void onPreTick(Event<Void> tick) {
        runAllQueuePackets(mc.getNetworkHandler());
    }

    public void peekPingPacketIn(Event<CommonPingS2CPacket> packetPing) {
        peekPingRequest += 1;
        lastPingTick = Tasks.getTick();
        //        Debug.info("in", packetPing.getPacketId(), peekPingRequest);
    }

    private final Deque<Integer> transactionIds = new ArrayDeque<>(50);

    private boolean isTransactionId(int id) {
        if (id > 0 || id < -32767) return false;

        if (!transactionIds.isEmpty()) {
            int last = transactionIds.peekLast();
            int gap = ((-id) - (-last) + 32768) % 32768;

            if (gap == 0 || gap > 16) return false;
        }

        transactionIds.addLast(id);
        if (transactionIds.size() > 50) transactionIds.removeFirst();
        return true;
    }

    public void postPongPacketOut(Event<CommonPingS2CPacket> event) {
        // we sent the Common Pong in Ping's handle

        // end transaction,
        // fresh queue
        peekPingRequest -= 1;
        //            Debug.info("out",pong.getPacketId(), peekPingRequest);

        // fix anything wrong wtf
        if (peekPingRequest < 0) peekPingRequest = 0;
        // anyway ,flush

        var id = event.context.getParameter();
        if (id == 0) {
            transactionIds.clear();
            transactionIds.addLast(id);
        } else if (id < 0 && id >= -64) {
            transactionIds.addLast(id);
        } else if (!isTransactionId(id)) { // we only use negative ids
            return;
        }
        runAllQueuePackets(mc.getNetworkHandler());
    }

    private void runAllPostTickPackets(ClientPlayNetworkHandler handler) {
        runQueue(handler, postTickHandlers);
    }

    private void runQueue(
            ClientPlayNetworkHandler handler, Deque<Consumer<ClientPlayNetworkHandler>> postTickHandlers) {
        if (!postTickHandlers.isEmpty()) {

            if (handler != null) {
                var iter = postTickHandlers.iterator();
                while (iter.hasNext()) {
                    iter.next().accept(handler);
                    iter.remove();
                }
            } else {
                postTickHandlers.clear();
            }
        }
    }

    private void runAllQueuePackets(ClientPlayNetworkHandler handler) {
        runQueue(handler, queuePackets);
    }

    private void onDisconnectReset(Event<Void> v) {
        peekPingRequest = 0;
        //        hasHandledPongPacket = false;
    }

    private void onWatchPingLongTimeNoSent(Event<ClientPlayerEntity> v) {
        if (peekPingRequest > 0 && lastPingTick + 20 < Tasks.getTick()) {
            peekPingRequest = 0;
            lastPingTick = Tasks.getTick();
            runAllQueuePackets(mc.getNetworkHandler());
        }
    }

    public void onDisconnect(Event<Void> v) {}
}
