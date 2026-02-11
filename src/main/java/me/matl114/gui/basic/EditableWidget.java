package me.matl114.gui.basic;

import lombok.Getter;

public class EditableWidget extends DrawableWidget {
    public EditableWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }

    TextHandler handler;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Getter
    boolean editable = false;

    private boolean canEdit() {
        return editable && (this.handler != null && this.handler.isMutable());
    }

    public void setFocused(boolean focused) {
        super.setFocused(focused);
        editable = focused;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isEditable()) {
            return false;
        }
        return false;
    }
}
