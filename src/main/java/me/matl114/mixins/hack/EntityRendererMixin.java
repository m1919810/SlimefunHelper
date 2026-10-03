package me.matl114.mixins.hack;

import me.matl114.hacks.RenderTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(
            method = "updateRenderState",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/render/entity/state/EntityRenderState;outlineColor:I",
                            opcode = Opcodes.PUTFIELD,
                            shift = At.Shift.AFTER))
    private void applyCustomHighlight(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        Integer color = null;
        if (RenderTasks.getExplosiveESP() != null) {
            color = RenderTasks.getExplosiveESP().getHighlightColor(entity);
        }
        if (color == null && RenderTasks.getEntityESP() != null) {
            color = RenderTasks.getEntityESP().getHighlightColor(entity);
        }
        if (color != null) {
            state.outlineColor = color;
        }
    }
}
