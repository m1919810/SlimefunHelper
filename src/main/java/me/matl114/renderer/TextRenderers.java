package me.matl114.renderer;

import me.matl114.renderer.text.SupplierDelegatingTextDrawer;
import me.matl114.renderer.text.TextDrawer;
import me.matl114.renderer.text.VanillaTextDrawer;

public class TextRenderers {
    public static volatile TextDrawer INSTANCE = new VanillaTextDrawer();
    private static final SupplierDelegatingTextDrawer DELEGATE = new SupplierDelegatingTextDrawer(() -> INSTANCE);

    public static void setRenderer(TextDrawer renderer) {
        TextDrawer previous = INSTANCE;
        INSTANCE = renderer;
        if (previous != null && previous != renderer && previous instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static SupplierDelegatingTextDrawer get() {
        return DELEGATE;
    }

    public static float getCompositeTextYOffset() {
        float textHeight = get().getTextHeight();
        return textHeight == 9.0F ? 0.0F : 9.0F - textHeight / 2.0F;
    }

    public static TextDrawer resolve(TextDrawer renderer) {
        TextDrawer current = renderer;
        while (current instanceof SupplierDelegatingTextDrawer delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }
}
