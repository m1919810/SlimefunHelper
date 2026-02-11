package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class LivingEntityEvents extends Entity implements EntityAccess<LivingEntity> {

    public LivingEntityEvents(EntityType<?> type, World world) {
        super(type, world);
    }

    @Unique
    Integer nextJumpCooldown;

    @Shadow
    private int jumpingCooldown;

    @Shadow
    protected int glidingTicks;

    @WrapOperation(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;jump()V"))
    private void onJump(LivingEntity instance, Operation<Void> original) {
        if ((Entity) this == ((Entity) MinecraftClient.getInstance().player)) {
            // 10 sec
            Event<Integer> jumpEvent = new Event<>(10, true, true);
            Listener.getPlayerNotFlyJumpPoint().handleValue(jumpEvent);
            nextJumpCooldown = jumpEvent.context();
            if (jumpEvent.isCancelled()) {

            } else {
                original.call(instance);
            }
        } else {
            original.call(instance);
        }
    }

    @Inject(
            method = "tickMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/LivingEntity;isFallFlying()Z",
                            shift = At.Shift.BEFORE))
    private void overrideJumpCooldown(CallbackInfo ci) {
        if (nextJumpCooldown != null) {
            jumpingCooldown = nextJumpCooldown;
            nextJumpCooldown = null;
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/LivingEntity;isFallFlying()Z",
                            shift = At.Shift.BEFORE))
    private void onWriteFlyingTicks(CallbackInfo ci) {
        if (checkClientPlayer()) {
            Event<Integer> fallFlyingEvent = new Event<>(this.glidingTicks + 1, true, true);
            Listener.getPlayerFallFlyingTick().handleValue(fallFlyingEvent);
            if (fallFlyingEvent.isCancelled()) {
                this.glidingTicks -= 1;
            } else {
                this.glidingTicks = fallFlyingEvent.context() - 1;
            }
        }
    }
}
