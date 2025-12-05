package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hackUtils.NetworksTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
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
    private static final Config.FlagRef hijackException = Configs.TEST_CONFIG.getBoolean(Configs.IGNORE_PROTOCOL_ERROR);
    @Inject(method = "method_11072", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/listener/PacketListener;onPacketException(Lnet/minecraft/network/packet/Packet;Ljava/lang/Exception;)V", shift = At.Shift.BEFORE), cancellable = true)
    private static void hijackExceptionDisconnection(PacketListener packetListener, Packet packet, CallbackInfo ci, @Local Exception exception){
        if(hijackException.get()){
            NetworksTasks.handlePacketExceptionSoft(packetListener, packet, exception);
            ci.cancel();
        }
    }

}
