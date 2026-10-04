package me.matl114.gui.interfaces;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.hacks.utils.config.GuiSetting;
import me.matl114.hacks.utils.gui.GuiStyle;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.WrapperFactory;
import org.jetbrains.annotations.Nullable;

public final class CombinedElementFactory implements ElementFactory {
    private final Supplier<GuiSetting> settingSupplier;
    private final Map<GuiStyle, ElementFactory> delegates = new EnumMap<>(GuiStyle.class);

    public CombinedElementFactory(Supplier<GuiSetting> settingSupplier) {
        this.settingSupplier = settingSupplier;
        delegates.put(
                GuiStyle.VANILLA,
                new VanillaElementFactory(() -> settingSupplier.get().textConfig()));
        delegates.put(GuiStyle.CUSTOM, new CustomElementFactory(settingSupplier::get));
    }

    private ElementFactory delegate(ElementType type) {
        return delegates.get(settingSupplier.get().style(type));
    }

    @Override
    public ElementHandler createBackground() {
        return delegate(ElementType.BACKGROUND).createBackground();
    }

    @Override
    public ElementHandler createBackground(ColorSampler overrideColor) {
        return delegate(ElementType.BACKGROUND).createBackground(overrideColor);
    }

    @Override
    public ElementHandler createPlate(boolean hasInteract, ColorSampler overrideColor) {
        return delegate(ElementType.BACKGROUND).createPlate(hasInteract, overrideColor);
    }

    @Override
    public ElementHandler createLabel(TextProvider provider, TooltipHandler handler) {
        return delegate(ElementType.LABEL).createLabel(provider, handler);
    }

    @Override
    public ElementHandler createLabel(TextProvider provider, TooltipHandler handler, boolean splitter) {
        return delegate(ElementType.LABEL).createLabel(provider, handler, splitter);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler) {
        return delegate(ElementType.LABEL).createText(provider, handler);
    }

    @Override
    public ElementHandler createText(TextProvider provider, TooltipHandler handler, boolean splitter) {
        return delegate(ElementType.LABEL).createText(provider, handler, splitter);
    }

    @Override
    public ElementHandler createTitle(TextProvider provider, TooltipHandler handler) {
        return delegate(ElementType.TITLE).createTitle(provider, handler);
    }

    @Override
    public ElementHandler createTitle(TextProvider provider, TooltipHandler handler, boolean splitter) {
        return delegate(ElementType.TITLE).createTitle(provider, handler, splitter);
    }

    @Override
    public ElementHandler createConfigKey(TextProvider provider, TooltipHandler handler) {
        return delegate(ElementType.LABEL).createConfigKey(provider, handler);
    }

    @Override
    public ElementHandler createConfigTitle(TextProvider provider, TooltipHandler handler) {
        return delegate(ElementType.TITLE).createConfigTitle(provider, handler);
    }

    @Override
    public ElementHandler createButton(
            TextProvider provider,
            TooltipHandler handler,
            ButtonAction action,
            BooleanSupplier active,
            BooleanSupplier enable) {
        return delegate(ElementType.BUTTON).createButton(provider, handler, action, active, enable);
    }

    @Override
    public ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor, boolean frame) {
        return delegate(ElementType.BUTTON).createToggleButton(provider, handler, accessor, frame);
    }

    @Override
    public ElementHandler createElement(
            ElementHandler handler, @Nullable TooltipHandler tooltips, @Nullable ButtonAction action) {
        return delegate(ElementType.BUTTON).createElement(handler, tooltips, action);
    }

    @Override
    public <T> ElementHandler createInput(Value<T> value) {
        return delegate(ElementType.INPUT).createInput(value);
    }

    @Override
    public <T> ElementHandler createSlidingInput(Value<T> value, WrapperFactory<Double, T> sliderFactory) {
        return delegate(ElementType.SLIDER).createSlidingInput(value, sliderFactory);
    }
}
