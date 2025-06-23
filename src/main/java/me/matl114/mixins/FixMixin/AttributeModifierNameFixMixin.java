package me.matl114.mixins.FixMixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(EntityAttributeModifier.class)
public abstract class AttributeModifierNameFixMixin {
    @Final
    @Shadow
    private String name;

    @Unique
    public String getAttributeModifierName(){
        return this.name;
    }
    @Redirect(method = "toNbt",at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtCompound;putString(Ljava/lang/String;Ljava/lang/String;)V"))
    private void fixNullNames(NbtCompound instance, String key, String value){
        if(value != null){
            instance.putString(key, value);
        }
    }

}
