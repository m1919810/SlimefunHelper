package me.matl114.gui.config;

import me.matl114.gui.basic.*;
import me.matl114.gui.presets.index.IndexedScreen;
import me.matl114.hackUtils.Tasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

public class ConfigurateNewStyleScreen extends IndexedScreen<Config, ConfigureListWidget> {
    public ConfigurateNewStyleScreen(List<Config> list) {
        super(list ,400, 320);
    }
    private static Config selectingConfig;
    private static final int configButtonWidth = 100;
    private static final int indexWidth = 140;
    private static final int buttonWidth = 220;
    private static final int buttonHeight = 20;
    @Override
    public void setGlobal(Config config) {
        if(config != selectingConfig){
            selectingConfig = config;
            onIndexChange();
            //Tasks.scheduleDelayed(this::onIndexChange, 1);
        }
    }

    @Override
    public Config getGlobal() {
        return selectingConfig;
    }

    @Override
    protected ElementHandler createIndexHandler(Config val) {
        return new ButtonElement(TextProvider.of(Text.literal(val.getConfigName())), ButtonAction.run(()->{
            this.setGlobal(val);
        }))
            .setInactiveId(ButtonElement.BUTTON)
            .setActiveId(ButtonElement.BUTTON_HIGHLIGHT)
            .setActivePredicate((el)-> getGlobal() == val);
    }

    @Override
    protected ConfigureListWidget createSelectingDisplayWidget(Config val) {
        return ConfigureListWidget.createConfigConfigure(
            val,
             20, 0, configButtonWidth, indexWidth, 0, buttonWidth, buttonHeight,this.width - configButtonWidth - 30, this.height - 20
        );
    }

    //TODO ：should we make first-level index
    //TODO : add tooltips with translation


    //    @Override
    public void saveSelected() {
        if(subScreenDelegate != null){
            var config = this.subScreenDelegate.getDisplaying();
            if(config != null){
                config.saveSelected();
            }
        }
    }
    static{
        Listener.getHotKeyTriggeredListener().registerHandler(iHotKeyEvent -> {
            //do not use any hotkeys in configure screen because we may use keyBindConfigurate
            if(MinecraftClient.getInstance().currentScreen instanceof ConfigurateNewStyleScreen){
                iHotKeyEvent.cancel();
            }
        });
    }
}
