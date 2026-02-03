package me.matl114.mixins.fix;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemEnchantmentsComponent.class)
public abstract class EnchantmentLevelFixMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/Object2IntMap$Entry;getIntValue()I"))
    public int init(Object2IntMap.Entry instance){
        return MathHelper.clamp(instance.getIntValue(),0, 255);
    }

    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;"), index = 1)
    private static int rewriteLevel(int maxInclusive){
        return Integer.MAX_VALUE;
    }
}
