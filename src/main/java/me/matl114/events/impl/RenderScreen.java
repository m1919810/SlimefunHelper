package me.matl114.events.impl;

import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.screen.Screen;

public record RenderScreen(
        VDrawContext context, Screen screen, Stage stage, double mouseX, double mouseY, float tickDelta) {

    public enum Stage {
        PRE,
        MID,
        POST
    }
}
