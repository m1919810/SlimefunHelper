package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.PacketApplyBatcher;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(PacketApplyBatcher.Entry.class)
public abstract class NetworkThreadUtilsEvents {
    @WrapOperation(
            method = "apply",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private void wrapPacketHandle(Packet instance, PacketListener t, Operation<Void> original) {
        Listener.callPacketHandleEvent(instance, t, original::call);
    }
}
