package me.matl114.hacks.utils.recipes;

import net.minecraft.item.ItemStack;

public record RecipeIngredient(ItemStack[] matchingStack) {
    public RecipeIngredient(ItemStack s){
        this(new ItemStack[]{s});
    }
    public static RecipeIngredient EMPTY = new RecipeIngredient(new ItemStack[0]);

    public boolean isEmpty(){
        return matchingStack.length == 0;
    }
}
