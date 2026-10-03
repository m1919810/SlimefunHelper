package me.matl114.gui.presets.single;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

public class KeyBindConfigurateWidget extends SubScreenWidget {
    AttrKeyValue<MultiKeyBind> multiKeyBind;
    // todo: can not sync with attributeKeyValue
    MultiKeyBind keyBind;
    ElementFactory factory;
    public KeyBindConfigurateWidget(ElementFactory factory, int x, int y, int dx, int dy, AttrKeyValue<MultiKeyBind> config) {
        super(x, y, dx, dy);
        this.factory = factory;
        multiKeyBind = config;
        keyBind = multiKeyBind.get();
        init();
    }

    ExecutableWidget keyInputWidget;
    ExecutableWidget deleteKeyInputWidget;

    private void init() {
        ExecutableWidget.instance(0, 1, dx - 3 * dy - 2, dy - 2)
            .setElementHandler(
                new AbstractElement()
                    .withElement(factory.createExecuteButton(
                        (TextProvider) this::createKeyDisplay,
                        TooltipHandler.of( ChatUtils.parseTooltipsTranslation(
                            "widget.gui.key-bind-configurate-widget.keycode-configure.tooltips", "")),
                        (ButtonAction) ((element, widget, mouseButton) -> {
                            // select the widget for the first press, and set code for the second
                            if (this.isFocused() && widget == this.selected) {
                                onAnyKeyPressed(KeyCode.getKeyCodeFromMouseAction(mouseButton));
                            }
                            return true;
                        }),
                        ()-> true
                    ))
                    .withInputHandler(
                        InputHandler.keyboard((widget, keyCode, scanCode, modifiers, isPress) -> {
                            if (this.isFocused() && widget == this.selected && isPress) {
                                onAnyKeyPressed(keyCode);
                                return true;
                            }
                            return false;
                        })
                    )
                )
            .addToSub(this);
        factory.createToggleButton(
            TextProvider.of(Text.literal("T")),
            TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                "widget.gui.key-bind-configurate-widget.keycode-t.tooltips", "")),
                ValueAccessor.of(()-> multiKeyBind.get().isToggleOnRelease(), (v) -> onSwitchToggleOnRelease()),
            true,
            dx - 3 * dy - 1, 1, dy - 2, dy - 2
        ).addToSub(this);
        factory.createToggleButton(
            TextProvider.of(Text.literal("V")),
            TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                "widget.gui.key-bind-configurate-widget.keycode-v.tooltips", "")),
            ValueAccessor.of(()-> multiKeyBind.get().isAllowVanilla(), (v) -> onSwitchAllowVanilla()),
            true,
            dx - 2 * dy - 1, 1, dy - 2, dy - 2
        ).addToSub(this);
        factory.createExecuteButton(
            TextProvider.of(Text.literal("D")),
            TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                "widget.gui.key-bind-configurate-widget.keycode-d.tooltips", "")),
            ((element, widget, mouseButton) -> {
                clear();
                // make it return false, do not unselect current
                return false;
            }),
            ()-> true,
            dx - dy - 1, 1, dy - 2, dy - 2
        ).addToSub(this);

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        return isMouseOver(mouseX, mouseY);
    }

    private List<String> getKeys() {
        var re = multiKeyBind.get().getKeys();
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
                    multiKeyBind.get().isToggleOnRelease(),
                    multiKeyBind.get().isAllowVanilla());
        }
    }

    private void onSwitchToggleOnRelease() {
        var multi = multiKeyBind.get();
        multiKeyBind.accept(multi.withToggleOnRelease(!multi.isToggleOnRelease()));
    }

    private void onSwitchAllowVanilla() {
        var multi = multiKeyBind.get();
        multiKeyBind.accept(multi.withAllowVanilla(!multi.isAllowVanilla()));
    }

    private void clear() {
        List<String> keyCodes = getKeys();
        if (keyCodes.isEmpty()) {
            return;
        }
        keyCodes.remove(keyCodes.size() - 1);
        ackChange(
                keyCodes,
                multiKeyBind.get().isToggleOnRelease(),
                multiKeyBind.get().isAllowVanilla());
    }

    private void undo() {
        List<String> keyCodes;
        keyCodes = new ArrayList<>();
        keyCodes.addAll(Arrays.asList(keyBind.getKeys()));
        ackChange(keyCodes, keyBind.isToggleOnRelease(), keyBind.isAllowVanilla());
    }

    private void ackChange(List<String> keyCodes, boolean toggleOnBindRelease, boolean vanilla) {
        multiKeyBind.accept(new MultiKeyBind(keyCodes, toggleOnBindRelease, vanilla));
    }
}
