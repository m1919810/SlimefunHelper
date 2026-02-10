package me.matl114.mixins.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourcePackPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ResourcePackPosition.class)
public abstract class ResourcePackPositionMixin {

    @Inject(method = "fixedPosition", at = @At("HEAD"), cancellable = true)
    private void ignoreFixPosition(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
    //    @Inject(method = "required", at = @At("HEAD"), cancellable = true)
    //    private void ignoreRequired(CallbackInfoReturnable<Boolean> cir){
    //       // cir.setReturnValue(false);
    //    }
}
