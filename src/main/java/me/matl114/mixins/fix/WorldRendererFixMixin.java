package me.matl114.mixins.fix;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.matl114.hacks.ExtraTasks;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererFixMixin {
    @WrapWithCondition(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WorldRenderer;fillEntityRenderStates(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/client/render/state/WorldRenderState;)V"))
    public boolean onRenderEntity(
            WorldRenderer instance,
            Camera camera,
            Frustum frustum,
            RenderTickCounter tickCounter,
            WorldRenderState renderStates) {
        return !ExtraTasks.getTests().flag2.get();
    }

    @WrapWithCondition(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WorldRenderer;fillBlockEntityRenderStates(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/render/state/WorldRenderState;)V"))
    public boolean onRenderBlockEntity(
            WorldRenderer instance, Camera camera, float tickProgress, WorldRenderState renderStates) {
        return !ExtraTasks.getTests().flag3.get();
    }
}
