package me.matl114.gui.presets.single;

import me.matl114.gui.basic.*;
import me.matl114.managers.KeyCode;
import me.matl114.managers.MultiKeyBind;
import me.matl114.utils.UtilClass.AttrKeyValue;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class KeyBindConfigurateWidget extends SubScreenWidget {
    List<String> keyCodes;
    AttrKeyValue<MultiKeyBind> multiKeyBind;

    private static final List<Text> KEYCODE_CONFIGURE_TOOLTIPS = List.of(
        Text.literal("使用鼠标点击以选中该构件,在该构件被选中时:"),
        Text.literal("点击键盘以追加键"),
        Text.literal("使用鼠标点击以追加鼠标键"),
        Text.literal("点击右侧D以删除末尾键"),
        Text.literal("点击右侧R以撤销本次修改"),
        Text.literal("点击空白处或者其他构件以取消选中")
    );
    MultiKeyBind keyBind;
    MultiKeyBind resetKeyBind;
    public KeyBindConfigurateWidget(int x, int y, int dx, int dy, AttrKeyValue<MultiKeyBind> config, MultiKeyBind resetKeybind) {
        super(x, y, dx, dy);
        multiKeyBind = config;
        keyBind = multiKeyBind.getOriginValue();
        keyCodes = new ArrayList<>();
        keyCodes.addAll(Arrays.asList(keyBind.getKeys()));
        resetKeyBind = resetKeybind;
        init();
    }
    ExecutableWidget keyInputWidget;
    ExecutableWidget deleteKeyInputWidget;
    ExecutableWidget undoKeyInputWidget;
    ExecutableWidget resetKeyInputWidget;

    private void init(){
        keyInputWidget = ExecutableWidget.instance(0, 1, dx - 3* dy - 2, dy - 2)
            .setElementHandler(
                new ButtonElement(this::createKeyDisplay, ((element, widget, mouseButton) -> {
                    //select the widget for the first press, and set code for the second
                    if(this.isFocused() && widget == this.selected){
                        onAnyKeyPressed(KeyCode.getKeyCodeFromMouseAction(mouseButton));
                    }
                    return true;
                }))
                    .setHighLightColor(((widget, isFocused) -> {
                        if(multiKeyBind.isValidate()){
                            return isFocused ? Colors.WHITE : null;
                        }else{
                            return Colors.RED;
                        }
                    }))
                    .withInputHandler(
                        InputHandler.keyboard((widget, keyCode, scanCode, modifiers, isPress) -> {
                            if(this.isFocused() && widget == this.selected && isPress){
                                onAnyKeyPressed(keyCode);
                                return true;
                            }
                            return false;
                        })
                    )

                    .withTooltips(TooltipHandler.of(KEYCODE_CONFIGURE_TOOLTIPS))
            )
            .addToSub(this);
        deleteKeyInputWidget = ExecutableWidget.instance(dx - 3*  dy - 1, 1, dy - 2, dy - 2)
            .setElementHandler(
                new ButtonElement(
                    TextProvider.of(Text.literal("D")),
                    ((element, widget, mouseButton) -> {
                        clear();
                        //make it return false, do not unselect current
                        return false;
                    })
                )
            )
            .addToSub(this);
        undoKeyInputWidget = ExecutableWidget.instance(dx -2* dy -1, 1, dy - 2, dy - 2)
            .setElementHandler(
                new ButtonElement(
                    TextProvider.of(Text.literal("U")),
                    ((element, widget, mouseButton) -> {
                        undo();
                        //make it return false, do not unselect current
                        return false;
                    })
                )
            )
            .addToSub(this);
        resetKeyInputWidget = ExecutableWidget.instance(dx - dy -1, 1, dy - 2, dy - 2)
            .setElementHandler(
                new ButtonElement(
                    TextProvider.of(Text.literal("R")),
                    ((element, widget, mouseButton) -> {
                        reset();
                        //make it return false, do not unselect current
                        return false;
                    })
                )
            )
            .addToSub(this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        return isMouseOver(mouseX, mouseY);
    }

    private Text createKeyDisplay(DrawableWidget el) {
        String context = keyCodes.isEmpty() ? "None" : String.join(",", keyCodes);

        return (this.isFocused() && el == selected) ? Text.literal("> " + context + " <").formatted(Formatting.GOLD) :Text.literal(context);
    }
    private void onAnyKeyPressed(int keyCode) {
        String keyName = KeyCode.getNameForKey(keyCode);
        if(keyCodes.isEmpty() || !Objects.equals(keyCodes.get(keyCodes.size() - 1), keyName)) {
            keyCodes.add(keyName);
            ackChange();
        }
    }


    private void clear() {
        if (keyCodes.isEmpty()) {
            return;
        }
        keyCodes.remove(keyCodes.size() - 1);
        ackChange();
    }
    private void undo(){
        keyCodes = new ArrayList<>();
        keyCodes.addAll(Arrays.asList(keyBind.getKeys()));
        ackChange();
    }
    private void reset(){
        keyCodes = new ArrayList<>();
        keyCodes.addAll(Arrays.asList(resetKeyBind.getKeys()));
        ackChange();
    }

    private void ackChange(){
        multiKeyBind.valueChange(this, "hotkey:" + String.join(",", keyCodes));
    }
    public MultiKeyBind createKeybind(){
        return new MultiKeyBind(String.join(",", keyCodes));
    }



}
