package me.matl114.gui.presets.choices;

import java.awt.*;
import me.matl114.gui.basic.*;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public class ColorSelectScreen extends ConfirmingBigScreen {
    ValueAccessor<TextColor> source;

    protected ColorSelectScreen(ValueAccessor<TextColor> color) {
        super(Text.literal("颜色选择界面").formatted(Formatting.GREEN));
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
                .setContentDelegate(ExecutableWidget.instance(0, 0, 255, 255).setElementHandler(new ElementHandler() {
                    @Override
                    public void renderAtCentered(
                            DrawableWidget element,
                            VDrawContext context,
                            int mouseX,
                            int mouseY,
                            float delta,
                            float alpha,
                            boolean shouldHighlight) {
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
                    }

                    @Override
                    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                        boolean val = false;
                        if (mouseX >= 0 && mouseX <= 255) {
                            rValue = (int) mouseX;
                            val = true;
                        }
                        if (mouseY >= 0 && mouseY <= 255) {
                            gValue = (int) mouseY;
                            val = true;
                        }
                        return val;
                    }

                    @Override
                    public boolean onAction(
                            ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                        if (type == Type.MOUSE_START_DRAG) {
                            return element.isMouseOver(mouseX, mouseY);
                        }
                        if (type == Type.MOUSE_DRAG) {
                            return onClick(element, mouseX, mouseY, button);
                        }
                        return ElementHandler.super.onAction(element, mouseX, mouseY, button, type);
                    }
                }))
                .addToSub(subScreenWidget);
        new ContentDelegateWidget<>(30, 300, 0, 0)
                .setContentDelegate(ExecutableWidget.instance(0, 0, 255, 20).setElementHandler(new ElementHandler() {

                    @Override
                    public void renderAtCentered(
                            DrawableWidget element,
                            VDrawContext context,
                            int mouseX,
                            int mouseY,
                            float delta,
                            float alpha,
                            boolean shouldHighlight) {
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
                    }

                    @Override
                    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                        if (mouseX >= 0 && mouseX <= 255) {
                            bValue = (int) mouseX;
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onAction(
                            ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                        if (type == Type.MOUSE_START_DRAG) {
                            return element.isMouseOver(mouseX, mouseY);
                        }
                        if (type == Type.MOUSE_DRAG) {
                            return onClick(element, mouseX, mouseY, button);
                        }
                        return ElementHandler.super.onAction(element, mouseX, mouseY, button, type);
                    }
                }))
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
