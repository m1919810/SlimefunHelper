package me.matl114.gui.config;

import me.matl114.gui.GenericScreen;
import me.matl114.managers.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ConfigureScreen extends GenericScreen {
    private Config config;
    //    private HashMap<String,Object> originValue;
    // private HashMap<String,Object> values;
    @Deprecated
    public ConfigureScreen(Config config, Text title) {
        super(title, 400, 320);
        loadConfig(config);
        // this.values=new HashMap<>(this.originValue);
    }

    public void loadConfig(Config config) {
        this.config = config;
        //        this.config = config;
        //        this.originValue = new LinkedHashMap<>();
        //        config.getPaths().forEach(path -> {
        ////            Debug.info((Object[]) Config.cutToPath(path));
        //            this.originValue.put(path,config.get(Config.cutToPath(path)));
        //        });
        ////        Debug.info(this.originValue);
    }

    private static final int buttonWidth = 160;
    private static final int buttonHeight = 20;
    ConfigureListWidget configs;
    //    private HashMap<String, TextFieldWidget> textEntryBox = new HashMap<>();
    protected void init() {
        super.init();
        if (configs != null) {
            configs.saveSelected();
            Config.launchSaveTasks();
        }
        configs = ConfigureListWidget.createConfigConfigure(
                this.config,
                0,
                10,
                buttonWidth,
                buttonWidth,
                20,
                buttonWidth,
                buttonHeight,
                this.width - 20,
                this.height - 40);
        addDrawableChild(configs);
        //        List<DrawableWidget> drawableWidgets = originValue.entrySet().stream().map((entry)->{
        //            var subscreen = new SubScreenWidget(0, 0, 2 *buttonWidth + 20, buttonHeight)
        //                .addDrawableChild(
        //                    ExecutableWidget.instance(0, 0, buttonWidth, buttonHeight)
        //                            .setElementHandler(new
        // ButtonElement(TextProvider.of(Text.literal(entry.getKey())), ButtonAction.run(this::saveEntryToValues)))
        ////
        ////                    ButtonWidget
        ////                    .builder(Text.literal(entry.getKey()), b ->saveEntryToValues())
        ////                    .dimensions(10 , 0 , buttonWidth, buttonHeight).build()
        //                );
        //            var textField = new TextFieldWidget(this.textRenderer, buttonWidth + 20, 0, buttonWidth,
        // buttonHeight, Text.empty());
        //
        //            // 设置一些属性
        //            textField.setMaxLength(1000);  // 设置最大输入字符数
        //            textField.setEditable(true);  // 设置为可编辑
        //            textField.setText(Config.getSaveFormat(entry.getValue()));  // 设置默认文本
        ////            addDrawableChild(textField);
        //            textEntryBox.put(entry.getKey(),textField);
        //            subscreen.addDrawableChild(new McWidgetHelpers.TextContentDelegateWidget<>(0,0, textField));
        //            return (DrawableWidget)subscreen;
        //        }).toList();
        //        addDrawableChild(new ListUnmodifiableWidget(ListEntryWidgetController.immutable(drawableWidgets,
        // Function.identity(), buttonHeight, 20 + 2 * buttonWidth), 10, 10, this.width - 40, this.height - 20));
        //  addDrawableChild()
        //        originValue.forEach((key, value) -> {
        //            int y0=buttonHeight*y.getAndIncrement();
        //            addDrawableChild(ButtonWidget
        //                    .builder(Text.literal(key), b ->saveEntryToValues())
        //                    .dimensions(10 ,y0 , buttonWidth, buttonHeight).build());
        //            //
        //
        //        });
    }
    //    public void saveEntryToValues(){
    //        textEntryBox.forEach((key, value) -> {
    //            Object originval = originValue.get(key);
    //            originValue.put(key,Config.saveFrom(value.getText(), originval));
    //        });
    //    }
    public void resize(MinecraftClient client, int width, int height) {
        saveEntryToValues();
        super.resize(client, width, height);
    }

    public void close() {
        super.close();
        saveEntryToValues();
        // totol save

        //        for(String value: originValue.keySet()) {
        //            config.setValueNoNew(originValue.get(value),Config.cutToPath(value));
        //        }
        //        config.save();
    }

    public void saveEntryToValues() {
        if (configs != null) {
            configs.saveSelected();
        }
        Config.launchSaveTasks();
    }
}
