package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.combat.SpearEnhance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Item.class)
public abstract class ItemMixin {
    @WrapOperation(
            method = "getUseAction",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/item/ItemStack;contains(Lnet/minecraft/component/ComponentType;)Z",
                            ordinal = 1))
    private boolean fixSpearUse1(ItemStack instance, ComponentType componentType, Operation<Boolean> original) {
        if (componentType == DataComponentTypes.KINETIC_WEAPON && SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            return SpearEnhance.INSTANCE.getRealComponent(instance) != null;
        }
        return original.call(instance, componentType);
    }

    @WrapOperation(
            method = "getMaxUseTime",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/item/ItemStack;contains(Lnet/minecraft/component/ComponentType;)Z",
                            ordinal = 1))
    private boolean fixSpearUse2(ItemStack instance, ComponentType componentType, Operation<Boolean> original) {
        if (componentType == DataComponentTypes.KINETIC_WEAPON && SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            return SpearEnhance.INSTANCE.getRealComponent(instance) != null;
        }
        return original.call(instance, componentType);
    }
}
