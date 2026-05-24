package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.modules.move.ElytraExtra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BipedEntityRenderer.class)
public abstract class EntityRenderStateMixin {
    @ModifyExpressionValue(
            method = "updateBipedRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isFallFlying()Z"))
    private static boolean updateBipedRenderState(boolean original, @Local(argsOnly = true) LivingEntity livingEntity) {
        if (livingEntity.isFallFlying() && livingEntity == MinecraftClient.getInstance().player) {
            ElytraExtra elytraExtra = MovTasks.getElytraExtra();
            if (elytraExtra.renderFix.get() && elytraExtra.isCurrentArmorGliding()) {
                return false;
            }
        }
        return original;
    }
}
