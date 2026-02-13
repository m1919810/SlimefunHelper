package me.matl114.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.utils.ResourceUtils;
import net.minecraft.util.Identifier;

public class GuiMain {
    public static void init() {}

    private static final List<Identifier> CUSTOM_GUI_SPRITE = new ArrayList<>();
    private static final List<Identifier> CUSTOM_TEXTURES = new ArrayList<>();

    public static void onAtlasLoadGuiElements(Event<Set<Identifier>> loadEvent) {
        if (new Identifier("minecraft", "gui").equals(loadEvent.getArgs(1))) {
            var customGuis = ResourceUtils.lookupResources(
                    loadEvent.getArgs(0),
                    "slimefunhelper",
                    "slimefunhelper",
                    "textures",
                    ".png",
                    s -> s.startsWith("gui"));
            loadEvent.context().addAll(customGuis);
            CUSTOM_GUI_SPRITE.clear();
            CUSTOM_GUI_SPRITE.addAll(customGuis);
            var customTextures = ResourceUtils.lookupResources(
                    loadEvent.getArgs(0),
                    "slimefunhelper",
                    "slimefunhelper",
                    "textures",
                    ".png",
                    s -> s.startsWith("custom"));
            CUSTOM_TEXTURES.clear();
            customTextures.stream()
                    .map(s -> new Identifier(s.getNamespace(), "textures/" + s.getPath() + ".png"))
                    .forEach(CUSTOM_TEXTURES::add);
        }
    }

    public static List<Identifier> getCustomGuiSprites() {
        return Collections.unmodifiableList(CUSTOM_GUI_SPRITE);
    }

    public static List<Identifier> getCustomTextures() {
        return Collections.unmodifiableList(CUSTOM_TEXTURES);
    }

    static {
        RenderListener.getAtlasSourceSupply().registerHandler(GuiMain::onAtlasLoadGuiElements);
    }
}
