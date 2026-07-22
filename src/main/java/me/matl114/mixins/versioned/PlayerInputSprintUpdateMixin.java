package me.matl114.mixins.versioned;

import me.matl114.versioned.accessors.PlayerInputAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class PlayerInputSprintUpdateMixin {
    @Shadow
    public Input input;

    @Shadow
    @Final
    protected MinecraftClient client;

    @Inject(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.BEFORE))
    private void onTickMovement(CallbackInfo ci) {
        PlayerInputAccess.of(this.input).setPressingSprint(this.client.options.sprintKey.isPressed());
    }
}
