package me.matl114.events.impl;

import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.ButtonWidget;

public record RecipeBookToggle(
        RecipeBookProvider provider, RecipeBookWidget recipeBookWidget, ButtonWidget toggleWidget) {}
