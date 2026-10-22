package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ArmorFeatureRenderer.class)
public abstract class EquipmentRendererMixin {
    @Inject(method = "renderArmorParts", at = @At("HEAD"), cancellable = true)
    private void hideArmor(CallbackInfo ci) {
        if (NoRender.INSTANCE.noArmorRender()) ci.cancel();
    }

    @Inject(method = "renderTrim", at = @At("HEAD"), cancellable = true)
    private void hideArmorTrim(CallbackInfo ci) {
        if (NoRender.INSTANCE.noArmorTrimRender()) ci.cancel();
    }

    @Inject(method = "renderGlint", at = @At("HEAD"), cancellable = true)
    private void hideArmorGlint(CallbackInfo ci) {
        if (NoRender.INSTANCE.noArmorGlintRender()) ci.cancel();
    }
}
