package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.extra.BadPacketsFix;
import me.matl114.hacks.modules.move.AutoResync;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(
            method = "onPlayerList",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
                            shift = At.Shift.BEFORE,
                            remap = false),
            cancellable = true)
    public void onInvalidPlayerEntry(PlayerListS2CPacket packet, CallbackInfo ci) {
        if (BadPacketsFix.INSTANCE.fixInvalidPlayerEntryUpdate.get()) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "onPlayerPositionLook",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(DDD)V"))
    private void wrapSetPositionLook(PlayerEntity instance, double v, double v2, double v3, Operation<Void> original) {
        if (AutoResync.INSTANCE.noVelocitySetback.get()) {
            return;
        } else {
            original.call(instance, v, v2, v3);
        }
    }
}
