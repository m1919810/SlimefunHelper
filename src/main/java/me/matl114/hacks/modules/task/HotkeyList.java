package me.matl114.hacks.modules.task;

import java.util.function.Consumer;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.HackModules;
import me.matl114.managers.Configs;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;

public class HotkeyList extends BaseModule {
    public final ModulePath hotkeys = makePath(Configs.MISC_CONFIG, "hotkeys");
    public static HotkeyList INSTANCE;
    public final KeyBindRef filter =
            hotkey(hotkeys.add("filter")).defaultValue(new MultiKeyBind()).build();

    public HotkeyList() {
        super("Hotkey");
        INSTANCE = this;
    }

    @Override
    public void addCustomWidgets(
            Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank, ElementFactory factory) {
        super.addCustomWidgets(acceptor, dx, dy, dblank, factory);
        acceptor.accept(factory.createTitle("widget.hotkey-manager.list", 0, dblank, dx, dy));
        var layout = WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT.forWidth(dx);
        int width = layout.totalWidth();
        int rowHeight = layout.buttonHeight() + layout.buttonBlank();
        var keyBinds = HackModules.getModuleGroups().stream()
                .flatMap(group -> group.getModules().stream())
                .flatMap(module -> module.getEditableConfig().stream())
                .filter(config -> config.ref() instanceof KeyBindRef && config.ref() != filter)
                .toList();

        for (WrapperConfigRef<?> config : keyBinds) {
            KeyBindRef keyBind = (KeyBindRef) config.ref();
            SubScreenWidget row = new SubScreenWidget(0, 0, width, rowHeight);
            row.addDrawableChild(DisplayWidget.instance(0, 0, width, rowHeight));
            row.addDrawableChild(WidgetUtils.createKeyValueWidget(keyBind, config.keyName(), layout, CONFIG_FACTORY));
            acceptor.accept(new DynamicContentWidget<>(
                    () -> config.shouldShow() && matchesFilter(keyBind.get()) ? row : null, 0, 0));
        }
    }

    private boolean matchesFilter(MultiKeyBind keyBind) {
        int[] filterKeyCodes = filter.get().getKeyCodes();
        for (int filterKeyCode : filterKeyCodes) {
            boolean match = false;
            for (int keyCode : keyBind.getKeyCodes()) {
                if (keyCode == filterKeyCode) {
                    match = true;
                }
            }
            if (!match) return false;
        }

        return true;
    }
}
