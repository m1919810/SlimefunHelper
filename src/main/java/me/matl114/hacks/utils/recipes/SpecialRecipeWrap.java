package me.matl114.hacks.utils.recipes;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public record SpecialRecipeWrap(
        String rid,
        String id,
        ItemStack workType,
        ItemStack output,
        RecipeIngredient[] ingredient,
        Consumer<ScreenHandler> placer)
        implements WrapRecipe {
    public SpecialRecipeWrap {
        Objects.requireNonNull(rid);
        Objects.requireNonNull(id);
        Objects.requireNonNull(workType);
        Objects.requireNonNull(output);
        Objects.requireNonNull(ingredient);
        Objects.requireNonNull(placer);
    }

    @Override
    public void place(ScreenHandler screenHandler) {
        placer.accept(screenHandler);
    }
}
