package me.matl114.mixins.FixMixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.util.StringHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(StringHelper.class)
public abstract class ConstantModifyMixin {
    @Inject(method = "isValidChar",at = @At("HEAD"),cancellable = true)
    private static void isValidChar(char c, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(c >= ' ' && c != 127);
    }
}
