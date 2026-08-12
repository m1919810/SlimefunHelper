package me.matl114.hooks.mixin.baritone;

import com.mojang.authlib.GameProfile;
import java.util.Objects;
import me.matl114.accessors.events.ClientPlayerEntityAccess;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hooks.BaritoneHooks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class BaritoneClientPlayerEntityRotFixMixin extends AbstractClientPlayerEntity
        implements ClientPlayerEntityAccess {
    @Shadow
    public abstract float getPitch(float tickProgress);

    @Shadow
    public abstract float getYaw(float tickProgress);

    @Unique
    Vec2f storedPreBaritonePitchYaw;

    public BaritoneClientPlayerEntityRotFixMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
                            shift = At.Shift.AFTER),
            order = 999)
    private void onPreBaritonePlayerUpdateEvent(CallbackInfo ci) {
        if (checkClientPlayer()
                && BaritoneHooks.getInstance().isBaritoneAPISupported()
                && (BaritoneHooks.getInstance().isBaritonePathing()
                        || BaritoneHooks.getInstance().isBaritoneElytraProcessing())) {
            LegalMovementManager manager = getLegalMovementManager();
            Vec2f rotModify =
                    BaritoneHooks.getInstance().getBaritoneCurrentMoveRot(MinecraftClient.getInstance().player);
            Vec2f currentPY = new Vec2f(getPitch(), getYaw());
            if (rotModify != null && !Objects.equals(rotModify, currentPY)) {
                if (manager.isResetRot()) {
                    storedPreBaritonePitchYaw = currentPY;
                    manager.playerStatus.restoreRotation();
                }
            }
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
                            shift = At.Shift.AFTER),
            order = 1111)
    private void onPostBaritonePlayerUpdateEvent(CallbackInfo ci) {
        if (storedPreBaritonePitchYaw != null) {
            float pitch = getPitch();
            float yaw = getYaw();
            LegalMovementManager manager = getLegalMovementManager();
            if (pitch != manager.playerStatus.pitch || yaw != manager.playerStatus.yaw) {
                // mark that Baritone change the rotation
            } else {
                setPitch(storedPreBaritonePitchYaw.x);
                setYaw(storedPreBaritonePitchYaw.y);
            }
            storedPreBaritonePitchYaw = null;
        }
    }
}
