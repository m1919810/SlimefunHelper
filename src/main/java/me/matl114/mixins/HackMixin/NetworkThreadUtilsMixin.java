package me.matl114.mixins.HackMixin;

import me.matl114.listenerUtils.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(NetworkThreadUtils.class)
public abstract class NetworkThreadUtilsMixin {
    @Inject(method = "method_11072",at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.BEFORE), cancellable = true)
    private static void applyListenerToPacketPre(PacketListener packetListener, Packet packet, CallbackInfo ci){
        if(Listener.prepacketListenerApplyPoint(packet, packetListener)){
            ci.cancel();
        }
    }
    @Inject(method = "method_11072",at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.AFTER))
    private static void applyListenerToPacketPost(PacketListener packetListener, Packet packet, CallbackInfo ci){
        Listener.postPacketListenerApplyPoint(packet, packetListener);
    }

}
