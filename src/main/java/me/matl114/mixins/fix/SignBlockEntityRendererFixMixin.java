package me.matl114.mixins.fix;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.accessors.events.MetadataHolder;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.modules.render.RenderOptimize;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SignBlockEntityRenderer.class)
public abstract class SignBlockEntityRendererFixMixin {

    @WrapWithCondition(
            method =
                    "render(Lnet/minecraft/block/entity/SignBlockEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/block/BlockState;Lnet/minecraft/block/AbstractSignBlock;Lnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/SignBlockEntityRenderer;renderText(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/SignText;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IIIZ)V"))
    public boolean onSignBlockEntityStateUpdate(
        SignBlockEntityRenderer instance, BlockPos pos, SignText signText, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int lineHeight, int lineWidth, boolean front, @Local(argsOnly = true) SignBlockEntity signBlockEntity) {
        RenderOptimize optimize = RenderTasks.getRenderOptimize();
        if (optimize.enableBlockLabelRenderOpt.get()
                && signBlockEntity instanceof MetadataHolder holder
                && holder.getMetadata().get(optimize, RenderOptimize.KEY_RENDER_CONTROL)
                        instanceof RenderOptimize.RenderController controller) {
            if (controller.hideLabelBack() && !front) {
                return false;
            }
            if (controller.hideLabelFront() && front) {
                return false;
            }
        }
        return true;
    }
}
