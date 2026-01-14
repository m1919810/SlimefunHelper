package me.matl114.gui.config;

import me.matl114.gui.GenericScreen;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ButtonElement;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.hackUtils.Tasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

public class ConfigurateNewStyleScreen extends GenericScreen {
    private final List<Config> configList;
    public ConfigurateNewStyleScreen(List<Config> list) {
        super(Text.empty(), 400, 320);
        this.configList = list;
    }
    private ListUnmodifiableWidget selectList;
    private ConfigureListWidget selectingConfigEdit;
    private static Config selectingConfig;
    private static final int configButtonWidth = 100;
    private static final int indexWidth = 140;
    private static final int buttonWidth = 220;
    private static final int buttonHeight = 20;
    private void onConfigChange(){
        resize(this.client, this.width, this.height);
    }
    public void setConfig(Config config){
        if(config != selectingConfig){
            selectingConfig = config;
            Tasks.scheduleDelayed(this::onConfigChange, 1);
        }
    }
    //TODO ：should we make first-level index
    //TODO : add tooltips with translation
    @Override
    protected void init() {
        super.init();
        this.selectList = new ListUnmodifiableWidget(
            ListEntryWidgetController.immutable(
                this.configList,
                (config)->{
                    return new ExecutableWidget(0, 0, configButtonWidth, buttonHeight)
                        .setElementHandler(new ButtonElement(TextProvider.of(Text.literal(config.getConfigName())), ButtonAction.run(()->{
                            this.setConfig(config);
                        }))
                            .setInactiveId(ButtonElement.BUTTON)
                            .setActiveId(ButtonElement.BUTTON_HIGHLIGHT)
                            .setActivePredicate((el)-> selectingConfig == config)
                        );
                },
                buttonHeight,
                configButtonWidth
            ),
            10, 10, configButtonWidth +4, this.height - 40
        );
        addDrawableChild(this.selectList);
        if(selectingConfigEdit != null){
            selectingConfigEdit.save();
        }
        if(selectingConfig != null){
            selectingConfigEdit = ConfigureListWidget.createConfigConfigure(
                selectingConfig,
                configButtonWidth + 30, 10, configButtonWidth, indexWidth, 0, buttonWidth, buttonHeight,this.width - configButtonWidth - 60, this.height -40
            );
            addDrawableChild(this.selectingConfigEdit);
        }else{
            selectingConfigEdit = null;
        }
    }
    public void resize(MinecraftClient client, int width, int height) {
        saveEntryToValues();
        super.resize(client,width,height);

    }
    public void close() {
        super.close();
        saveEntryToValues();
        //totol save

//        for(String value: originValue.keySet()) {
//            config.setValueNoNew(originValue.get(value),Config.cutToPath(value));
//        }
//        config.save();
    }
//    @Override
    public void saveEntryToValues() {
        if(selectingConfigEdit != null){
            selectingConfigEdit.save();
        }
        Config.launchSaveTasks();
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
