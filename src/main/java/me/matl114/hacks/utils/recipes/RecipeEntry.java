package me.matl114.hacks.utils.recipes;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;

import java.util.Arrays;

public interface RecipeEntry{
    public String rid();
    public String id();
    public RecipeIngredient[] ingredient();
    public ItemStack output();

    public static final RecipeEntry EMPTY = new RecipeEntry() {
        @Override
        public String rid() {
            return "";
        }

        @Override
        public String id() {
            return "";
        }
        private static final RecipeIngredient[] EMPTY = new RecipeIngredient[9];
        static {
            Arrays.fill(EMPTY, RecipeIngredient.EMPTY);
        }
        @Override
        public RecipeIngredient[] ingredient() {
            return EMPTY.clone();
        }

        @Override
        public ItemStack output() {
            return ItemStack.EMPTY;
        }
    };
}