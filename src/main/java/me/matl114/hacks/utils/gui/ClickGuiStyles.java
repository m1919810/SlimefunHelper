package me.matl114.hacks.utils.gui;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.hacks.modules.task.ClickGui;
import net.minecraft.util.Identifier;

/** Registry of ClickGui layout styles. */
public final class ClickGuiStyles {
    public static final Identifier SUB_SCREEN = Identifier.of("slimefunhelper", "sub_screen");
    public static final Identifier SLIDER = Identifier.of("slimefunhelper", "slider");

    private static final Map<Identifier, ClickGuiStyle> STYLES = new LinkedHashMap<>();

    static {
        register(new ClickGuiStyle(SUB_SCREEN, ClickGui::openSubScreenStyle));
        register(new ClickGuiStyle(SLIDER, ClickGui::openSliderStyle));
    }

    private ClickGuiStyles() {}

    public static ClickGuiStyle register(ClickGuiStyle style) {
        STYLES.put(style.id(), style);
        return style;
    }

    public static boolean unregister(ClickGuiStyle style) {
        return STYLES.remove(style.id(), style);
    }

    public static ClickGuiStyle get(Identifier id) {
        return STYLES.get(id);
    }

    public static ClickGuiStyle getOrDefault(String configValue) {
        ClickGuiStyle style = STYLES.values().stream()
                .filter(candidate -> candidate.id().toString().equals(configValue)
                        || legacyConfigKey(candidate).equals(configValue))
                .findFirst()
                .orElse(null);
        return style == null ? STYLES.get(SUB_SCREEN) : style;
    }

    public static String configValue(Identifier id) {
        return id.toString();
    }

    private static String legacyConfigKey(ClickGuiStyle style) {
        return "configenum.click-gui-style." + style.id().getPath();
    }

    public static Collection<ClickGuiStyle> values() {
        return List.copyOf(STYLES.values());
    }

    public static List<String> configValues() {
        return STYLES.values().stream().map(style -> configValue(style.id())).toList();
    }
}
