package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import me.matl114.hacks.modules.extra.BadPacketsFix;
import me.matl114.hacks.modules.move.AutoResync;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.Vec3d;
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
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;setPosition(Lnet/minecraft/entity/EntityPosition;Ljava/util/Set;Lnet/minecraft/entity/Entity;Z)Z"))
    private boolean wrapSetPositionLook(
            EntityPosition pos, Set<PositionFlag> flags, Entity entity, boolean bl, Operation<Boolean> original) {
        if (AutoResync.INSTANCE.noVelocitySetback.get()) {
            Vec3d currentVelocity = entity.getVelocity();
            boolean re = original.call(pos, flags, entity, bl);
            entity.setVelocity(currentVelocity);
            return re;
        } else {
            return original.call(pos, flags, entity, bl);
        }
    }
}
