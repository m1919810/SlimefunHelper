package me.matl114.gui.complex.config;

import java.util.List;
import me.matl114.events.Listener;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.*;
import me.matl114.gui.interfaces.DefaultElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.presets.index.IndexedScreen;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.StringRef;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ConfigMultiLevelListScreen extends IndexedScreen<Config, DrawableWidget> {
    private static final int CONFIG_BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int CONFIG_INDENT_WIDTH = 20;

    private final ElementFactory factory;
    private StringRef filterWidget;
    private Config selectingConfig;

    public ConfigMultiLevelListScreen(List<Config> list) {
        this(list, DefaultElementFactory.INSTANCE);
    }

    public ConfigMultiLevelListScreen(List<Config> list, ElementFactory factory) {
        super(list, 400, 320);
        this.factory = factory;
        this.configButtonWidth = CONFIG_BUTTON_WIDTH;
        this.buttonHeight = BUTTON_HEIGHT;
    }

    @Override
    public void setGlobal(Config config) {
        if (config != selectingConfig) {
            selectingConfig = config;
            onIndexChange();
        }
    }

    @Override
    public Config getGlobal() {
        return selectingConfig;
    }

    @Override
    protected ElementHandler createIndexHandler(Config config) {
        return factory.createButton(
                TextProvider.of(Text.translatable(config.getTranslationKey())),
                TooltipHandler.of(List.of()),
                ButtonAction.run(() -> setGlobal(config)),
                () -> getGlobal() == config,
                () -> getGlobal() == config);
    }

    @Override
    protected DrawableWidget createSelectingDisplayWidget(Config config) {
        if (filterWidget == null) {
            filterWidget = new StringRef("");
        }
        int panelWidth = Math.max(1, this.width - 20 - configButtonWidth - 16);
        int contentWidth = Math.max(1, panelWidth - SCROLLBAR_WIDTH);
        ScrollableListWidget scrollable = new ScrollableListWidget(0, 0, contentWidth, Math.max(1, this.height - 20));
        scrollable.addScrollingWidget(new ConfigMultiLevelList(
                new ModulePath(config, new String[0]),
                WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT.forWidth(contentWidth),
                factory,
                false,
                CONFIG_INDENT_WIDTH));
        return scrollable;
    }

    @Override
    protected void onIndexChange() {
        super.onIndexChange();
        filterWidget = new StringRef("");
    }

    @Override
    public void saveSelected() {}

    static {
        Listener.getHotKeyTriggeredListener().registerHandler(iHotKeyEvent -> {
            if (MinecraftClient.getInstance().currentScreen instanceof ConfigMultiLevelListScreen) {
                iHotKeyEvent.cancel();
            }
        });
    }
}
