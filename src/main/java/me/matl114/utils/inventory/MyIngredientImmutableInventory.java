package me.matl114.utils.inventory;

import me.matl114.hacks.Tasks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;

public class MyIngredientImmutableInventory implements Inventory {
    public MyIngredientImmutableInventory(Ingredient[] val){
        this.ingredients = val;
    }
    Ingredient[] ingredients;
    @Override
    public int size() {
        return ingredients.length;
    }

    @Override
    public boolean isEmpty() {
        return Arrays.stream(ingredients).allMatch(Ingredient::isEmpty);
    }
    public ItemStack getCurrentItemStack(Ingredient ingredient) {
        ItemStack[] itemStacks = ingredient.getMatchingStacks();
        return itemStacks.length == 0 ? ItemStack.EMPTY : itemStacks[MathHelper.floor(Tasks.getTick() / 30.0F) % itemStacks.length];
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
    public void setStack(int slot, ItemStack stack) {

    }

    @Override
    public void markDirty() {

    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {
    }
}