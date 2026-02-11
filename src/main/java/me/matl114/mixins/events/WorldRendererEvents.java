package me.matl114.mixins.events;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import me.matl114.events.RenderListener;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererEvents {

    @Inject(at = @At("RETURN"), method = "render")
    public void renderMore(
        ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);
        // fixme: Event

        RenderListener.renderMoreTasks(matrixStack, tickCounter.getTickProgress(false));
    }
}
