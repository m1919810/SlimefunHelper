package me.matl114.hacks.utils.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.modules.task.ClickGui;
import me.matl114.hacks.modules.task.ClickGui.ClickGuiMetaData;
import me.matl114.hooks.BaritoneHooks;
import net.minecraft.client.gui.screen.Screen;

/** Shared screen-opening behavior for the classic ClickGui layouts. */
public abstract class AbstractClassicClickGuiFactory<F extends ElementFactory> implements ClickGuiFactory {
    protected static final String SEARCH_MODULE = "Search";

    protected ClickGui clickGui;
    protected final F elementFactory;

    protected AbstractClassicClickGuiFactory(F elementFactory) {
        this.elementFactory = elementFactory;
    }

    protected abstract DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta);

    @Override
    public void openClickGui(ClickGui clickGui, ClickGuiMetaData meta) {
        this.clickGui = clickGui;
        List<String> modules = clickGui.getModules();
        Map<String, Function<Screen, DrawableWidget>> selections = new LinkedHashMap<>();
        selections.put("Module", screen -> createModuleGroupList(modules, meta));
        selections.put("Friends", screen -> clickGui.createFriendSettings(screen, meta));
        selections.put("CmdMacros", screen -> clickGui.createCmdMacrosSettings(screen, meta));
        selections.put("Hotkeys", screen -> clickGui.createKeyBindListSettings(screen, meta));
        selections.put("BaseSettings", screen -> clickGui.createBaseSettings(screen, meta));
        selections.put("GuiSettings", screen -> clickGui.createGuiSettings(screen, meta));
        selections.put("Font", screen -> clickGui.createFontSettings(screen, meta));
        selections.put("Config", screen -> clickGui.createConfig(meta));
        if (BaritoneHooks.getInstance().isBaritoneAPISupported()) {
            selections.put("Baritone", screen -> clickGui.createBaritoneScreen(screen, meta));
        }

        Screen screen = new ClassicClickGuiScreen(selections, elementFactory);
        ScreenAccess.of(screen).addCloseFuture(() -> clickGui.setClickGuiMeta(meta));
        ScreenAccess.of(screen).openFromCurrent();
    }
}
