package me.matl114.renderer.text;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class CometTextDrawer extends ResourceFontTextDrawer {
    public static final Identifier COMET_FONT_ID = Identifier.of("slimefunhelper", "comet");

    public CometTextDrawer() {
        this(MinecraftClient.getInstance().textRenderer);
    }

    public CometTextDrawer(net.minecraft.client.font.TextRenderer delegate) {
        super(delegate, COMET_FONT_ID);
    }
}
