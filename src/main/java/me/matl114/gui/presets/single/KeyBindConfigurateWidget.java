package me.matl114.gui.presets.single;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

public class KeyBindConfigurateWidget extends SubScreenWidget {
    AttrKeyValue<MultiKeyBind> multiKeyBind;
    // todo: can not sync with attributeKeyValue
    MultiKeyBind keyBind;

    public KeyBindConfigurateWidget(int x, int y, int dx, int dy, AttrKeyValue<MultiKeyBind> config) {
        super(x, y, dx, dy);
        multiKeyBind = config;
        keyBind = multiKeyBind.getOriginValue();
        init();
    }

    ExecutableWidget keyInputWidget;
    ExecutableWidget deleteKeyInputWidget;

    private void init() {
        keyInputWidget = ExecutableWidget.instance(0, 1, dx - 3 * dy - 2, dy - 2)
                .setElementHandler(new ButtonElement(this::createKeyDisplay, ((element, widget, mouseButton) -> {
                            // select the widget for the first press, and set code for the second
                            if (this.isFocused() && widget == this.selected) {
                                onAnyKeyPressed(KeyCode.getKeyCodeFromMouseAction(mouseButton));
                            }
                            return true;
                        }))
                        .setHighLightColor(((widget, isFocused) -> {
                            if (multiKeyBind.isValidate()) {
                                return isFocused ? Colors.WHITE : null;
                            } else {
                                return Colors.RED;
                            }
                        }))
                        .withInputHandler(InputHandler.keyboard((widget, keyCode, scanCode, modifiers, isPress) -> {
                            if (this.isFocused() && widget == this.selected && isPress) {
                                onAnyKeyPressed(keyCode);
                                return true;
                            }
                            return false;
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.key-bind-configurate-widget.keycode-configure.tooltips", ""))))
                .addToSub(this);
        ExecutableWidget.instance(dx - 3 * dy - 1, 1, dy - 2, dy - 2)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.literal("T")), ((element, widget, mouseButton) -> {
                                    onSwitchToggleOnRelease();
                                    return false;
                                }))
                        .setActivePredicate((v) -> multiKeyBind.getOriginValue().isToggleOnRelease())
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.key-bind-configurate-widget.keycode-t.tooltips", ""))))
                .addToSub(this);
        ExecutableWidget.instance(dx - 2 * dy - 1, 1, dy - 2, dy - 2)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.literal("V")), ((element, widget, mouseButton) -> {
                                    onSwitchAllowVanilla();
                                    return false;
                                }))
                        .setActivePredicate((v) -> multiKeyBind.getOriginValue().isAllowVanilla())
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.key-bind-configurate-widget.keycode-v.tooltips", ""))))
                .addToSub(this);
        deleteKeyInputWidget = ExecutableWidget.instance(dx - dy - 1, 1, dy - 2, dy - 2)
                .setElementHandler(
                        new ButtonElement(TextProvider.of(Text.literal("D")), ((element, widget, mouseButton) -> {
                                    clear();
                                    // make it return false, do not unselect current
                                    return false;
                                }))
                                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.key-bind-configurate-widget.keycode-d.tooltips", ""))))
                .addToSub(this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        return isMouseOver(mouseX, mouseY);
    }

    private List<String> getKeys() {
        var re = multiKeyBind.getOriginValue().getKeys();
        return new ArrayList<>(Arrays.asList(re));
    }

    private Text createKeyDisplay(DrawableWidget el) {
        List<String> keyCodes = getKeys();
        String context = keyCodes.isEmpty() ? "None" : String.join(",", keyCodes);

        return (this.isFocused() && el == selected)
                ? Text.literal("> " + context + " <").formatted(Formatting.GOLD)
                : Text.literal(context);
    }

    private void onAnyKeyPressed(int keyCode) {
        String keyName = KeyCode.getNameForKey(keyCode);
        List<String> keyCodes = getKeys();
        if (keyCodes.isEmpty() || !Objects.equals(keyCodes.get(keyCodes.size() - 1), keyName)) {
            keyCodes.add(keyName);
            ackChange(
                    keyCodes,
                    multiKeyBind.getOriginValue().isToggleOnRelease(),
                    multiKeyBind.getOriginValue().isAllowVanilla());
        }
    }

    private void onSwitchToggleOnRelease() {
        var multi = multiKeyBind.getOriginValue();
        multiKeyBind.valueChangeInternal(this, multi.withToggleOnRelease(!multi.isToggleOnRelease()));
    }

    private void onSwitchAllowVanilla() {
        var multi = multiKeyBind.getOriginValue();
        multiKeyBind.valueChangeInternal(this, multi.withAllowVanilla(!multi.isAllowVanilla()));
    }

    private void clear() {
        List<String> keyCodes = getKeys();
        if (keyCodes.isEmpty()) {
            return;
        }
        keyCodes.remove(keyCodes.size() - 1);
        ackChange(
                keyCodes,
                multiKeyBind.getOriginValue().isToggleOnRelease(),
                multiKeyBind.getOriginValue().isAllowVanilla());
    }

    private void undo() {
        List<String> keyCodes;
        keyCodes = new ArrayList<>();
        keyCodes.addAll(Arrays.asList(keyBind.getKeys()));
        ackChange(keyCodes, keyBind.isToggleOnRelease(), keyBind.isAllowVanilla());
    }

    private void ackChange(List<String> keyCodes, boolean toggleOnBindRelease, boolean vanilla) {
        multiKeyBind.valueChangeInternal(this, new MultiKeyBind(keyCodes, toggleOnBindRelease, vanilla));
    }
}
