package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererEvents {

    @WrapOperation(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
    public boolean onEntityRenderEvent(
        EntityRenderDispatcher instance, Entity entity, Frustum frustum, double x, double y, double z, Operation<Boolean> original) {
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
