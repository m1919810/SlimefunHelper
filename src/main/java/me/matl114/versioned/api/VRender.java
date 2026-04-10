package me.matl114.versioned.api;

import java.awt.*;
import java.util.List;
import lombok.With;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.impl.Render_v1_21_11;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public interface VRender {
    public static final VRender INSTANCE = new Render_v1_21_11();

    public static VRender getInstance() {
        return INSTANCE;
    }

    // *********************************** layers *****************************
    @LimitOperation(layer = "Lines", format = "PositionColorNormalLineWidth")
    public void createLinesLayer(RenderCallback callback);

    @LimitOperation(layer = "LineStrip", format = "PositionColorNormalLineWidth")
    public void createLineStripLayer(RenderCallback callback);

    @LimitOperation(layer = "Quad", format = "PositionColor")
    public void createQuadsLayer(RenderCallback callback, boolean hasCulling);

    @LimitOperation(layer = "Rect", format = "PositionColor")
    public void createTrianglesLayer(RenderCallback callback, boolean hasCulling);

    @LimitOperation(layer = "Rect", format = "PositionColor")
    public void createTriangleStripLayer(RenderCallback callback, boolean hasCulling);

    @LimitOperation(layer = "TexturedGui", format = "PositionTextureColor")
    public void createGuiTexturedLayer(Identifier path, RenderCallback callback);

    @LimitOperation(layer = "TexturedGui", format = "PositionTextureColor")
    public void createSpriteTexturedLayer(Sprite sprite, RenderCallback callback);

    @LimitOperation(layer = "Gui", format = "PositionColor")
    public void createGuiLayer(RenderCallback callback);

    // ********************************** defaults **************************************

    default void drawStripLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> path, Color color) {
        createLinesLayer((op, bf) -> {
            op.drawLines(matrixStack, bf, path, color.getRGB());
        });
    }

    default void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> pairs, Color color) {
        createLinesLayer((op, bf) -> {
            int size = pairs.size();
            for (int i = 1; i < size; i += 2) {
                op.drawLine(matrixStack, bf, pairs.get(i - 1), pairs.get(i), color.getRGB());
            }
        });
    }

    default void drawOutlinedBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to, Color color) {
        createLinesLayer((op, bf) -> {
            op.drawOutlinedBox(matrix, bf, from, to, color.getRGB());
        });
    }

    default void drawSolidBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to, Color color) {
        createQuadsLayer(
                (op, bf) -> {
                    op.drawSolidBoxQuad(matrix, bf, from, to, color.getRGB());
                },
                true);
    }

    default void drawQuadCameraCoord(MatrixStack matrix4f, Quad quad, ColorQuad color) {
        createQuadsLayer(
                (op, bf) -> {
                    op.drawQuad(matrix4f, bf, quad, color);
                },
                false);
    }

    // 九宫格， -1 0 1  x +
    //      -1 0 1 2
    //      0  3 4 5
    //      1  6 7 8
    //      y +
    public static int createTextPositionFlag(int xAlign, int yAlign) {
        int flag0 = xAlign < 0 ? 0 : (xAlign > 0 ? 2 : 1);
        int flag1 = yAlign < 0 ? 0 : (yAlign > 0 ? 2 : 1);
        return 3 * flag1 + flag0;
    }

    /**
     * draw a texture in 3D,
     * looks normal when in Z+
     * @param path
     * @param stack
     * @param quad
     * @param uv
     * @param color
     */
    default void drawTexturedQuadCameraCoord(Identifier path, MatrixStack stack, Quad quad, UV uv, ColorQuad color) {
        createGuiTexturedLayer(path, (op, bf) -> {
            op.drawTexturedQuad(stack, bf, quad, uv, color);
        });
    }

    /**
     * draw a sprite texture in 3D
     * looks normal when in Z+
     * @param sprite
     * @param stack
     * @param quad
     * @param uv
     * @param color
     */
    default void drawSpriteQuadCameraCoord(Sprite sprite, MatrixStack stack, Quad quad, UV uv, ColorQuad color) {
        createSpriteTexturedLayer(sprite, (op, bf) -> {
            op.drawTexturedQuad(stack, bf, quad, uv, color);
        });
    }

    /**
     * draw a sprite texture in 3D
     * looks normal when in Z+
     * @param path
     * @param stack
     * @param quad
     * @param uv
     * @param color
     */
    default void drawGuiSpriteQuadCameraCoord(Identifier path, MatrixStack stack, Quad quad, UV uv, ColorQuad color) {
        SpriteAtlasTexture spriteAtlasTexture =
                MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Atlases.GUI);
        Sprite sprite = spriteAtlasTexture.getSprite(path);
        drawSpriteQuadCameraCoord(sprite, stack, quad, uv, color);
    }

    /**
     * draw a colored quad in 3D
     * using GUI Pipeline
     * @param stack
     * @param quad
     * @param color
     */
    default void drawGuiQuadCameraCoord(MatrixStack stack, Quad quad, ColorQuad color) {
        createGuiLayer((operation, vertexConsumer) -> {
            operation.drawQuad(stack, vertexConsumer, quad, color);
        });
    }
    // ************************** Specials **********************************

    /**
     * pass the coordinate of the "center"
     * draw a text related to it
     * the text should looks normal when in Z+
     * use the displayPositionFlag to control the relative position
     * @param orderedText
     * @param stack
     * @param center
     * @param displayPositionFlag
     * @param color
     * @param displayInfo
     */
    public void drawTextCameraCoord(
            OrderedText orderedText,
            MatrixStack stack,
            Vec3d center,
            int displayPositionFlag,
            Color color,
            TextDisplay displayInfo);

    public void drawItemCameraCoord(
            ItemStack itemStack, MatrixStack stack, Vec3d vec3d, ItemDisplayContext context, ItemDisplay displayInfo);

    @With
    public record TextDisplay(boolean shadow, TextRenderer.TextLayerType layerType, int backgroundColor, int light) {}

    public static TextDisplay DEFAULT_TEXT = new TextDisplay(false, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0);

    public record ItemDisplay(int light, int overlay, int outlineColor) {}

    public static ItemDisplay DEFAULT_ITEM = new ItemDisplay(0XFF00FF, OverlayTexture.DEFAULT_UV, 0);

    public static interface RenderCallback {
        public void draw(WrapRenderOperation operation, VertexConsumer vertexConsumer);
    }

    public static interface WrapRenderOperation {
        @LimitOperation(format = "PositionColorNormalLineWidth", layer = "Lines")
        public void drawOutlinedBox(
                MatrixStack matrix4f, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor);

        //        @LimitOperation(format = "PositionColorNormalLineWidth", layer = "LineStrip")
        //        public void drawOutlinedBoxStrip(
        //            MatrixStack matrix4f, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor);
        @LimitOperation(format = "PositionColor", layer = "Quad")
        public void drawSolidBoxQuad(
                MatrixStack matrixStack, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor);

        @LimitOperation(format = "PositionColor", layer = "Rect")
        public void drawSolidBoxTriangle(
                MatrixStack matrixStack, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor);

        @LimitOperation(format = "PositionColor")
        public void drawQuad(MatrixStack matrixStack, VertexConsumer bufferBuilder, Quad uv, ColorQuad color);

        @LimitOperation(format = "PositionColorNormalLineWidth")
        public void drawLines(MatrixStack matrixStack, VertexConsumer consumer, List<Vec3d> points, int color);

        @LimitOperation(format = "PositionColorNormalLineWidth")
        public void drawLine(MatrixStack matrixStack, VertexConsumer consumer, Vec3d prevV, Vec3d nextV, int color);

        @LimitOperation(format = "PositionTextureColor", layer = "TexturedGui")
        public void drawTexturedQuad(MatrixStack stack, VertexConsumer vertex, Quad quad, UV uv, ColorQuad colorQuad);
    }

    public @interface LimitOperation {
        String format() default "";

        String layer() default "";
    }
}
