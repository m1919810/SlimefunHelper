package me.matl114.gui.presets.single;

import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.basic.*;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

@Accessors(chain = true)
public class IntFastInputWidget extends SubScreenWidget {
    final AttrKeyValue<Integer> keyValue;
    final Consumer<AttrKeyValue<Integer>> callback;
    ContentDelegateWidget<TextFieldWidget> inputField;
    final int inputDx;
    int inputTextDx;
    @Setter
    Runnable finishRunning;
    public IntFastInputWidget(AttrKeyValue<Integer> keyValue, Consumer<AttrKeyValue<Integer>> callback, int x, int y, int dx, int dy, int dx0) {
        super(x, y, dx, dy);
        this.keyValue = keyValue;
        this.callback = callback;
        inputDx = dx0;
        inputTextDx = inputDx;
        init();
    }

    public static IntFastInputWidget instance(AttrKeyValue<Integer> keyValue, Consumer<AttrKeyValue<Integer>> callback, int x, int y, int dx, int dy, int dx0){
        if(keyValue instanceof AttrKeyValue.ClampedIntAttrKeyValue clamped && clamped.getMin() <= 1){
            return new ClampedIntFastInputWidget(keyValue, callback, clamped.getMax(), x, y, dx, dy, dx0);
        }
        return new IntFastInputWidget(keyValue, callback, x, y, dx, dy, dx0);
    }
    public static class ClampedIntFastInputWidget extends IntFastInputWidget{
        int maxValue;
        public ClampedIntFastInputWidget(AttrKeyValue<Integer> keyValue, Consumer<AttrKeyValue<Integer>> callback, int maxValue, int x, int y, int dx, int dy, int dx0) {
            super(keyValue, callback, x, y, dx, dy, dx0);
            this.maxValue = maxValue;
        }

        @Override
        protected void init() {
            this.inputTextDx = inputDx/2 + 12;
            super.init();
        }

        @Override
        protected void initBackgroundAndText() {
            super.initBackgroundAndText();
            DisplayWidget.instance(inputTextDx  - 2, 12, inputDx - inputTextDx, dy - 16)
                .setRenderHandler(
                    RawTextElement.instance((i)-> Text.literal("/" + this.maxValue))
                        .setAlignment(-1)
                )
                .addToSub(this);
        }
    }

    protected void addValue(int val){
        Integer int0 = keyValue.getOriginValue();
        int val0 =  int0 == null? val : int0 + val;
        if(keyValue instanceof AttrKeyValue.ClampedIntAttrKeyValue clamp){
            val0 = clamp.clampInput(val0);
        }
        if(inputField.getDelegate() != null){
            //trigger update internal and text update
            inputField.getDelegate().setText(String.valueOf(val0));
        }


    }
    private static final Text A1 = Text.literal("+1");
    private static final Text A16 = Text.literal("+16");
    private static final Text A64 = Text.literal("+64");
    private static final Text N1 = Text.literal("-1");
    private static final Text N16 = Text.literal("-16");
    private static final Text N64 = Text.literal("-64");
    private static final Text CONFIRM = Text.literal("提交");
    protected static Identifier CANCEL_GUI_TEXTURE = new Identifier("minecraft","container/beacon/cancel");
    protected static final List<Text> CANCEL_GUI_TOOLTIPS = List.of(Text.literal("点击关闭当前子界面"));
    protected static final List<Text> SHIFT_CANCEL_TOOLTIPS = List.of(
        Text.literal("点击此按钮,"),
        Text.literal("或者按下回车键"),
        Text.literal("以提交输入框的数字")
    );


