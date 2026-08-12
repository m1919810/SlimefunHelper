package xaero.map.gui.dropdown.rightclick;

import net.minecraft.client.gui.screen.Screen;
import xaero.map.gui.IRightClickableElement;

public abstract class RightClickOption {
    protected String name;
    protected int index;
    protected boolean active;
    protected IRightClickableElement target;
    protected Object[] nameFormatArgs;

    public RightClickOption(String name, int index, IRightClickableElement target) {
        this.name = name;
        this.index = index;
        this.active = true;
        this.target = target;
        this.nameFormatArgs = new Object[0];
    }

    public abstract void onAction(Screen var1);

    public boolean onSelected(Screen screen) {
        return isActive();
    }

    protected String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return null;
    }

    public boolean isActive() {
        return this.active;
    }

    public RightClickOption setActive(boolean isActive) {
        this.active = isActive;
        return this;
    }

    public RightClickOption setNameFormatArgs(Object... nameFormatArgs) {
        this.nameFormatArgs = nameFormatArgs;
        return this;
    }
}
