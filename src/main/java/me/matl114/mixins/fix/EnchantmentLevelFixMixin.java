package me.matl114.mixins.fix;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemEnchantmentsComponent.class)
public abstract class EnchantmentLevelFixMixin {
    @WrapOperation(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lit/unimi/dsi/fastutil/objects/Object2IntMap$Entry;getIntValue()I",
                            remap = false))
    public int init(Object2IntMap.Entry instance, Operation<Integer> original) {
        return MathHelper.clamp(instance.getIntValue(), 0, 255);
    }

    @ModifyArg(
            method = "<clinit>",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;",
                            remap = false),
            index = 1)
    private static int rewriteLevel(int maxInclusive) {
        return Integer.MAX_VALUE;
    }
}
