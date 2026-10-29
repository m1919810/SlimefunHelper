package me.matl114.gui.interfaces;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.matl114.gui.basic.*;
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

    @Override
    public SelectBackgroundElement createSelectorTab(ValueAccessor<Boolean> selected) {
        return createSelectorTab(
                selected,
                () -> colors().selectorSelectedColor().color(),
                () -> colors().selectorHoverColor().color(),
                () -> colors().selectorNormalColor().color());
    }

    protected ColorSampler textColor() {
        return () -> colors().textColor().color();
    }

    protected ColorSampler inputTextColor() {
        return () -> colors().inputTextColor().color();
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
        return new ColorPlateElement(hasInteract, plate);
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
        return createText(provider, handler, (TextAlign) null);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler, boolean splitter) {
        return createText(provider, handler, splitter, null);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler, @Nullable TextAlign overrideAlign) {
        return createText(provider, handler, false, overrideAlign);
    }

    @Override
    public ElementHandler createText(
            TextProvider provider, TooltipHandler handler, boolean splitter, @Nullable TextAlign overrideAlign) {
        TextProvider alignedProvider =
                overrideAlign == null ? provider : TextProvider.withAlign(provider, overrideAlign);
        RawTextElement text = new RawTextElement(alignedProvider, textColor());
        if (splitter) {
            text.combineRender(new TextSplitterElement(alignedProvider, textColor()));
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
        label.withElement(new RawTextElement(alignedProvider, textColor()));
        if (splitter) {
            label.withElement(new TextSplitterElement(alignedProvider, textColor()));
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
                                return colors().activeFrameColor().color();
                            }
                            return selected ? -1 : colors().inactiveFrameColor().color();
                        })
                .withTooltips(handler);
    }

    @Override
    public ElementHandler createElement(
            ElementHandler handler, @Nullable TooltipHandler tooltips, @Nullable ButtonAction action) {
        AbstractElement element = new AbstractElement();
        element = element.withElement(handler);
        if (tooltips != null) {
            element = element.withTooltips(tooltips);
        }
        if (action != null) {
            element = element.withInputHandler(new BoxElement(action));
        }
        return element;
    }

    @Override
    public <T> ElementHandler createInput(Value<T> value) {
        return createInput(value, null);
    }

    @Override
    public <T> ElementHandler createInput(Value<T> value, @Nullable TextAlign overrideAlign) {
        return TextFieldElement.ofValue(value)
                .setBackgroundColor(() -> colors().textBackground().color())
                .setTextColor(inputTextColor())
                .setAlign(overrideAlign == null ? inputTextAlign() : overrideAlign);
    }

    @Override
    public <T> ElementHandler createSlidingInput(Value<T> value, WrapperFactory<Double, T> sliderFactory) {
        return createSlidingInput(value, sliderFactory, null);
    }

    @Override
    public <T> ElementHandler createSlidingInput(
            Value<T> value, WrapperFactory<Double, T> sliderFactory, @Nullable TextAlign overrideAlign) {
        return new SlidingInputElement<>(value, sliderFactory)
                .setSliderColor(() -> colors().sliderColor().color())
                .setBackgroundColor(() -> colors().textBackground().color())
                .setTextColor(inputTextColor())
                .setAlign(overrideAlign == null ? sliderTextAlign() : overrideAlign);
    }
}
