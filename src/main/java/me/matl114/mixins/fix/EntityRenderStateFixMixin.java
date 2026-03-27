package me.matl114.mixins.fix;

import me.matl114.hacks.RenderTasks;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRenderStateFixMixin {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void getDisplayName(Entity entity, CallbackInfoReturnable<Text> cir) {
        if (RenderTasks.getRenderOptimize().shouldCancelShowDisplayName(entity)) {
            cir.setReturnValue(null);
        }
    }
}
