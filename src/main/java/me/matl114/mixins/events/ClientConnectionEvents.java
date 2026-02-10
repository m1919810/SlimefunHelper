package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.handler.PacketSizeLogger;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public class ClientConnectionEvents {
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
            method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void sendPacket(
            Packet<?> packet,
            PacketCallbacks callbacks,
            boolean flush,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<Packet<?>> packetLocalRef) {
        // fix: null values from cancelled send Events
        if (packet == null) {
            ci.cancel();
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

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("RETURN"))
    private void sendPacketPost(Packet<?> packet, PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
        Listener.getPacketPostSendPoint().broadcast(packet);
    }

    @Inject(
            method = "handlePacket",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V",
                            shift = At.Shift.BEFORE))
    private static <T extends PacketListener> void applyListenerToPacket(
            Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        if (Listener.prepacketListenerApplyPoint(packet, listener)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handlePacket",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V",
                            shift = At.Shift.AFTER))
    private static <T extends PacketListener> void applyListenerToPacketPost(
            Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        Listener.postPacketListenerApplyPoint(packet, listener);
    }

    @Inject(method = "addHandlers", at = @At("HEAD"))
    private static void proxyChannelIp(
            ChannelPipeline pipeline,
            NetworkSide side,
            boolean local,
            PacketSizeLogger packetSizeLogger,
            CallbackInfo ci) {
        Listener.getConnectionChannelInitialize().broadcast(pipeline, side);
    }
}
