package me.matl114.mixins.events;

import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderManagerEvents {
    // this method clash with sodium
    //    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true)
    //    public  void onRenderBlockEntity(BlockEntity blockEntity, float tickProgress,
    // ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, CallbackInfoReturnable<BlockEntityRenderState>
    // cir){
    //
    //    }
    //
    @Inject(
            method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target ="Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"),
            cancellable = true)
    private static <T extends BlockEntity> void onRenderBlockEntity(
        BlockEntityRenderer<T> renderer, T blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (blockEntity != null) {
            Event<BlockEntity> event = new Event<>(blockEntity, true, false);
            RenderListener.getBlockEntityRenderListener().handleValue(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }
}
