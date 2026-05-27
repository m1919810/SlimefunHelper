package me.matl114.gui.complex.config;

import java.util.List;
import me.matl114.events.Listener;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.presets.index.IndexedScreen;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.StringRef;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ConfigurateNewStyleScreen extends IndexedScreen<Config, ConfigureListWidget> {
    public ConfigurateNewStyleScreen(List<Config> list) {
        super(list, 400, 320);
    }

    private static Config selectingConfig;
    private static final int configButtonWidth = 100;
    private static final int indexWidth = 140;
    private static final int buttonWidth = 220;
    private static final int buttonHeight = 20;

    @Override
    public void setGlobal(Config config) {
        if (config != selectingConfig) {
            selectingConfig = config;
            onIndexChange();
            // Tasks.scheduleDelayed(this::onIndexChange, 1);
        }
    }

    @Override
    public Config getGlobal() {
        return selectingConfig;
    }

    @Override
    protected ElementHandler createIndexHandler(Config val) {
        return new ButtonElement(TextProvider.of(Text.literal(val.getConfigName())), ButtonAction.run(() -> {
                    this.setGlobal(val);
                }))
                .setInactiveId(ButtonElement.BUTTON)
                .setActiveId(ButtonElement.BUTTON_HIGHLIGHT)
                .setActivePredicate((el) -> getGlobal() == val);
    }

    private StringRef filterWidget;

    @Override
    protected ConfigureListWidget createSelectingDisplayWidget(Config val) {
        if (filterWidget == null) {
            filterWidget = new StringRef("");
        }
        return ConfigureListWidget.createConfigConfigure(
                val,
                20,
                0,
                configButtonWidth,
                indexWidth,
                0,
                buttonWidth,
                buttonHeight,
                this.width - configButtonWidth - 30,
                this.height - 20,
                filterWidget);
    }

    protected void onIndexChange() {
        super.onIndexChange();
        this.filterWidget = new StringRef("");
    }

    //    @Override
    public void saveSelected() {
        if (subScreenDelegate != null) {
            var config = this.subScreenDelegate.getDisplaying();
            if (config != null) {
                config.saveSelected();
            }
        }
    }

    static {
        Listener.getHotKeyTriggeredListener().registerHandler(iHotKeyEvent -> {
            // do not use any hotkeys in configure screen because we may use keyBindConfigurate
            if (MinecraftClient.getInstance().currentScreen instanceof ConfigurateNewStyleScreen) {
                iHotKeyEvent.cancel();
            }
        });
    }
}
