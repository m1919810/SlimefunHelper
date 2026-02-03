package me.matl114.hacks.utils.recipes;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;

public interface RecipeEntry{
    public String rid();
    public String id();
    public Ingredient[] ingredient();
    public ItemStack output();
}