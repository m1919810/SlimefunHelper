package me.matl114.renderer;

import me.matl114.renderer.text.CustomFontTextRenderer;
import me.matl114.renderer.text.TextRenderer;
import me.matl114.renderer.text.VanillaTextRenderer;

public class TextRenderers {
    public static TextRenderer INSTANCE;

    public static TextRenderer get() {
        if (INSTANCE == null) {
            INSTANCE = new VanillaTextRenderer();
        }
        return INSTANCE;
    }

    public static TextRenderer useUnicodeFont() {
        INSTANCE = new CustomFontTextRenderer();
        return INSTANCE;
    }
}
