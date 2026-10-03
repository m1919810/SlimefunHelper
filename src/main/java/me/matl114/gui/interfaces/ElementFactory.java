package me.matl114.gui.interfaces;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.complex.BoxElement;
import me.matl114.gui.elements.PlateElement;
import me.matl114.gui.elements.SplitterTextElement;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public interface ElementFactory {
    default ElementHandler createLabel(String translationKey) {
        return createLabel(translationKey, false);
    }

    default ElementHandler createLabel(String translationKey, boolean splitter) {
        return createLabel(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                splitter);
    }

    default ElementHandler createLabel(Supplier<Text> text, Supplier<List<Text>> tooltips) {
        return createLabel(el -> text.get(), TooltipHandler.of(tooltips));
    }

    default ElementHandler createLabel(Supplier<Text> text, Supplier<List<Text>> tooltips, boolean splitter) {
        return createLabel(el -> text.get(), TooltipHandler.of(tooltips), splitter);
    }

    default DrawableWidget createLabel(String translationKey, int x, int y, int dx, int dy) {
        return createLabel(translationKey, x, y, dx, dy, false);
    }

    default DrawableWidget createLabel(String translationKey, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createLabel(translationKey, splitter));
    }

    default DrawableWidget createLabel(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy) {
        return createLabel(text, tooltips, x, y, dx, dy, false);
    }

    default DrawableWidget createLabel(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createLabel(text, tooltips, splitter));
    }

    default DrawableWidget createLabel(TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy) {
        return createLabel(provider, handler, x, y, dx, dy, false);
    }

    default DrawableWidget createLabel(
            TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createLabel(provider, handler, splitter));
    }

    ElementHandler createLabel(TextProvider provider, TooltipHandler handler);

    default ElementHandler createLabel(TextProvider provider, TooltipHandler handler, boolean splitter) {
        ElementHandler label = createLabel(provider, handler);
        return splitter
                ? new AbstractElement()
                        .withElement(label)
                        .combineRender(new SplitterTextElement(provider, () -> 0xFFFFFFFF))
                : label;
    }

    default ElementHandler createText(String translationKey) {
        return createText(translationKey, false);
    }

    default ElementHandler createText(String translationKey, boolean splitter) {
        return createText(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                splitter);
    }

    default ElementHandler createText(Supplier<Text> text, Supplier<List<Text>> tooltips) {
        return createText(el -> text.get(), TooltipHandler.of(tooltips));
    }

    default ElementHandler createText(Supplier<Text> text, Supplier<List<Text>> tooltips, boolean splitter) {
        return createText(el -> text.get(), TooltipHandler.of(tooltips), splitter);
    }

    default DrawableWidget createText(String translationKey, int x, int y, int dx, int dy) {
        return createText(translationKey, x, y, dx, dy, false);
    }

    default DrawableWidget createText(
            String translationKey, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createText(translationKey, splitter));
    }

    default DrawableWidget createText(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy) {
        return createText(text, tooltips, x, y, dx, dy, false);
    }

    default DrawableWidget createText(
            Supplier<Text> text,
            Supplier<List<Text>> tooltips,
            int x,
            int y,
            int dx,
            int dy,
            boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createText(text, tooltips, splitter));
    }

    default DrawableWidget createText(
            TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy) {
        return createText(provider, handler, x, y, dx, dy, false);
    }

    default DrawableWidget createText(
            TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createText(provider, handler, splitter));
    }

    ElementHandler createText(TextProvider provider, TooltipHandler handler);

    default ElementHandler createText(TextProvider provider, TooltipHandler handler, boolean splitter) {
        ElementHandler text = createText(provider, handler);
        return splitter
                ? new AbstractElement()
                        .withElement(text)
                        .combineRender(new SplitterTextElement(provider, () -> 0xFFFFFFFF))
                : text;
    }

    default DrawableWidget createBackground(int x, int y, int dx, int dy) {
        return createBackground(null, x, y, dx, dy);
    }

    default DrawableWidget createBackground(@Nullable ColorSampler overrideColor, int x, int y, int dx, int dy) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createBackground(overrideColor));
    }

    ElementHandler createBackground();

    default ElementHandler createBackground(@Nullable ColorSampler overrideColor) {
        return createBackground();
    }

    default ElementHandler createPlate(boolean hasInteract, @Nullable ColorSampler overrideColor) {
        return new PlateElement(hasInteract, overrideColor);
    }

    default ElementHandler createPlate(boolean hasInteract) {
        return createPlate(hasInteract, null);
    }

    default ElementHandler createTitle(String translationKey) {
        return createTitle(translationKey, false);
    }

    default ElementHandler createTitle(String translationKey, boolean splitter) {
        return createTitle(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                splitter);
    }

    default ElementHandler createTitle(Supplier<Text> text, Supplier<List<Text>> tooltips) {
        return createTitle(el -> text.get(), TooltipHandler.of(tooltips));
    }

    default ElementHandler createTitle(Supplier<Text> text, Supplier<List<Text>> tooltips, boolean splitter) {
        return createTitle(el -> text.get(), TooltipHandler.of(tooltips), splitter);
    }

    default DrawableWidget createTitle(String translationKey, int x, int y, int dx, int dy) {
        return createTitle(translationKey, x, y, dx, dy, false);
    }

    default DrawableWidget createTitle(String translationKey, int x, int y, int dx, int dy, boolean splitter) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createTitle(translationKey, splitter));
    }

    default DrawableWidget createTitle(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy) {
        return createTitle(text, tooltips, x, y, dx, dy, false);
    }

    default DrawableWidget createTitle(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy, boolean splitter) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createTitle(text, tooltips, splitter));
    }

    default DrawableWidget createTitle(TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy) {
        return createTitle(provider, handler, x, y, dx, dy, false);
    }

    default DrawableWidget createTitle(
            TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy, boolean splitter) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createTitle(provider, handler, splitter));
    }

    ElementHandler createTitle(TextProvider provider, TooltipHandler handler);

    default ElementHandler createTitle(TextProvider provider, TooltipHandler handler, boolean splitter) {
        ElementHandler title = createTitle(provider, handler);
        return splitter
                ? new AbstractElement()
                        .withElement(title)
                        .combineRender(new SplitterTextElement(provider, () -> 0xFFFFFFFF))
                : title;
    }

    default ElementHandler createConfigKey(TextProvider provider, TooltipHandler handler) {
        return createLabel(provider, handler);
    }

    default ElementHandler createConfigTitle(TextProvider provider, TooltipHandler handler) {
        return createTitle(provider, handler);
    }

    ElementHandler createButton(
            TextProvider provider,
            TooltipHandler handler,
            ButtonAction action,
            BooleanSupplier active,
            BooleanSupplier enable);

    default ElementHandler createExecuteButton(
            TextProvider provider, TooltipHandler handler, ButtonAction action, BooleanSupplier active) {
        return createButton(provider, handler, action, active, () -> false);
    }

    default ElementHandler createExecuteButton(String translationKey, ButtonAction action) {
        return createExecuteButton(translationKey, action, () -> true);
    }

    default ElementHandler createExecuteButton(String translationKey, ButtonAction action, BooleanSupplier active) {
        return createExecuteButton(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                action,
                active);
    }

    default ElementHandler createExecuteButton(
            Supplier<Text> text, Supplier<List<Text>> tooltips, ButtonAction action, BooleanSupplier active) {
        return createExecuteButton(el -> text.get(), TooltipHandler.of(tooltips), action, active);
    }

    default DrawableWidget createExecuteButton(
            String translationKey, ButtonAction action, int x, int y, int dx, int dy) {
        return createExecuteButton(translationKey, action, () -> true, x, y, dx, dy);
    }

    default DrawableWidget createExecuteButton(
            String translationKey, ButtonAction action, BooleanSupplier active, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createExecuteButton(translationKey, action, active));
    }

    default DrawableWidget createExecuteButton(
            Supplier<Text> text,
            Supplier<List<Text>> tooltips,
            ButtonAction action,
            BooleanSupplier active,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createExecuteButton(text, tooltips, action, active));
    }

    default DrawableWidget createExecuteButton(
            Supplier<Text> text, Supplier<List<Text>> tooltips, ButtonAction action, int x, int y, int dx, int dy) {
        return createExecuteButton(text, tooltips, action, () -> true, x, y, dx, dy);
    }

    default DrawableWidget createExecuteButton(
        TextProvider provider, TooltipHandler handler, ButtonAction action, BooleanSupplier active, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
            .setElementHandler(createExecuteButton(provider, handler, action, active));
    }

    default ElementHandler createToggleButton(String translationKey, ValueAccessor<Boolean> accessor) {
        return createToggleButton(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                accessor);
    }

    default ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor) {
        return createToggleButton(provider, handler, accessor, true);
    }

    default ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor, boolean frame) {
        return createButton(
                provider,
                handler,
                ButtonAction.run(() -> accessor.setValue(!accessor.getValue())),
                accessor::getValue,
                frame ? accessor::getValue : () -> false);
    }

    default ElementHandler createToggleButton(
            Supplier<Text> text, Supplier<List<Text>> tooltips, ValueAccessor<Boolean> accessor) {
        return createToggleButton(el -> text.get(), TooltipHandler.of(tooltips), accessor, true);
    }

    default ElementHandler createToggleButton(
            Supplier<Text> text,
            Supplier<List<Text>> tooltips,
            ValueAccessor<Boolean> accessor,
            boolean frame) {
        return createToggleButton(el -> text.get(), TooltipHandler.of(tooltips), accessor, frame);
    }

    default DrawableWidget createToggleButton(
            String translationKey, ValueAccessor<Boolean> accessor, int x, int y, int dx, int dy) {
        return createToggleButton(translationKey, accessor, true, x, y, dx, dy);
    }

    default DrawableWidget createToggleButton(
            String translationKey,
            ValueAccessor<Boolean> accessor,
            boolean frame,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createToggleButton(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        accessor,
                        frame));
    }

    default DrawableWidget createToggleButton(
            String translationKey,
            ValueAccessor<Boolean> accessor,
            int x,
            int y,
            int dx,
            int dy,
            boolean frame) {
        return createToggleButton(translationKey, accessor, frame, x, y, dx, dy);
    }

    default DrawableWidget createToggleButton(
            Supplier<Text> text,
            Supplier<List<Text>> tooltips,
            ValueAccessor<Boolean> accessor,
            int x,
            int y,
            int dx,
            int dy) {
        return createToggleButton(text, tooltips, accessor, true, x, y, dx, dy);
    }

    default DrawableWidget createToggleButton(
            Supplier<Text> text,
            Supplier<List<Text>> tooltips,
            ValueAccessor<Boolean> accessor,
            boolean frame,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createToggleButton(text, tooltips, accessor, frame));
    }

    default DrawableWidget createToggleButton(
            Supplier<Text> text,
            Supplier<List<Text>> tooltips,
            ValueAccessor<Boolean> accessor,
            int x,
            int y,
            int dx,
            int dy,
            boolean frame) {
        return createToggleButton(text, tooltips, accessor, frame, x, y, dx, dy);
    }

    default DrawableWidget createToggleButton(
        TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor, boolean frame, int x,
        int y,
        int dx,
        int dy){
        return ExecutableWidget.instance(x, y, dx, dy)
            .setElementHandler(createToggleButton(provider, handler, accessor, frame));
    }

    ElementHandler createElement(
            ElementHandler handler, @Nullable TooltipHandler tooltips, @Nullable ButtonAction action);

    default DrawableWidget createElement(
            ElementHandler handler,
            @Nullable TooltipHandler tooltips,
            @Nullable ButtonAction action,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createElement(handler, tooltips, action));
    }

    default DrawableWidget createElement(
            RenderHandler handler,
            @Nullable TooltipHandler tooltips,
            @Nullable ButtonAction action,
            int x,
            int y,
            int dx,
            int dy) {
        ElementHandler element = new BoxElement(action == null ? ButtonAction.empty() : action).combineRender(handler);
        if (tooltips != null) {
            element = element.withTooltips(tooltips);
        }
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(element);
    }

    <T> ElementHandler createInput(Value<T> string);

    default <T> DrawableWidget createInput(Value<T> string, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createInput(string));
    }

    default <T> ElementHandler createSlidingInput(Value<T> string, Function<Double, T> slider) {
        return createSlidingInput(string, WrapperFactory.of(slider, value -> 0.0));
    }

    <T> ElementHandler createSlidingInput(Value<T> string, WrapperFactory<Double, T> sliderFactory);

    default <T> ElementHandler createSlider(Value<T> value, WrapperFactory<Double, T> sliderFactory) {
        return createSlidingInput(value, sliderFactory)
                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                        "widget.gui.slider.tooltips", "\u5de6\u952e\u8f93\u5165\u7f16\u8f91\n\u53f3\u952e\u8bbe\u7f6e\u4f4d\u7f6e")));
    }

    default <T> DrawableWidget createSlider(
            Value<T> value,
            WrapperFactory<Double, T> sliderFactory,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createSlider(value, sliderFactory));
    }

    default <T> DrawableWidget createSlidingInput(
            Value<T> string,
            WrapperFactory<Double, T> sliderFactory,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createSlidingInput(string, sliderFactory));
    }
}
