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
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.ColorLabelElement;
import me.matl114.gui.elements.LabelElement;
import me.matl114.gui.elements.PlateElement;
import me.matl114.gui.elements.SplitterTextElement;
import me.matl114.gui.elements.TextFieldElement;
import me.matl114.hacks.utils.gui.TextConfig;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.WrapperFactory;
import org.jetbrains.annotations.Nullable;

public final class VanillaElementFactory implements ElementFactory {
    public static final VanillaElementFactory INSTANCE = new VanillaElementFactory(() -> TextConfig.DEFAULT);

    private final Supplier<TextConfig> textConfigSupplier;

    public VanillaElementFactory(Supplier<TextConfig> textConfigSupplier) {
        this.textConfigSupplier = textConfigSupplier;
    }

    private ColorSampler textColor() {
        return () -> textConfigSupplier.get().color().color();
    }

    private TextAlign textAlign() {
        return textConfigSupplier.get().textAlign().textAlign();
    }

    @Override
    public ElementHandler createBackground() {
        return createBackground(null);
    }

    @Override
    public ElementHandler createBackground(ColorSampler overrideColor) {
        return new LabelElement(overrideColor == null ? textColor().getColorInt() : overrideColor.getColorInt());
    }

    @Override
    public ElementHandler createPlate(boolean hasInteract, ColorSampler overrideColor) {
        return new PlateElement(hasInteract, overrideColor);
    }

    @Override
    public ElementHandler createLabel(TextProvider provider, TooltipHandler handler) {
        return new LabelElement(TextProvider.withAlign(provider, textAlign()), textColor().getColorInt())
                .withTooltips(handler);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler) {
        return new RawTextElement(provider, textColor()).withTooltips(handler);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler, boolean splitter) {
        ColorSampler textColor = textColor();
        RawTextElement text = new RawTextElement(provider, textColor);
        if (splitter) {
            text.combineRender(new SplitterTextElement(provider, textColor));
        }
        return text.withTooltips(handler);
    }

    @Override
    public ElementHandler createTitle(TextProvider provider, TooltipHandler handler) {
        return createTitle(provider, handler, false);
    }

    @Override
    public ElementHandler createTitle(TextProvider provider, TooltipHandler handler, boolean splitter) {
        ColorSampler textColor = textColor();
        TextProvider alignedProvider = TextProvider.withAlign(provider, textAlign());
        ColorLabelElement title = new ColorLabelElement(textColor);
        title.withElement(new RawTextElement(alignedProvider, textColor));
        if (splitter) {
            title.combineRender(new SplitterTextElement(alignedProvider, textColor));
        }
        return title.withTooltips(handler);
    }

    @Override
    public ElementHandler createLabel(TextProvider provider, TooltipHandler handler, boolean splitter) {
        if (!splitter) {
            return createLabel(provider, handler);
        }
        ColorSampler textColor = textColor();
        TextProvider alignedProvider = TextProvider.withAlign(provider, textAlign());
        return new SplitterTextElement(alignedProvider, textColor)
                .combineRender(new RawTextElement(alignedProvider, textColor))
                .withTooltips(handler);
    }

    @Override
    public ElementHandler createButton(
            TextProvider provider,
            TooltipHandler handler,
            ButtonAction action,
            BooleanSupplier active,
            BooleanSupplier enable) {
        ButtonElement button = new ButtonElement(provider, action, textColor());
        button.setActivePredicate(element -> active.getAsBoolean());
        return button.withTooltips(handler);
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
        return TextFieldElement.ofValue(value);
    }

    @Override
    public <T> ElementHandler createSlidingInput(Value<T> value, WrapperFactory<Double, T> sliderFactory) {
        return createInput(value);
    }
}
