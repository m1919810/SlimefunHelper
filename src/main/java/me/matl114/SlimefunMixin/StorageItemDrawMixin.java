package me.matl114.SlimefunMixin;

import me.matl114.SlimefunUtils.StorageItemRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Deprecated
@Environment(value= EnvType.CLIENT)
@Mixin(DrawContext.class)
public abstract class StorageItemDrawMixin {
    @Inject(
            method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onStorageItemDraw(LivingEntity entity, World world, ItemStack itemStack, int x, int y, int seed, int z, CallbackInfo ci){
//        DrawContext self = (DrawContext)(Object)this;
//        StorageItemRenderer.render( self,entity,world, itemStack, x, y,z,seed);
    }
}
