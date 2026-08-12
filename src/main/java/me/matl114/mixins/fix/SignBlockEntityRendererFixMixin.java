package me.matl114.mixins.fix;

import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.modules.render.RenderOptimize;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignBlockEntityRenderer.class)
public abstract class SignBlockEntityRendererFixMixin {

    @Inject(
            method =
                    "updateRenderState(Lnet/minecraft/block/entity/SignBlockEntity;Lnet/minecraft/client/render/block/entity/state/SignBlockEntityRenderState;FLnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
            at = @At("RETURN"))
    public void onSignBlockEntityStateUpdate(
            SignBlockEntity signBlockEntity,
            SignBlockEntityRenderState signBlockEntityRenderState,
            float f,
            Vec3d vec3d,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand,
            CallbackInfo ci) {
        RenderOptimize optimize = RenderTasks.getRenderOptimize();
        if (optimize.enableBlockLabelRenderOpt.get()
                && signBlockEntity instanceof MetadataHolder holder
                && holder.getMetadata().get(optimize, RenderOptimize.KEY_RENDER_CONTROL)
                        instanceof RenderOptimize.RenderController controller) {
            if (controller.hideLabelBack()) {
                signBlockEntityRenderState.backText = null;
            }
            if (controller.hideLabelFront()) {
                signBlockEntityRenderState.frontText = null;
            }
        }
    }
}
