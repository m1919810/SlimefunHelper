package me.matl114.gui.interfaces;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import me.matl114.hacks.utils.gui.CustomGuiConfig;
import me.matl114.hacks.utils.gui.GuiStyle;
import net.minecraft.util.Identifier;

/** Registry of GUI element styles. */
public final class ElementFactories {
    private static final Map<Identifier, ElementStyle> STYLES = new LinkedHashMap<>();

    static {
        register(new ElementStyle(
                setting -> new VanillaElementFactory(() -> setting.get().textConfig()),
                false,
                GuiStyle.VANILLA,
                CustomGuiConfig.DEFAULT));
        register(new ElementStyle(
                setting -> new CustomElementFactory(setting::get), true, GuiStyle.CUSTOM, CustomGuiConfig.DEFAULT));
    }

    private ElementFactories() {}

    public static ElementStyle register(ElementStyle style) {
        STYLES.put(style.id(), style);
        return style;
    }

    public static boolean unregister(ElementStyle style) {
        return STYLES.remove(style.id(), style);
    }

    public static ElementStyle get(Identifier id) {
        return STYLES.get(id);
    }

    public static ElementStyle getOrCustom(Identifier id) {
        ElementStyle style = get(id);
        return style == null ? STYLES.get(GuiStyle.CUSTOM) : style;
    }

    public static Collection<ElementStyle> values() {
        return STYLES.values();
    }

    public static java.util.List<Identifier> ids() {
        return java.util.List.copyOf(STYLES.keySet());
    }
}
