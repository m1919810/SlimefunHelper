package me.matl114.mixins.HackMixin;

import io.netty.channel.ChannelHandlerContext;
import me.matl114.listenerUtils.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",at=@At("HEAD"),cancellable = true)
    private void acceptPacket(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if(!Listener.acceptS2CPacket((ClientConnection) (Object)this,packet)){
            ci.cancel();
        }
    }
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",at=@At("HEAD"),cancellable = true)
    private void sendPacket(Packet<?> packet, PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
        if(!Listener.sendC2SPacket((ClientConnection) (Object)this,packet)){
            ci.cancel();
        }
    }
//    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",at=@At("HEAD"),cancellable = true)
//    private void sendPacket(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci){
//        if(!Listener.sendC2SPacket(packet)){
//            ci.cancel();
//        }
//    }

}
