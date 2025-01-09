package me.matl114.SlimefunMixin.HackMixin;

import me.matl114.HackUtils.RenderTasks;
import me.matl114.ListenerUtils.Listener;
import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.ParticleTypes;
import org.bukkit.Particle;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Inject(method = "handlePacket",at=@At("HEAD"),cancellable = true)
    private static void acceptPacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        if(!Listener.acceptS2CPacket(packet)){
            ci.cancel();
        }
    }
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",at=@At("HEAD"),cancellable = true)
    private void sendPacket(Packet<?> packet, PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
        if(!Listener.sendC2SPacket(packet)){
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
