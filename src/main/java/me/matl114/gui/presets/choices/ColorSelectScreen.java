package me.matl114.gui.presets.choices;

import java.awt.*;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public class ColorSelectScreen extends ConfirmingBigScreen {
    ValueAccessor<TextColor> source;

    protected ColorSelectScreen(ValueAccessor<TextColor> color) {
        super(Text.translatable("widget.gui.color-select-screen.title").formatted(Formatting.GREEN));
        this.source = color;
        TextColor color1 = color.getValue();
        Color color2 = new Color(color1.getRgb(), true);
        this.rValue = color2.getRed();
        this.gValue = color2.getGreen();
        this.bValue = color2.getBlue();
    }

    int rValue;
    int gValue;
    int bValue;

    @Override
    protected void init() {
        super.init();
        SubScreenWidget subScreenWidget =
                new SubScreenWidget(this.x, this.y, this.backgroundWidth, this.backgroundHeight);
        new ContentDelegateWidget<>(30, 30, 0, 0)
                .setContentDelegate(ExecutableWidget.instance(0, 0, 255, 255)
                        .setInputHandler(
                                WidgetUtils.createGridPosSelectInputHandler((v) -> rValue = v, (v) -> gValue = v))
                        .setRenderHandler(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.fillGuiGradient(
                                    0,
                                    0,
                                    element.getTextureWidth(),
                                    element.getTextureHeight(),
                                    ColorUtils.getColorInt(0, 0, bValue),
                                    ColorUtils.getColorInt(0, 255, bValue),
                                    ColorUtils.getColorInt(255, 255, bValue),
                                    ColorUtils.getColorInt(255, 0, bValue),
                                    0);
                            context.fill(rValue - 1, gValue - 1, rValue + 1, gValue + 1, -1);
                        })))
                .addToSub(subScreenWidget);
        new ContentDelegateWidget<>(30, 300, 0, 0)
                .setContentDelegate(ExecutableWidget.instance(0, 0, 255, 20)
                        .setInputHandler(WidgetUtils.createGridPosSelectInputHandler((vl) -> bValue = vl, (vl) -> {}))
                        .setRenderHandler(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.fillGuiGradient(
                                    0,
                                    0,
                                    element.getTextureWidth(),
                                    element.getTextureHeight(),
                                    ColorUtils.getColorInt(0, 0, 0),
                                    ColorUtils.getColorInt(0, 0, 0),
                                    ColorUtils.getColorInt(0, 0, 255),
                                    ColorUtils.getColorInt(0, 0, 255),
                                    0);
                            context.fill(bValue - 1, 0, bValue + 1, element.getTextureHeight(), -1);
                        })))
                .addToSub(subScreenWidget);
        ExecutableWidget.instance(350, 60, 40, 40)
                .setElementHandler(new ColorSelectIcon(
                        ValueAccessor.of(() -> TextColor.fromRgb(ColorUtils.getColorInt(rValue, gValue, bValue))),
                        ButtonAction.empty()))
                .addToSub(subScreenWidget);
        ExecutableWidget.instance(370 - 60, 120, 120, 30)
                .setElementHandler(new RawTextElement(
                        (el) -> Text.literal("R: %d, G: %d, B: %d".formatted(rValue, gValue, bValue)),
                        ColorUtils.getColorInt(0, 0, 0, 255),
                        0))
                .addToSub(subScreenWidget);
        subScreenWidget.addTo(this);
    }

    @Override
    protected boolean canConfirm(ElementHandler elementHandler) {
        return ColorUtils.getColorInt(rValue, gValue, bValue, 0)
                != source.getValue().getRgb();
    }

    @Override
    protected void onConfirmButton() {
        int rgb = ColorUtils.getColorInt(rValue, gValue, bValue);
        for (var format : Formatting.values()) {
            if (format.isColor() && format.getColorValue() == rgb) {
                TextColor color = TextColor.fromFormatting(format);
                source.setValue(color);
                close();
                return;
            }
        }
        source.setValue(TextColor.fromRgb(rgb));
        close();
    }
}
