package me.matl114.gui.complex;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.utils.ScreenUtils;

public class BoxElement extends AbstractElement {
    private final ButtonAction action;

    public BoxElement(ButtonAction action) {
        super();
        this.action = action;
    }

    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return action != null && action.onClick(this, element, button);
    }

    @Override
    public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
        // add shift-press-trigger-press feature
        if (this.action != null && widget.isSelected()) {
            if (ScreenUtils.isToggle(keyCode)) {
                return this.action.onClick(this, widget, 0);
            }
        }
        return super.onKey(widget, keyCode, scanCode, modifiers, isPress);
    }
}
