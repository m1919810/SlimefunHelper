package me.matl114.SlimefunMixin.FixMixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SharedConstants.class)
public abstract class ConstantModifyMixin {
    @Inject(method = "isValidChar",at = @At("HEAD"),cancellable = true)
    private static void isValidChar(char c, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
