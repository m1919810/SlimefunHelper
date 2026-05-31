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
    // failure:
    // rewrite pong packets to avoid post check
    // origin:
    // last tick
    // client tick end
    //  <- ping
    // pong ->
    // our post task
    // <- ping
    // pong ->
    // this tick
    //
    // we rewrite as
    // last tick
    // client tick end
    //  <- ping
    // pong ->
    // our post task
    // <- ping
    // pong -> (supressed)
    // this tick
    // this client tick end
    // supressed pong send
    //
    // shit, it doesn't work
    // private boolean hasHandledPongPacket = false;
    private Deque<CommonPongC2SPacket> delayedPingPackets = new ArrayDeque<>(33);

    public void addPostTickAction(Consumer<ClientPlayNetworkHandler> handler) {
        postTickHandlers.add(handler);
    }

    public void addNextPreTickAction(Consumer<ClientPlayNetworkHandler> packet) {
        if (lastPingTick < Tasks.getTick() - 10) {
            if (mc.getNetworkHandler() != null) {
                packet.accept(mc.getNetworkHandler());
            }
        } else {
            queuePackets.addLast(packet);
        }
    }

    public void onPreTick(Event<Void> tick) {
        runAllQueuePackets(mc.getNetworkHandler());
    }

    public void peekPingPacketIn(Event<CommonPingS2CPacket> packetPing) {
        peekPingRequest += 1;
        lastPingTick = Tasks.getTick();
        //        Debug.info("in", packetPing.getPacketId(), peekPingRequest);
    }

    public void postTickEnd(Event<ClientPlayerEntity> event) {
        runAllPostTickPackets(mc.getNetworkHandler());
        // flush pong packets
        //        for(var pongPacket : delayedPingPackets) {
        //            Listener.sendPacketNoEvents(pongPacket);
        //        }
        //        delayedPingPackets.clear();
        //        hasHandledPongPacket = false;
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
        // runAllQueuePackets(mc.getNetworkHandler());
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
