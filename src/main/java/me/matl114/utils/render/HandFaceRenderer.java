package me.matl114.utils.render;

import java.util.List;
import me.matl114.versioned.api.ModelFaceFactory;
import me.matl114.versioned.impl.HandFaceRenderer_v1_21_11;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Compatibility facade for callers that still use the pre-versioned renderer location.
 *
 * <p>Version-specific code lives in {@code versioned.impl}.
 */
@Deprecated(forRemoval = false)
public final class HandFaceRenderer {
    private static final HandFaceRenderer_v1_21_11 DELEGATE = ModelFaceFactory.getInstance();

    private HandFaceRenderer() {}

    public static void begin() {
        DELEGATE.begin();
    }

    public static void submitArm(ModelPart part, MatrixStack matrices, Identifier texture, int light) {
        DELEGATE.submitArm(part, matrices, texture, light);
    }

    public static void submitItem(MatrixStack matrices, List<BakedQuad> quads, RenderLayer layer, int color) {
        DELEGATE.submitItem(matrices, quads, layer, color);
    }

    public static void render(int color) {
        DELEGATE.render(color);
    }

    public static void renderDeferred(OrderedRenderCommandQueue queue, int color) {
        DELEGATE.renderDeferred(queue, color);
    }

    public static void clear() {
        DELEGATE.clear();
    }
}
