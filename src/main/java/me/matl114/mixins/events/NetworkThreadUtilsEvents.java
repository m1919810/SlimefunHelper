package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.crash.CrashException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(NetworkThreadUtils.class)
public abstract class NetworkThreadUtilsEvents {
    @WrapOperation(
            method = "method_11072",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private static void wrapPacketHandle(Packet instance, PacketListener t, Operation<Void> original) {
        if (!Listener.prepacketListenerApplyPoint(instance, t)) {
            try {
                original.call(instance, t);
            } catch (Throwable e) {
                if (e instanceof CrashException crashException
                        && crashException.getCause() instanceof OutOfMemoryError) {
                    throw e;
                }
                if (!Listener.getPacketListenerException().isEmpty()) {
                    Event<Packet<?>> exevent = new Event<>(instance, true, false, t, e);
                    Listener.getPacketListenerException().handleValue(exevent);
                    if (!exevent.isCancelled()) {
                        throw e;
                    }
                }
            } finally {
                Listener.postPacketListenerApplyPoint(instance, t);
            }
        }
    }
}
