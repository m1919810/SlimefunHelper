package me.matl114.versioned.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/** Draws GUI elements directly on the supplied world-facing matrix stack. */
public final class DrawContext3d_v1_21_4 extends DrawContext {
    public DrawContext3d_v1_21_4(MatrixStack matrices) {
        super(MinecraftClient.getInstance(), Render_v1_21_4.getVCP());
        getMatrices().multiplyPositionMatrix(matrices.peek().getPositionMatrix());
    }

    public void render() {
        draw();
        Render_v1_21_4.getVCP().draw();
        Render_v1_21_4.getOutlineVCP().draw();
    }
}
