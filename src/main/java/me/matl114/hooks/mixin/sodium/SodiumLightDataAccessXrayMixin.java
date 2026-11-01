package me.matl114.hooks.mixin.sodium;

import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
public abstract class SodiumLightDataAccessXrayMixin {
    @Unique
    private static final int slimefunhelper$fullLight = 15 | (15 << 4) | (15 << 8);

    @Shadow
    protected BlockRenderView level;

    @Shadow
    @Final
    private BlockPos.Mutable pos;

    @Unique
    private Xray slimefunhelper$xray;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0, expect = 0)
    private void slimefunhelper$initializeXray(CallbackInfo ci) {
        slimefunhelper$xray = Xray.INSTANCE;
    }

    @ModifyVariable(method = "compute", at = @At("TAIL"), name = "bl", require = 0, expect = 0)
    private int slimefunhelper$brightenWhitelist(int original) {
        if (slimefunhelper$xray != null && slimefunhelper$xray.isActive()) {
            BlockState state = level.getBlockState(pos);
            if (!slimefunhelper$xray.isBlocked(state.getBlock(), pos)) {
                return slimefunhelper$fullLight;
            }
        }

        return original;
    }
}
