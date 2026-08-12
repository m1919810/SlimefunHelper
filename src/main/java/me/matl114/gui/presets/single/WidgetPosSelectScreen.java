package me.matl114.gui.presets.single;

import java.util.function.Consumer;
import me.matl114.gui.GenericScreen;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.utils.collections.FPoint;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

public class WidgetPosSelectScreen extends GenericScreen {
    Consumer<FPoint> predicate;
    FPoint selected;

    public WidgetPosSelectScreen(int backgroundWidth, FPoint initializePoint, Consumer<FPoint> pointAcceptor) {
        super(
                Text.translatable("widget.gui.widget-select-screen.title").formatted(Formatting.GREEN),
                backgroundWidth,
                60);
        this.predicate = pointAcceptor;
        this.selected = initializePoint;
    }

    @Override
    protected void init() {
        super.init();
        DisplayWidget.instance(this.x, this.y, this.backgroundWidth, this.backgroundHeight)
                .setRenderHandler(new RawTextElement(this::getTitleLabel, -1, 0))
                .addTo(this);
        ;
        InputHandler selectHandler = WidgetUtils.createGridPosSelectInputHandler(selected::setX, selected::setY);

        ExecutableWidget.instance(0, 0, this.width, this.height)
                .setInputHandler(InputHandler.advanced(((element, mouseX, mouseY, button, type) -> {
                    if (button == 0) {
                        return selectHandler.onAction(element, mouseX, mouseY, button, type);
                    } else {
                        predicate.accept(selected);
                        this.close();
                        return true;
                    }
                })))
                .setRenderHandler(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                    context.fill(
                            (int) (selected.x - 5),
                            (int) (selected.y - 5),
                            (int) (selected.x + 5),
                            (int) (selected.y + 5),
                            Colors.RED);
                    context.fill(
                            (int) (selected.x - 5),
                            (int) (selected.y - 1),
                            (int) (selected.x + 5),
                            (int) (selected.y + 1),
                            Colors.GREEN);
                    context.fill(
                            (int) (selected.x - 1),
                            (int) (selected.y - 5),
                            (int) (selected.x + 1),
                            (int) (selected.y + 5),
                            Colors.GREEN);
                }))
                .addTo(this);

        //        DisplayWidget.instance(0,0, width, height)
        //            .setRenderHandler(
        //                (element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
        //
        //                }
        //            )
        //            .addTo(this);
    }
}
