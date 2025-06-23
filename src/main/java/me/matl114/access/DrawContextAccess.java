package me.matl114.access;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public interface DrawContextAccess {
    MinecraftClient getMinecraftClient();
    MatrixStack getMatrixStack();
    VertexConsumerProvider.Immediate getVertexConsumers();
    static DrawContextAccess of(DrawContext drawContext) {
        return (DrawContextAccess) drawContext;
    }
}
