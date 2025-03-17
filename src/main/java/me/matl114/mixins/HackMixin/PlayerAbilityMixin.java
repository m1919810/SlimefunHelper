package me.matl114.mixins.HackMixin;

import com.google.common.util.concurrent.AtomicDouble;
import me.matl114.managers.Configs;
import net.minecraft.entity.player.PlayerAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(PlayerAbilities.class)
public class PlayerAbilityMixin {
    @Unique
    private static final AtomicBoolean overrideFly = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_FLY);
//    @Unique
//    private static final AtomicBoolean overrideWalk = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_OVERRIDE_WALK);
    @Unique
    private static final AtomicDouble flySpeed = Configs.MOV_CONFIG.getDouble(Configs.MOVE_SPEED_FLY_VAL);
    @Unique
    private static final AtomicDouble flySpeedCreative = Configs.MOV_CONFIG.getDouble(Configs.MOVE_SPEED_FLY_VAL_CREATIVE);
    @Shadow
    public boolean creativeMode;
//    @Unique
//    private static final AtomicDouble walkSpeed = Configs.MOV_CONFIG.getDouble(Configs.MOVE_SPEED_WALK_VAL);
    @Inject(method = "getFlySpeed",at = @At("HEAD"), cancellable = true)
    public void getFlySpeed(CallbackInfoReturnable<Float> cir) {
        if(overrideFly.get()) {
            cir.setReturnValue(creativeMode? flySpeedCreative.floatValue(): flySpeed.floatValue());
        }
    }
//    @Inject(method = "getWalkSpeed",at = @At("HEAD"), cancellable = true)
//    public void getWalkSpeed(CallbackInfoReturnable<Float> cir) {
//        if(overrideWalk.get()) {
//            cir.setReturnValue((float)walkSpeed.get());
//        }
//    }

}
