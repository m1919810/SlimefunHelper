package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import me.matl114.hacks.modules.render.NoRender;
import me.matl114.utils.AttributeUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin extends PlayerEntity {
    public AbstractClientPlayerEntityMixin(World world, GameProfile profile) {
        super(world, BlockPos.ORIGIN, 0, profile);
    }

    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerAbilities;flying:Z"))
    private boolean onNoFlyFov(boolean original) {
        if (NoRender.INSTANCE.noFlyFov()) {
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D"))
    private double onNoSpeedFov(double original) {
        if (NoRender.INSTANCE.noSprintFov()) {
            var attributeContainer = AttributeUtils.copyAttributes(this);
            EntityAttributeInstance entityAttributeInstance =
                    attributeContainer.getCustomInstance(EntityAttributes.MOVEMENT_SPEED);
            if (entityAttributeInstance != null) {
                entityAttributeInstance.removeModifier(Identifier.ofVanilla("sprinting"));
            }
            original = attributeContainer.getValue(EntityAttributes.MOVEMENT_SPEED);
        }
        if (NoRender.INSTANCE.noSlowDownFov()) {
            original = Math.max(original, 0.1F);
        }
        if (NoRender.INSTANCE.noSpeedFov()) {
            original = Math.min(original, 0.135F);
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z"))
    private boolean onNoUseItemFov(boolean original) {
        if (NoRender.INSTANCE.noUseItemFov()) {
            return false;
        }
        return original;
    }
}
