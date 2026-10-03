package me.matl114.hacks.utils.recipes;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public interface WrapRecipe extends RecipeEntry {
    ItemStack workType();

    void place(ScreenHandler screenHandler);

    default RecipeIngredient[] ingredients() {
        return ingredient();
    }
}
