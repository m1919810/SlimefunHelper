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

    public void drawStripLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> path, Color color);

    public void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> pairs, Color color);

    public void drawOutlinedBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to);

    public void drawSolidBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to);

    public void drawQuadCameraCoord(MatrixStack matrix4f, Quad quad, ColorQuad color);

    public void setAsShaderColor(Color color, float opacity);

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
    public void drawTexturedQuadCameraCoord(Identifier path, MatrixStack stack, Quad quad, UV uv, ColorQuad color);

    /**
     * draw a sprite texture in 3D
     * looks normal when in Z+
     * @param sprite
     * @param stack
     * @param quad
     * @param uv
     * @param color
     */
    public void drawSpriteQuadCameraCoord(Sprite sprite, MatrixStack stack, Quad quad, UV uv, ColorQuad color);

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
    public void drawGuiQuadCameraCoord(MatrixStack stack, Quad quad, ColorQuad color);

    public void drawItemCameraCoord(
            ItemStack itemStack, MatrixStack stack, Vec3d vec3d, ItemDisplayContext context, ItemDisplay displayInfo);

    @With
    public record TextDisplay(boolean shadow, TextRenderer.TextLayerType layerType, int backgroundColor, int light) {}

    public static TextDisplay DEFAULT_TEXT = new TextDisplay(false, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0);

    public record ItemDisplay(int light, int overlay, int outlineColor) {}

    public static ItemDisplay DEFAULT_ITEM = new ItemDisplay(0XFF00FF, OverlayTexture.DEFAULT_UV, 0);
}
