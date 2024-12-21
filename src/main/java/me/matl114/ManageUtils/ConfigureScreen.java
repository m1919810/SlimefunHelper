package me.matl114.ManageUtils;

import me.matl114.Access.ButtonNotFocusedScreenAccess;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfigureScreen extends Screen implements ButtonNotFocusedScreenAccess {
    private Config config;
    private HashMap<String,Object> originValue;
    //private HashMap<String,Object> values;
    public ConfigureScreen(Config config,Text title) {
        super(title);
        loadConfig(config);
       // this.values=new HashMap<>(this.originValue);
    }
    public void loadConfig(Config config){
        this.config = config;
        this.originValue = new HashMap<>();
        config.getPaths().forEach(path -> {
            Debug.info((Object[]) Config.cutToPath(path));
            this.originValue.put(path,config.get(Config.cutToPath(path)));
        });
        Debug.info(this.originValue);
    }
    private static final int buttonWidth=200;
    private static final int buttonHeight=20;
    private HashMap<String, TextFieldWidget> textEntryBox = new HashMap<>();
    protected void init() {
        super.init();
        AtomicInteger y=new AtomicInteger(1);
        originValue.forEach((key, value) -> {
            int y0=buttonHeight*y.getAndIncrement();
            addDrawableChild(ButtonWidget
                    .builder(Text.literal(key), b ->saveEntryToValues())
                    .dimensions(10 ,y0 , buttonWidth, buttonHeight).build());
            //
            var textField = new TextFieldWidget(this.textRenderer, buttonWidth+30, y0, buttonWidth, buttonHeight, Text.of(""));

            // 设置一些属性
            textField.setMaxLength(100);  // 设置最大输入字符数
            textField.setEditable(true);  // 设置为可编辑
            textField.setText(Config.toString(value));  // 设置默认文本
            addDrawableChild(textField);
            textEntryBox.put(key,textField);
        });
    }
    protected void saveEntryToValues(){
        textEntryBox.forEach((key, value) -> {
            originValue.put(key,Config.saveFrom(value.getText()));
        });
    }
    public void resize(MinecraftClient client, int width, int height) {
        saveEntryToValues();
        super.resize(client,width,height);

    }
    public void close() {
        super.close();
        saveEntryToValues();
        //totol save
        for(String value: originValue.keySet()) {
            config.setValue(originValue.get(value),Config.cutToPath(value));
        }
        config.save();
    }
    @Unique
    public Element getDefaultElement(){
        return null;
    }
    @Unique
    public boolean doKeepButtonAfterClicked(){
        return false;
    }

}
