package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.render.HandView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @WrapOperation(
            method = "renderArm",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;)V"))
    private void applyArmEffect(
            OrderedRenderCommandQueue queue,
            ModelPart part,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            Sprite sprite,
            Operation<Void> original) {
        if (HandView.INSTANCE == null || !HandView.INSTANCE.useArmEffect()) {
            original.call(queue, part, matrices, renderLayer, light, overlay, sprite);
            return;
        }

        queue.submitModelPart(
                part,
                matrices,
                renderLayer,
                light,
                overlay,
                sprite,
                false,
                HandView.INSTANCE.armHasGlint(),
                HandView.INSTANCE.armTint.get() ? HandView.INSTANCE.armTintColor() : -1,
                (ModelCommandRenderer.CrumblingOverlayCommand) null,
                0);
    }
}
