package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererEvents {
    @Shadow
    public abstract void tick(Camera camera);

    @Inject(at = @At("RETURN"), method = "render")
    public void renderMore(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f basicProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);
        RenderListener.renderWorldTasks(matrixStack, tickCounter.getTickProgress(false));
    }

    @WrapOperation(
            method = "fillEntityRenderStates",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
    public boolean onEntityRenderEvent(
            EntityRenderManager instance,
            Entity entity,
            Frustum frustum,
            double x,
            double y,
            double z,
            Operation<Boolean> original) {
        Event<Entity> event = new Event<>(entity, true, false);
        RenderListener.getEntityRenderListener().handleValue(event);
        if (event.isCancelled()) {
            return false;
        }
        return original.call(instance, entity, frustum, x, y, z);
    }
    // this mixin clash with sodium mixin

    //    @WrapOperation(method = "fillBlockEntityRenderStates", at = @At(value = "INVOKE", target =
    // "Lnet/minecraft/client/render/block/entity/BlockEntityRenderManager;getRenderState(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;", ordinal = 0))
    //    public BlockEntityRenderState onBlockEntityRenderState(BlockEntityRenderManager instance, BlockEntity
    // blockEntity, float tickProgress, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
    // Operation<BlockEntityRenderState> original){
    //        Event<BlockEntity> event = new Event<>(blockEntity, true, false);
    //        RenderListener.getBlockEntityRenderListener().handleValue(event);
    //        if(event.isCancelled()){
    //            return null;
    //        }else {
    //            return original.call(instance, blockEntity, tickProgress, crumblingOverlay);
    //        }
    //    }
}
