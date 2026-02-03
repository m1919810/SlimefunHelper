package me.matl114.versioned.api;

import me.matl114.versioned.impl.ItemUtils_v1_21_1;
import net.minecraft.item.ItemStack;

public interface VItem {
    public static final VItem INSTANCE = new ItemUtils_v1_21_1();
    public static VItem getInstance(){
        return INSTANCE;
    }
    public boolean canGlide(ItemStack stack);

    public boolean isSpear(ItemStack stack);

    public boolean isWeapon(ItemStack stack);

    public boolean isTool(ItemStack stack);
}
