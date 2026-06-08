package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.combat.SpearEnhance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.Lancing;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Lancing.class)
public abstract class LancingMixin {
    @WrapOperation(
            method = "positionArmForSpear",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender1(ItemStack instance, ComponentType componentType, Operation<Object> original) {
        if (SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            return SpearEnhance.INSTANCE.getRealComponent(instance);
        }
        return original.call(instance, componentType);
    }

    @WrapOperation(
            method = "method_75392",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender2(ItemStack instance, ComponentType componentType, Operation<Object> original) {
        if (SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            return SpearEnhance.INSTANCE.getRealComponent(instance);
        }
        return original.call(instance, componentType);
    }

    @WrapOperation(
            method = "method_75395",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender3(ItemStack instance, ComponentType componentType, Operation<Object> original) {
        if (SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            return SpearEnhance.INSTANCE.getRealComponent(instance);
        }
        return original.call(instance, componentType);
    }

    @WrapOperation(
            method = "method_75396",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender4(ItemStack instance, ComponentType componentType, Operation<Object> original) {
        if (SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            return SpearEnhance.INSTANCE.getRealComponent(instance);
        }
        return original.call(instance, componentType);
    }
}
