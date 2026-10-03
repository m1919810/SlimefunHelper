package me.matl114.hacks.utils.recipes;

import me.matl114.hacks.RecipeTasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;

public record NetworkRecipeWrap(
        RecipeEntry<?> entry, RecipeType<?> type, ItemStack workType, ItemStack output, RecipeIngredient[] ingredient)
        implements WrapRecipe {
    public static NetworkRecipeWrap of(
            RecipeEntry<?> entry, RecipeType<?> type, RegistryWrapper.WrapperLookup registryLookup) {
        var recipe = entry.value();
        return new NetworkRecipeWrap(
                entry,
                type,
                RecipeTasks.getVanillaRecipeTypeIcon(
                        Registries.RECIPE_TYPE.getId(type).toString()),
                recipe.getResult(registryLookup),
                RecipeTasks.transfer3x3RecipeDisplay(
                        recipe,
                        recipe.getIngredients().stream()
                                .map(ingredient -> new RecipeIngredient(ingredient.getMatchingStacks()))
                                .toArray(RecipeIngredient[]::new)));
    }

    @Override
    public String rid() {
        return Registries.RECIPE_TYPE.getId(type).toString();
    }

    @Override
    public String id() {
        return entry.id().toString();
    }

    @Override
    public void place(ScreenHandler screenHandler) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.clickRecipe(screenHandler.syncId, entry, true);
        }
    }
}
