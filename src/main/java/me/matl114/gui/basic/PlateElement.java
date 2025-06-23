package me.matl114.gui.basic;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class PlateElement extends AbstractElement implements ElementHandler {
    private static final int xTextureOffset = 0;
    private static final int yTextureOffset = 66;
    private static final Identifier texture = new Identifier("slimefunhelper", "textures/gui/recipecontainer.png");
    private int color = -1;//0xFFBB0000;
    public static PlateElement instance(){
        return new PlateElement();
    }
    @Override
    public void renderCentered0(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
        color = -1;
        float alpha1 = ((color >> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        RenderSystem.setShaderColor(red, green, blue, alpha1);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 0);
        RenderSystem.blendFunc(770, 771);
        int width = element.getTextureWidth();
        int height = element.getTextureHeight();
        context.drawTexture(texture, 0, 0, 106 + xTextureOffset, 124 + yTextureOffset, 8, 8);
        context.drawTexture(texture,  width - 8, 0, 248 + xTextureOffset, 124 + yTextureOffset, 8, 8);
        context.drawTexture(texture, 0,   height - 8, 106 + xTextureOffset, 182 + yTextureOffset, 8, 8);
        context.drawTexture(texture,  width - 8,  height - 8, 248 + xTextureOffset, 182 + yTextureOffset, 8, 8);

        // Sides
        context.drawTexturedQuad(texture,  8,  width - 8, 0,  8, 0, (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f, (124 + yTextureOffset) / 256f, (132 + yTextureOffset) / 256f);
        context.drawTexturedQuad(texture,  8,  width - 8,  height - 8,  height, 0, (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f, (182 + yTextureOffset) / 256f, (190 + yTextureOffset) / 256f);
        context.drawTexturedQuad(texture, 0,  8,  8,  height - 8, 0, (106 + xTextureOffset) / 256f, (114 + xTextureOffset) / 256f, (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
        context.drawTexturedQuad(texture, width - 8,  width, 8,  height - 8, 0, (248 + xTextureOffset) / 256f, (256 + xTextureOffset) / 256f, (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);

        // Center
        context.drawTexturedQuad(texture,  8, width - 8,  8,  height - 8, 0, (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f, (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return false;
    }
}
