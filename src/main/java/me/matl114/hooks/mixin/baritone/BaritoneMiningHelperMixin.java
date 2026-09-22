package me.matl114.hooks.mixin.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.utils.BlockBreakHelper;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.hacks.modules.survival.BaritoneFix;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(BlockBreakHelper.class)
public abstract class BaritoneMiningHelperMixin {
    @WrapOperation(
            method = "Lbaritone/utils/BlockBreakHelper;tick(Z)V",
            at = @At(value = "FIELD", target = "Lbaritone/api/Settings$Setting;value:Ljava/lang/Object;"),
            require = 0,
            expect = 0,
            remap = false)
    private Object onOverrideMiningCooldownBaritone(Settings.Setting instance, Operation<Object> original) {
        if (instance == BaritoneAPI.getSettings().blockBreakSpeed) {
            if (BaritoneFix.INSTANCE.enableMiningCooldown.get()) {
                return (Integer) MineExtra.INSTANCE.getMiningPacketCooldown(1);
            }
        }
        return original.call(instance);
    }

    @WrapWithCondition(
            method = "Lbaritone/utils/BlockBreakHelper;tick(Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V",
                            ordinal = 0),
            require = 0,
            expect = 0,
            remap = false)
    private boolean onStopSwingHand1(ClientPlayerEntity instance, Hand hand) {
        if (BaritoneFix.INSTANCE.applyMineSettingsToBaritone.get() && MineExtra.INSTANCE.noSwing.get()) {
            return false;
        }
        return true;
    }

    @WrapWithCondition(
            method = "Lbaritone/utils/BlockBreakHelper;tick(Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V",
                            ordinal = 1),
            require = 0,
            expect = 0,
            remap = false)
    private boolean onStopSwingHand2(ClientPlayerEntity instance, Hand hand) {
        if (BaritoneFix.INSTANCE.applyMineSettingsToBaritone.get() && MineExtra.INSTANCE.noSwing.get()) {
            return false;
        }
        return true;
    }
}
