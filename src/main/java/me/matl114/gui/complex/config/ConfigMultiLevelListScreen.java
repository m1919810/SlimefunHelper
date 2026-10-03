package me.matl114.gui.complex.config;

import java.util.List;
import me.matl114.events.Listener;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.interfaces.DefaultElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.presets.index.IndexedScreen;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.StringRef;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ConfigMultiLevelListScreen extends IndexedScreen<Config, ConfigMultiLevelList> {
    private static final int CONFIG_BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    private final ElementFactory factory;
    private StringRef filterWidget;
    private Config selectingConfig;

    public ConfigMultiLevelListScreen(List<Config> list) {
        this(list, DefaultElementFactory.INSTANCE);
    }

    public ConfigMultiLevelListScreen(List<Config> list, ElementFactory factory) {
        super(list, 400, 320);
        this.factory = factory;
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
        return factory.createExecuteButton(
                () -> Text.literal(config.getConfigName()),
                List::of,
                ButtonAction.run(() -> setGlobal(config)),
                () -> getGlobal() == config);
    }

    @Override
    protected ConfigMultiLevelList createSelectingDisplayWidget(Config config) {
        if (filterWidget == null) {
            filterWidget = new StringRef("");
        }
        return new ConfigMultiLevelList(
                new ModulePath(config, new String[0]),
                WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT,
                factory);
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
