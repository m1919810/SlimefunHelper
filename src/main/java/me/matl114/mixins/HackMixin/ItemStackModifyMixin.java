package me.matl114.mixins.HackMixin;

import me.matl114.access.ItemStackAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStack.class)
@Environment(EnvType.CLIENT)
public class ItemStackModifyMixin implements ItemStackAccess {


    @Override
    public void setItem(Item item) {

    }
}
