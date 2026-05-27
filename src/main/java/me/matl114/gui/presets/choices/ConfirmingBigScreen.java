package me.matl114.gui.presets.choices;

import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public abstract class ConfirmingBigScreen extends GenericBackGroundScreen {
    protected ConfirmingBigScreen(Text title) {
        super(title, 480, 360);
    }

    protected ExecutableWidget cancelButtonWidget;
    protected ExecutableWidget confirmButtonWidget;
    protected static int CONTENT_START_Y = 40;
    protected static int CONTENT_START_X = 20;
    protected int content_end_y;

    protected void onCloseButton() {
        this.close();
    }

    protected abstract boolean canConfirm(ElementHandler elementHandler);

    protected abstract void onConfirmButton();

    private static final Text CANCEL = Text.literal("取消").formatted(Formatting.RED);
    private static final Text CONFIRM = Text.literal("确认").formatted(Formatting.GREEN);

    @Override
    protected void init() {
        super.init();
        this.content_end_y = this.backgroundHeight - 30;
        // 按钮大小 80, 20
        // 放在240 - 90 = 150
        this.cancelButtonWidget = ExecutableWidget.instance(this.x + 150, this.y + this.content_end_y + 5, 80, 20)
                .setElementHandler(new ButtonElement(TextProvider.of(CANCEL), ButtonAction.run(this::onCloseButton)))
                .addTo(this);
        this.confirmButtonWidget = ExecutableWidget.instance(this.x + 250, this.y + this.content_end_y + 5, 80, 20)
                .setElementHandler(new ButtonElement(TextProvider.of(CONFIRM), ButtonAction.run(this::onConfirmButton))
                        .withActiveActionCondition(this::canConfirm))
                .addTo(this);
    }
}
