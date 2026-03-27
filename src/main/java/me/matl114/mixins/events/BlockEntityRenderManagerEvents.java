package me.matl114.mixins.events;

import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderManager.class)
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
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"),
            cancellable = true)
    public void onRenderBlockEntity(
            BlockEntityRenderState renderState,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraRenderState,
            CallbackInfo ci) {
        BlockPos pos = renderState.pos;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world != null) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                Event<BlockEntity> event = new Event<>(blockEntity, true, false);
                RenderListener.getBlockEntityRenderListener().handleValue(event);
                if (event.isCancelled()) {
                    ci.cancel();
                }
            }
        }
    }
}
