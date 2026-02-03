package me.matl114.mixins.hack;

import me.matl114.hacks.MovTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAbilities.class)
@Environment(EnvType.CLIENT)
public class PlayerAbilityMixin {

    @Inject(method = "getFlySpeed",at = @At("HEAD"), cancellable = true)
    public void getFlySpeed(CallbackInfoReturnable<Float> cir) {
        if(MovTasks.getCreativeFlight().overrideFlySpeed.get()) {
            cir.setReturnValue((float) MovTasks.getCreativeFlight().getOverridingFlySpeed());
        }
    }


}
