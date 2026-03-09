package me.matl114.utils.inventory;

import java.util.Arrays;
import me.matl114.hacks.utils.recipes.RecipeIngredient;
import me.matl114.managers.Tasks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

public class MyIngredientImmutableInventory implements Inventory {
    public MyIngredientImmutableInventory(RecipeIngredient[] val) {
        this.ingredients = val;
    }

    RecipeIngredient[] ingredients;

    @Override
    public int size() {
        return ingredients.length;
    }

    @Override
    public boolean isEmpty() {
        return Arrays.stream(ingredients).allMatch(RecipeIngredient::isEmpty);
    }

    public ItemStack getCurrentItemStack(RecipeIngredient ingredient) {
        ItemStack[] itemStacks = ingredient.matchingStack();
        return itemStacks.length == 0
                ? ItemStack.EMPTY
                : itemStacks[MathHelper.floor(Tasks.getTick() / 30.0F) % itemStacks.length];
    }

    @Override
    public ItemStack getStack(int slot) {
        return getCurrentItemStack(ingredients[slot]);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {}

    @Override
    public void markDirty() {}

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {}
}
