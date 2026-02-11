package me.matl114.mixins.events;

import me.matl114.managers.input.SimpleInputManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = Keyboard.class, priority = 1)
public abstract class KeyBoardEvents {
    @Inject(
            method = "onKey",
            cancellable = true,
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Keyboard;debugCrashStartTime:J", ordinal = 0))
    private void onKeyboardInput(
            long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (SimpleInputManager.getInstance().onKeyInput(key, scanCode, modifiers, action)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "onChar",
            cancellable = true,
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/Keyboard;client:Lnet/minecraft/client/MinecraftClient;",
                            ordinal = 0))
    private void onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (SimpleInputManager.getInstance().onCharTyped(codePoint, modifiers)) {
            ci.cancel();
        }
    }
}
