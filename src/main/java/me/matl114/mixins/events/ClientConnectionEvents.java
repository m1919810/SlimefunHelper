package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.netty.channel.*;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import me.matl114.accessors.events.ClientConnectionAccess;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.PacketSizeLogger;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public abstract class ClientConnectionEvents extends SimpleChannelInboundHandler<Packet<?>>
        implements ClientConnectionAccess {
    @Shadow
    protected abstract void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet);

    @Shadow
    public Channel channel;

    @Unique
    boolean delayInbound;

    @Unique
    private final Deque<Packet<?>> incomePackets = new ConcurrentLinkedDeque<>();

    @Unique
    boolean isProcessingQueue;

    @Unique
    public void stopInBoundDelay() {
        this.channel.eventLoop().execute(this::processDelayedQueue);
    }

    @Unique
    public void startInBoundDelay() {
        this.channel.eventLoop().execute(() -> {
            delayInbound = true;
        });
    }

    public void startInBoundDelayImmediately() {
        delayInbound = true;
    }

    @Unique
    public boolean isInBoundDelay() {
        return delayInbound;
    }

    public void addPacketInBoundDelayQueue(Packet<?> packet) {
        if (delayInbound) {
            incomePackets.add(packet);
        } else {
            this.channel.eventLoop().execute(() -> {
                ChannelHandlerContext ctx = this.channel.pipeline().context(this);
                channelRead0(ctx, packet);
            });
        }
    }

    @Unique
    private void processDelayedQueue() {
        if (isProcessingQueue) return;
        isProcessingQueue = true;

        try {
            ChannelHandlerContext ctx = this.channel.pipeline().context(this);

            Packet<?> packet;
            while ((packet = incomePackets.poll()) != null) {
                channelRead0(ctx, packet);
            }
        } finally {
            isProcessingQueue = false;
            if (incomePackets.isEmpty()) {
                delayInbound = false;
            } else {
                this.channel.eventLoop().execute(this::processDelayedQueue);
            }
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void acceptPacket(
            ChannelHandlerContext channelHandlerContext,
            Packet<?> packet,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<Packet<?>> packetLocalRef) {
        if (packet == null) {
            ci.cancel();
            return;
        }
        if (delayInbound && !isProcessingQueue) {
            incomePackets.add(packet);
            ci.cancel();
            return;
        }
        Packet<?> packetToRecv = Listener.acceptS2CPacket((ClientConnection) (Object) this, packet);
        if (packetToRecv != packet) {
            if (packetToRecv == null) {
                ci.cancel();
            } else {
                packetLocalRef.set(packetToRecv);
            }
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void sendPacket(
            Packet<?> packet,
            ChannelFutureListener listener,
            boolean flush,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<Packet<?>> packetLocalRef) {
        // fix: null values from cancelled send Events
        if (packet == null) {
            ci.cancel();
            return;
        }
        Packet<?> packetToSend = Listener.sendC2SPacket((ClientConnection) (Object) this, packet);
        if (packetToSend != packet) {
            if (packetToSend == null) {
                ci.cancel();
            } else {
                packetLocalRef.set(packetToSend);
            }
        }
    }

    @Inject(method = "sendInternal", at = @At("RETURN"))
    private void sendPacketPost(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        Listener.getPacketPostSendPoint().broadcast(packet);
    }

    @WrapOperation(
            method = "handlePacket",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private static void applyPacketMainThread(Packet instance, PacketListener t, Operation<Void> original) {
        if (!MinecraftClient.getInstance().isOnThread() && !Listener.isAsyncImportantPacket(instance)) {
            original.call(instance, t);
            return;
        } else {
            Listener.callPacketHandleEvent(instance, t, original::call);
        }
    }

    @Inject(method = "addHandlers", at = @At("HEAD"))
    private static void proxyChannelIp(
            ChannelPipeline pipeline,
            NetworkSide side,
            boolean local,
            PacketSizeLogger packetSizeLogger,
            CallbackInfo ci) {
        Listener.getConnectionChannelInitialize().broadcast(pipeline, side, local);
    }

    @Override
    public void handlePacket(Packet<?> packet) {
        try {
            channelRead0(null, packet);
        } catch (NullPointerException e) {
            return;
        } catch (Throwable e) {
            throw e;
        }
    }
}
