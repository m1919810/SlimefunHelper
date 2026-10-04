package me.matl114.gui.interfaces;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.complex.BoxElement;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.gui.elements.*;
import me.matl114.hacks.utils.gui.GuiColorSamplier;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class CustomElementFactory implements ElementFactory {
    private final Supplier<GuiColorSamplier> colorSupplier;

    public CustomElementFactory(Supplier<GuiColorSamplier> colorSupplier) {
        this.colorSupplier = colorSupplier;
    }

    protected GuiColorSamplier colors() {
        return colorSupplier.get();
    }

    protected ColorSampler textColor() {
        return () -> colors().textColor().withAlpha(255).color();
    }

    protected ColorSampler labelBackground() {
        return () -> colors().labelColor().color();
    }

    protected ColorSampler backgroundColor() {
        return () -> colors().backgroundColor().color();
    }

    protected ColorSampler plateColor() {
        return () -> colors().plateColor().color();
    }

    protected ColorSampler titleBackground() {
        return () -> colors().titleColor().withAlpha(255).color();
    }

    protected ColorSampler buttonBackground(BooleanSupplier active) {
        return () -> (active.getAsBoolean() ? colors().buttonActiveColor() : colors().buttonInactiveColor()).color();
    }

    protected TextAlign buttonTextAlign() {
        return colors().buttonTextAlign().textAlign();
    }

    protected TextAlign inputTextAlign() {
        return colors().inputTextAlign().textAlign();
    }

    protected TextAlign sliderTextAlign() {
        return colors().sliderTextAlign().textAlign();
    }

    protected TextAlign textAlign() {
        return colors().textAlign().textAlign();
    }

    @Override
    public ElementHandler createBackground() {
        return createBackground(null);
    }

    @Override
    public ElementHandler createBackground(ColorSampler overrideColor) {
        ColorSampler background = overrideColor == null ? backgroundColor() : overrideColor;
        return new ColorLabelElement(background)
                .withElement(new RawTextElement(TextProvider.of(Text.empty()), textColor()));
    }

    @Override
    public ElementHandler createPlate(boolean hasInteract, ColorSampler overrideColor) {
        ColorSampler plate = overrideColor == null ? plateColor() : overrideColor;
        return new PlateElement(hasInteract, plate);
    }

    @Override
    public ElementHandler createLabel(TextProvider provider, TooltipHandler handler) {
        return createLabel(provider, handler, false);
    }

    @Override
    public ElementHandler createLabel(TextProvider provider, TooltipHandler handler, boolean splitter) {
        return createColorLabel(provider, handler, labelBackground(), splitter);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler) {
        return new RawTextElement(provider, textColor()).withTooltips(handler);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler, boolean splitter) {
        RawTextElement text = new RawTextElement(provider, textColor());
        if (splitter) {
            text.combineRender(new SplitterTextElement(provider, textColor()));
        }
        return text.withTooltips(handler);
    }

    @Override
    public ElementHandler createTitle(TextProvider provider, TooltipHandler handler) {
        return createTitle(provider, handler, false);
    }

    @Override
    public ElementHandler createTitle(TextProvider provider, TooltipHandler handler, boolean splitter) {
        return createColorLabel(provider, handler, titleBackground(), splitter);
    }

    private ElementHandler createColorLabel(
            TextProvider provider, TooltipHandler handler, ColorSampler background, boolean splitter) {
        ColorLabelElement label = new ColorLabelElement(background);
        TextProvider alignedProvider = TextProvider.withAlign(provider, textAlign());
        if (splitter) {
            label.withElement(new SplitterTextElement(alignedProvider, textColor()));
        } else {
            label.withElement(new RawTextElement(alignedProvider, textColor()));
        }
        return label.withTooltips(handler);
    }

    @Override
    public ElementHandler createButton(
            TextProvider provider,
            TooltipHandler handler,
            ButtonAction action,
            BooleanSupplier active,
            BooleanSupplier enable) {
        return new ColorBoxElement(
                        action,
                        TextProvider.withAlign(provider, buttonTextAlign()),
                        buttonBackground(active),
                        textColor(),
                        (el, selected) -> {
                            if (enable.getAsBoolean()) {
                                return colors().frameColor().color();
                            }
                            return selected ? -1 : null;
                        })
                .withTooltips(handler);
    }

    @Override
    public ElementHandler createElement(
            ElementHandler handler, @Nullable TooltipHandler tooltips, @Nullable ButtonAction action) {
        ElementHandler element = handler;
        if (tooltips != null) {
            element = element.withTooltips(tooltips);
        }
        if (action != null) {
            element = new BoxElement(action).withElement(element);
        }
        return element;
    }

    @Override
    public <T> ElementHandler createInput(Value<T> value) {
        return TextFieldElement.ofValue(value)
                .setBackgroundColor(() -> colors().textBackground().color())
                .setTextColor(textColor())
                .setAlign(inputTextAlign());
    }

    @Override
    public <T> ElementHandler createSlidingInput(Value<T> value, WrapperFactory<Double, T> sliderFactory) {
        return new SlidingInputElement<>(value, sliderFactory)
                .setSliderColor(() -> colors().sliderColor().color())
                .setBackgroundColor(() -> colors().textBackground().color())
                .setTextColor(textColor())
                .setAlign(sliderTextAlign());
    }

    @Override
    public ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor) {
        return createToggleButton(provider, handler, accessor, true);
    }

    @Override
    public ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor, boolean frame) {
        return createButton(
                provider,
                handler,
                ButtonAction.run(() -> accessor.setValue(!accessor.getValue())),
                accessor::getValue,
                frame ? accessor::getValue : () -> false);
    }
}
