package me.matl114.mixins.HackMixin;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import me.matl114.access.ClientConnectionAccess;
import me.matl114.hackUtils.HttpTasks;
import me.matl114.listenerUtils.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.handler.PacketSizeLogger;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

import static net.minecraft.network.ClientConnection.CLIENT_IO_GROUP;
import static net.minecraft.network.ClientConnection.EPOLL_CLIENT_IO_GROUP;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin implements ClientConnectionAccess {

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

    @Inject(method = "handlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V",shift = At.Shift.BEFORE))
    private static <T extends PacketListener> void applyListenerToPacket(Packet<T> packet, PacketListener listener, CallbackInfo ci){
        if(Listener.prepacketListenerApplyPoint(packet,listener)){
            ci.cancel();
        }
    }

    @Inject(method = "handlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V",shift = At.Shift.AFTER))
    private static <T extends PacketListener> void applyListenerToPacketPost(Packet<T> packet, PacketListener listener, CallbackInfo ci){
        Listener.postPacketListenerApplyPoint(packet,listener);
    }


//    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",at=@At("HEAD"),cancellable = true)
//    private void sendPacket(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci){
//        if(!Listener.sendC2SPacket(packet)){
//            ci.cancel();
//        }
//    }
//    @Redirect(method = "connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;",at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;"))
//    private static ChannelFuture proxyIp(io.netty.bootstrap.Bootstrap instance, InetAddress inetHost, int inetPort){
//        Tasks.redirectIp(instance);
//        return instance.connect(inetHost, inetPort);
//    }\
    @Shadow
    PacketSizeLogger packetSizeLogger;
    @Shadow private Channel channel;

    public PacketSizeLogger getPacketSizeLogger(){
        return packetSizeLogger;
    }



    @Inject(method = "addHandlers", at = @At("HEAD"))
    private static void proxyChannelIp(ChannelPipeline pipeline, NetworkSide side, boolean local, PacketSizeLogger packetSizeLogger, CallbackInfo ci){
        if(side == NetworkSide.CLIENTBOUND){
            HttpTasks.redirectIpPre(pipeline);
        }
    }




}
