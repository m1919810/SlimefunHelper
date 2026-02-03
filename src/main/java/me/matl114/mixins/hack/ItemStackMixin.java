package me.matl114.mixins.hack;

import me.matl114.accessors.access.ItemStackAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStack.class)
@Environment(EnvType.CLIENT)
public abstract class ItemStackMixin implements ItemStackAccess {


    @Shadow @Final @Deprecated @Nullable private Item item;

//    @Override
    public void setItem(Item item) {
        //todo: do it later
    }

    @Override
    public Item getRealItem() {
        return this.item;
    }
}
