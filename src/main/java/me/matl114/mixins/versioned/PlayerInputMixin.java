package me.matl114.mixins.versioned;

import me.matl114.versioned.accessors.PlayerInputAccess;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class PlayerInputMixin implements PlayerInputAccess {
    @Shadow
    @Final
    private GameOptions settings;

    @Unique
    boolean pressingSprint;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        pressingSprint = this.settings.sprintKey.isPressed();
    }

    @Unique
    public boolean isPressingSprint() {
        return pressingSprint;
    }

    @Unique
    public void setPressingSprint(boolean pressingSprint) {
        this.pressingSprint = pressingSprint;
    }
}