    protected void initFastButtons(){
        int maxWidth = dx /3;
        ExecutableWidget.instance(-6, - 14, maxWidth + 4, 12 )
            .setElementHandler(
                new ButtonElement(TextProvider.of(A1), ButtonAction.run(()->addValue(1)))
            )
            .addToSub(this);

        ExecutableWidget.instance(maxWidth - 2, - 14, maxWidth + 4, 12 )
            .setElementHandler(
                new ButtonElement(TextProvider.of(A16), ButtonAction.run(()->addValue(16)))
            )
            .addToSub(this);

        ExecutableWidget.instance(dx - maxWidth + 2 , - 14, maxWidth + 4, 12 )
            .setElementHandler(
                new ButtonElement(TextProvider.of(A64), ButtonAction.run(()->addValue(64)))
            )
            .addToSub(this);

        ExecutableWidget.instance(-6, dy + 2, maxWidth + 4, 12 )
            .setElementHandler(
                new ButtonElement(TextProvider.of(N1), ButtonAction.run(()->addValue(-1)))
            )
            .addToSub(this);

        ExecutableWidget.instance(maxWidth - 2, dy +2, maxWidth + 4, 12 )
            .setElementHandler(
                new ButtonElement(TextProvider.of(N16), ButtonAction.run(()->addValue(-16)))
            )
            .addToSub(this);

        ExecutableWidget.instance(dx - maxWidth + 2, dy + 2, maxWidth + 4, 12 )
            .setElementHandler(
                new ButtonElement(TextProvider.of(N64), ButtonAction.run(()->addValue(-64)))
            )
            .addToSub(this);
    }
    protected void initBackgroundAndText(){

        DisplayWidget.instance(0,0, inputDx, dy)
            .setRenderHandler(PlateElement.instance())
            .addToSub(this);
        DisplayWidget.instance(4, 1, inputDx - 8, 10)
            .setRenderHandler(
                RawTextElement.instance(Text.literal(keyValue.getKeyName()))
                    .setAlignment(-1)
            )
            .addToSub(this);
        inputField = keyValue.generateTextField(4, 12, inputTextDx - 8, dy - 16)
            .addToSub(this);
    }

    protected void initConfirmButton(){
        DisplayWidget.instance( inputDx - 2, 3, dx - inputDx + 2, dy - 6 )
            .setRenderHandler(PlateElement.instance())
            .addToSub(this);
        ExecutableWidget.instance(inputDx + 2, 7, dx - inputDx- 6, dy - 14)
            .setElementHandler(
                new ButtonElement(TextProvider.of(CONFIRM), ButtonAction.run(this::callback))
                    .withTooltips(TooltipHandler.of(SHIFT_CANCEL_TOOLTIPS))
                    .withInputHandler(
                        InputHandler.keyPress(((el, keycode) -> {
                            if (keycode == GLFW.GLFW_KEY_ENTER){
                                callback();
                                return true;
                            }
                            return false;
                        }))
                    )
            )
            .addToSub(this);
    }

    protected void initPlateBackground(){
        ExecutableWidget.instance(dx  + 6, -30, 24,24)
            .setElementHandler(
                IconElement.fixedGui(CANCEL_GUI_TEXTURE, ButtonAction.run(this::finishRunning))
                    .withTooltips(TooltipHandler.of(CANCEL_GUI_TOOLTIPS))
                    .withPresentCondition((i)->finishRunning != null)
            )
            .addToSub(this);
        ExecutableWidget.instance( -30, -30, dx + 60, dy + 60)
            .setElementHandler(
                PlateElement.catchInteract()
                    .withInputHandler(
                        InputHandler.scroller((el, am)->{
                            addValue(am > 0 ? -1: 1);
                            return true;
                        })
                    )
            )
            .addToSub(this, -1);

    }
    protected void callback(){
        if(this.callback != null)
            this.callback.accept(this.keyValue);
        finishRunning();
    }
    protected void finishRunning(){
        if(this.finishRunning != null)
            this.finishRunning.run();
    }

    protected void init(){
        setBasicDepth(1);
        initConfirmButton();
        initBackgroundAndText();
        initFastButtons();
        initPlateBackground();
    }
}
