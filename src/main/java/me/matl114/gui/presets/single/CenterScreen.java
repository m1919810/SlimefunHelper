package me.matl114.gui.presets.single;

import me.matl114.gui.GenericScreen;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicSubScreenWidget;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;

public class CenterScreen extends GenericScreen {
    DrawableWidget widget;

    public CenterScreen(DrawableWidget widget) {
        super(Text.empty(), 0, 0);
        this.widget = widget;
    }

    @Override
    protected void init0() {
        super.init0();
        this.x = 0;
        this.y = 0;
    }

    protected int getCenteredX() {
        return ((this.width - this.widget.getWidth()) / 2) - this.widget.getX();
    }

    int overrideY = 0;

    protected int getCenteredY() {
        int y = ((this.height - this.widget.getHeight()) / 2) - this.widget.getY();
        if (y < 0) {
            yLock = false;
            return overrideY;
        } else {
            yLock = true;
            overrideY = y;
            return y;
        }
    }

    boolean yLock = false;

    protected void setCenteredY(int y) {
        if (!yLock) {
            overrideY = Math.min(y, 0);
        }
    }

    @Override
    protected void init() {
        super.init();
        DynamicSubScreenWidget dynamic = new DynamicSubScreenWidget(
                ValueAccessor.ofIgnore(this::getCenteredX), ValueAccessor.of(this::getCenteredY, this::setCenteredY));
        dynamic.addDrawableChild(widget);
        dynamic.addTo(this);
    }
}
