package me.matl114.gui.elements;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.util.Identifier;

public class PlateElement extends AbstractElement {
    private static final int xTextureOffset = 0;
    private static final int yTextureOffset = 66;
    public static final Identifier TEXTURE = new Identifier("slimefunhelper", "textures/custom/recipecontainer.png");
    private final ColorSampler color;
    private final boolean catchInteract;

    public static PlateElement instance() {
        return new PlateElement(false, null);
    }

    public static PlateElement instance(ColorSampler color) {
        return new PlateElement(false, color);
    }

    public static PlateElement catchInteract() {
        return new PlateElement(true, null);
    }

    public static PlateElement catchInteract(ColorSampler color) {
        return new PlateElement(true, color);
    }

    public PlateElement() {
        this(false, null);
    }

    public PlateElement(ColorSampler color) {
        this(false, color);
    }

    public PlateElement(boolean catchInteract) {
        this(catchInteract, null);
    }

    public PlateElement(boolean catchInteract, ColorSampler color) {
        this.catchInteract = catchInteract;
        this.color = color == null ? ColorSampler.of(-1) : color;
        showTooltips = false;
    }

    @Override
    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        int sampledColor = color.getColorInt();
        float alpha1 = ((sampledColor >> 24) & 0xFF) / 255f;
        float red = ((sampledColor >> 16) & 0xFF) / 255f;
        float green = ((sampledColor >> 8) & 0xFF) / 255f;
        float blue = (sampledColor & 0xFF) / 255f;
        context.setShaderColor(red, green, blue, alpha1);
        int width = element.getTextureWidth();
        int height = element.getTextureHeight();
        context.drawTexture(TEXTURE, 0, 0, 106 + xTextureOffset, 124 + yTextureOffset, 8, 8);
        context.drawTexture(TEXTURE, width - 8, 0, 248 + xTextureOffset, 124 + yTextureOffset, 8, 8);
        context.drawTexture(TEXTURE, 0, height - 8, 106 + xTextureOffset, 182 + yTextureOffset, 8, 8);
        context.drawTexture(TEXTURE, width - 8, height - 8, 248 + xTextureOffset, 182 + yTextureOffset, 8, 8);

        // Sides
        context.drawTexturedQuad(
                TEXTURE,
                8,
                width - 8,
                0,
                8,
                0,
                (114 + xTextureOffset) / 256f,
                (248 + xTextureOffset) / 256f,
                (124 + yTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f);
        context.drawTexturedQuad(
                TEXTURE,
                8,
                width - 8,
                height - 8,
                height,
                0,
                (114 + xTextureOffset) / 256f,
                (248 + xTextureOffset) / 256f,
                (182 + yTextureOffset) / 256f,
                (190 + yTextureOffset) / 256f);
        context.drawTexturedQuad(
                TEXTURE,
                0,
                8,
                8,
                height - 8,
                0,
                (106 + xTextureOffset) / 256f,
                (114 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f,
                (182 + yTextureOffset) / 256f);
        context.drawTexturedQuad(
                TEXTURE,
                width - 8,
                width,
                8,
                height - 8,
                0,
                (248 + xTextureOffset) / 256f,
                (256 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f,
                (182 + yTextureOffset) / 256f);

        // Center
        context.drawTexturedQuad(
                TEXTURE,
                8,
                width - 8,
                8,
                height - 8,
                0,
                (114 + xTextureOffset) / 256f,
                (248 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f,
                (182 + yTextureOffset) / 256f);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return catchInteract;
    }
}
