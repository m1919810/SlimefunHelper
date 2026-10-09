package me.matl114.renderer.text;

import me.matl114.plugins.MainPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class CustomFontTextDrawer extends ResourceFontTextDrawer {
    public static final Identifier UNICODE_FONT_ID = MainPlugin.identifier("unicode");

    public CustomFontTextDrawer() {
        this(MinecraftClient.getInstance().textRenderer);
    }

    public CustomFontTextDrawer(net.minecraft.client.font.TextRenderer delegate) {
        super(delegate, UNICODE_FONT_ID);
    }
}
