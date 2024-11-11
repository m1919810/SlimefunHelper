package me.matl114.Access;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

public interface DrawContextAccess {
    MinecraftClient getMinecraftClient();
    MatrixStack getMatrixStack();
    static DrawContextAccess of(DrawContext drawContext) {
        return (DrawContextAccess) drawContext;
    }
}
