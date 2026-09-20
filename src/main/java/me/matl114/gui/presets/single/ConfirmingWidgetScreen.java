package me.matl114.gui.presets.single;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.choices.ConfirmingBigScreen;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;

public class ConfirmingWidgetScreen extends ConfirmingBigScreen {
    Function<ConfirmingWidgetScreen, DrawableWidget> widget;
    BooleanSupplier confirm;
    Runnable callback;
    DrawableWidget currentWidget = null;

    public ConfirmingWidgetScreen(
            Text title,
            Function<ConfirmingWidgetScreen, DrawableWidget> widget,
            BooleanSupplier confirm,
            Runnable callback) {
        super(title);
        this.widget = widget;
        this.confirm = confirm;
        this.callback = callback;
    }

    protected int getCenteredX() {
        return ((this.width - this.currentWidget.getWidth()) / 2) - this.currentWidget.getX();
    }

    protected int getCenteredY() {
        return CONTENT_START_Y
                + ((content_end_y - CONTENT_START_Y - this.currentWidget.getHeight()) / 2)
                - this.currentWidget.getY();
    }

    @Override
    protected void init() {
        super.init();
        currentWidget = widget.apply(this);
        var sbscreen = new DynamicSubScreenWidget(
                ValueAccessor.ofIgnore(this::getCenteredX), ValueAccessor.ofIgnore(this::getCenteredY));
        sbscreen.addDrawableChild(currentWidget);
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
