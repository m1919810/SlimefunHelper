package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.accessors.hacks.PlayerInternalAccess;
import me.matl114.hacks.modules.combat.CombatExtra;
import me.matl114.hacks.modules.combat.PositionPredict;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.utils.entity.Predictor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity
        implements LivingEntityAccess<PlayerEntity>, EntityInternalAccess<PlayerEntity>, PlayerInternalAccess {
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @ModifyExpressionValue(
            method = "getBlockBreakingSpeed",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/player/PlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D",
                            ordinal = 1))
    private double onBlockBreakingSpeedAttrWrongValueFix(double original) {
        return original < 1E-5 ? 1.0F : original;
    }

    @Override
    public Predictor getPositionPredictor() {
        return PositionPredict.getPlayerPredictor(this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void positionRecordTick(CallbackInfo ci) {
        getPositionPredictor().tick();
    }

    @Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void getBlockInteractionRange(CallbackInfoReturnable<Double> cir) {
        double reach = InteractExtra.INSTANCE.reachDistance.get();
        if (reach > 1E-6) {
            cir.setReturnValue(cir.getReturnValueD() + reach);
        }
    }

    @Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void getEntityInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (CombatExtra.INSTANCE.range.get() > 0.1) {
            cir.setReturnValue(CombatExtra.INSTANCE.getAttackRange());
        }
    }
}
