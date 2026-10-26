package me.matl114.versioned.impl;

import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.ColoredQuadGuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;

/** Replays the GUI element states used by NameTag on its world-facing plane. */
public final class DrawContext3d_v1_21_11 extends DrawContext {
    private static final int FULL_BRIGHT = 15728880;
    private final MatrixStack worldMatrices;

    public DrawContext3d_v1_21_11(MatrixStack matrices) {
        super(MinecraftClient.getInstance(), new GuiRenderState(), 0, 0);
        this.worldMatrices = matrices;
        MinecraftClient client = MinecraftClient.getInstance();
        getMatrices()
                .translate(
                        client.getWindow().getScaledWidth() / 2.0F,
                        client.getWindow().getScaledHeight() / 2.0F);
    }

    public void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = worldMatrices;
        matrices.push();
        try {
            matrices.translate(
                    -client.getWindow().getScaledWidth() / 2.0F,
                    -client.getWindow().getScaledHeight() / 2.0F,
                    0);
            // Items are behind their durability bars and count text. NameTag's sprites do not overlap items.
            GuiRenderState state = this.state;
            state.forEachItemElement(item -> renderItem(item, matrices));
            Render_v1_21_11.getVCP().draw();
            state.forEachSimpleElement(simple -> renderSimple(simple, matrices), GuiRenderState.LayerFilter.ALL);
            state.forEachTextElement(text -> renderText(text, matrices));
        } finally {
            matrices.pop();
        }
    }

    private static void renderItem(ItemGuiElementRenderState item, MatrixStack matrices) {
        matrices.push();
        try {
            matrices.multiplyPositionMatrix(toMatrix4f(item.pose()));
            matrices.translate(item.x() + 8, item.y() + 8, 0);
            // Keep vanilla GUI display transforms while flattening the item onto the name tag.
            matrices.scale(16, -16, 0.016F);
            item.state().render(matrices, Render_v1_21_11.QUEUE, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0);
            var vertices = Render_v1_21_11.getVCP();
            var outlineVertices = Render_v1_21_11.getOutlineVCP();
            for (var entry : Render_v1_21_11.QUEUE.getBatchingQueues().values()) {
                Render_v1_21_11.ITEM_RENDERER.render(entry, vertices, outlineVertices);
                entry.clear();
            }
        } finally {
            matrices.pop();
        }
    }

    private static void renderSimple(SimpleGuiElementRenderState simple, MatrixStack matrices) {
        if (simple instanceof TexturedQuadGuiElementRenderState texture) {
            matrices.push();
            try {
                matrices.translate(0, 0, 0.05F);
                matrices.multiplyPositionMatrix(toMatrix4f(texture.pose()));
                VRender.getInstance()
                        .drawTexturedQuadCameraCoord(
                                TexturedRenderLayers.GUI_ATLAS_TEXTURE,
                                matrices,
                                quad(texture.x1(), texture.y1(), texture.x2(), texture.y2()),
                                new UV(texture.u1(), texture.v1(), texture.u2(), texture.v2()),
                                ColorQuad.of(texture.color()));
            } finally {
                matrices.pop();
            }
        } else if (simple instanceof ColoredQuadGuiElementRenderState color) {
            matrices.push();
            try {
                matrices.translate(0, 0, 0.05F);
                matrices.multiplyPositionMatrix(toMatrix4f(color.pose()));
                VRender.getInstance()
                        .drawGuiQuadCameraCoord(
                                matrices,
                                quad(color.x0(), color.y0(), color.x1(), color.y1()),
                                ColorQuad.ofYGradient(color.col1(), color.col2()));
            } finally {
                matrices.pop();
            }
        }
    }

    private static void renderText(TextGuiElementRenderState text, MatrixStack matrices) {
        matrices.push();
        try {
            matrices.translate(0, 0, 0.06F);
            matrices.multiplyPositionMatrix(toMatrix4f(text.matrix));
            text.textRenderer.draw(
                    text.orderedText,
                    text.x,
                    text.y,
                    text.color,
                    text.shadow,
                    matrices.peek().getPositionMatrix(),
                    Render_v1_21_11.getVCP(),
                    TextLayerType.SEE_THROUGH,
                    text.backgroundColor,
                    FULL_BRIGHT);
            Render_v1_21_11.getVCP().draw();
        } finally {
            matrices.pop();
        }
    }

    private static Quad quad(int x1, int y1, int x2, int y2) {
        return new Quad(new Vec3d(x1, y1, 0), new Vec3d(x1, y2, 0), new Vec3d(x2, y2, 0), new Vec3d(x2, y1, 0));
    }

    private static Matrix4f toMatrix4f(Matrix3x2fc matrix) {
        return new Matrix4f()
                .m00(matrix.m00())
                .m01(matrix.m01())
                .m10(matrix.m10())
                .m11(matrix.m11())
                .m30(matrix.m20())
                .m31(matrix.m21());
    }
}
