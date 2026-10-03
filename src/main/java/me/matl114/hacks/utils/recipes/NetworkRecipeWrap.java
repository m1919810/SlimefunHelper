package me.matl114.hacks.utils.recipes;

import me.matl114.hacks.RecipeTasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;

public record NetworkRecipeWrap(
        RecipeDisplayEntry entry, ItemStack workType, ItemStack output, RecipeIngredient[] ingredient)
        implements WrapRecipe {
    public static NetworkRecipeWrap of(RecipeDisplayEntry entry, ContextParameterMap context) {
        return new NetworkRecipeWrap(
                entry,
                entry.display().craftingStation().getFirst(context),
                entry.display().result().getFirst(context),
                RecipeTasks.transfer3x3RecipeDisplay(entry.display(), context));
    }

    @Override
    public String rid() {
        return Registries.ITEM.getId(workType.getItem()).toString();
    }

    @Override
    public String id() {
        return Identifier.ofVanilla(String.valueOf(entry.id().index())).toString();
    }

    @Override
    public void place(ScreenHandler screenHandler) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.clickRecipe(screenHandler.syncId, entry.id(), true);
        }
    }
}
