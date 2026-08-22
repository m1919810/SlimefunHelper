package me.matl114.hooks.mixin.xaero;

import java.util.List;
import me.matl114.hacks.modules.survival.XaeroHelper;
import me.matl114.hooks.BaritoneHooks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaeroplus.util.BaritonePathHelper;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(BaritonePathHelper.class)
public abstract class XaeroPlusBaritonePathHelperMixin {
    @Inject(method = "getBaritonePath", at = @At("HEAD"), cancellable = true, expect = 0, require = 0, remap = false)
    private static void hookGetBaritonePath(CallbackInfoReturnable<List<BlockPos>> cir) {
        if (XaeroHelper.INSTANCE.xplusBaritonePathFix.get()) {
            if (BaritoneHooks.getInstance().isBaritoneElytraProcessing()) {
                List<BlockPos> currentBlockPos = BaritoneHooks.getInstance().getCurrentNetherPath();
                if (!currentBlockPos.isEmpty()) {
                    cir.setReturnValue(currentBlockPos);
                    return;
                }
            }
        }
    }
}
