package me.matl114.hooks.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class SodiumBlockRenderContextXrayMixin {
    @Shadow
    protected BlockState state;

    @Shadow
    protected BlockRenderView level;

    @Shadow
    protected BlockPos pos;

    @Unique
    private Xray slimefunhelper$xray;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0, expect = 0)
    private void slimefunhelper$initializeXray(CallbackInfo ci) {
        slimefunhelper$xray = Xray.INSTANCE;
    }

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void slimefunhelper$forceWhitelistedFace(Direction facing, CallbackInfoReturnable<Boolean> cir) {
        if (slimefunhelper$xray != null
                && slimefunhelper$xray.isActive()
                && !slimefunhelper$xray.isBlocked(state.getBlock(), null)) {
            cir.setReturnValue(true);
        }
    }

    @ModifyReturnValue(method = "shouldDrawSide", at = @At("RETURN"), require = 0, expect = 0)
    private boolean slimefunhelper$modifyDrawSide(boolean original, Direction facing) {
        if (slimefunhelper$xray == null || !slimefunhelper$xray.isActive()) return original;
        return slimefunhelper$xray.modifyDrawSide(state, level, pos, facing, original);
    }
}
