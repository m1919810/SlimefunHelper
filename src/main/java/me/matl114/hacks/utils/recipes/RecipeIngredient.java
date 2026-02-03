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

    public boolean testItemType(ItemStack itemStack){
        if (itemStack == null) {
            return false;
        } else if (this.isEmpty()) {
            return itemStack.isEmpty();
        } else {
            for (ItemStack itemStack2 : this.matchingStack()) {
                if (itemStack2.isOf(itemStack.getItem())) {
                    return true;
                }
            }

            return false;
        }
    }
}
