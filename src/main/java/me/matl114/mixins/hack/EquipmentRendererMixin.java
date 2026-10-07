package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {
    @WrapWithCondition(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                            ordinal = 2))
    private <S> boolean noArmorRender0(
            RenderCommandQueue instance,
            Model<? super S> model,
            S s,
            MatrixStack stack,
            RenderLayer renderLayer,
            int i1,
            int i2,
            int i3,
            @Nullable Sprite sprite,
            int i4,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand) {
        if (NoRender.INSTANCE.noArmorTrimRender()) {
            return false;
        }
        return true;
    }

    @WrapWithCondition(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                            ordinal = 0))
    private <S> boolean noArmorRender(
            RenderCommandQueue instance,
            Model<? super S> model,
            S s,
            MatrixStack stack,
            RenderLayer renderLayer,
            int i1,
            int i2,
            int i3,
            @Nullable Sprite sprite,
            int i4,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand) {
        if (NoRender.INSTANCE.noArmorRender()) {
            return false;
        }
        return true;
    }

    @WrapWithCondition(
            method =
                    "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                            ordinal = 1))
    private <S> boolean noArmorRender2(
            RenderCommandQueue instance,
            Model<? super S> model,
            S s,
            MatrixStack stack,
            RenderLayer renderLayer,
            int i1,
            int i2,
            int i3,
            @Nullable Sprite sprite,
            int i4,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand) {
        if (NoRender.INSTANCE.noArmorGlintRender()) {
            return false;
        }
        return true;
    }
}
