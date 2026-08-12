package me.matl114.gui.presets.single;

import java.util.function.BooleanSupplier;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.choices.ConfirmingBigScreen;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;

public class ConfirmingWidgetScreen extends ConfirmingBigScreen {
    DrawableWidget widget;
    BooleanSupplier confirm;
    Runnable callback;

    public ConfirmingWidgetScreen(Text title, DrawableWidget widget, BooleanSupplier confirm, Runnable callback) {
        super(title);
        this.widget = widget;
        this.confirm = confirm;
        this.callback = callback;
    }

    protected int getCenteredX() {
        return ((this.width - this.widget.getWidth()) / 2) - this.widget.getX();
    }

    protected int getCenteredY() {
        return CONTENT_START_Y + ((content_end_y - CONTENT_START_Y - this.widget.getHeight()) / 2) - this.widget.getY();
    }

    @Override
    protected void init() {
        super.init();
        var sbscreen = new DynamicSubScreenWidget(
                ValueAccessor.ofIgnore(this::getCenteredX), ValueAccessor.ofIgnore(this::getCenteredY));
        sbscreen.addDrawableChild(widget);
        sbscreen.addTo(this);
    }

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return confirm != null && confirm.getAsBoolean();
    }

    @Override
    protected void onConfirmButton() {
        if (callback != null) {
            callback.run();
        }
        this.close();
    }
}
