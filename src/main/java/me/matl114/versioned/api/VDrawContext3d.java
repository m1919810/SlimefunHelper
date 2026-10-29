package me.matl114.versioned.api;

import me.matl114.versioned.impl.DrawContext3d_v1_21_4;
import net.minecraft.client.util.math.MatrixStack;

/** Collects GUI elements in a DrawContext and renders them on a world-facing plane. */
public interface VDrawContext3d {
    static DrawContext3d_v1_21_4 createDrawContext(MatrixStack matrices) {
        return new DrawContext3d_v1_21_4(matrices);
    }
}
