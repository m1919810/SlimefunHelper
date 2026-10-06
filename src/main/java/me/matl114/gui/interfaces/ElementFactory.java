package me.matl114.gui.interfaces;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.complex.BoxElement;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public interface ElementFactory {
    default ElementHandler createLabel(String translationKey) {
        return createLabel(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")));
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
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createLabel(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", ""))));
    }

    default DrawableWidget createLabel(String translationKey, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createLabel(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        splitter));
    }

    default DrawableWidget createLabel(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createLabel(el -> text.get(), TooltipHandler.of(tooltips)));
    }

    default DrawableWidget createLabel(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createLabel(el -> text.get(), TooltipHandler.of(tooltips), splitter));
    }

    default DrawableWidget createLabel(TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createLabel(provider, handler));
    }

    default DrawableWidget createLabel(
            TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createLabel(provider, handler, splitter));
    }

    ElementHandler createLabel(TextProvider provider, TooltipHandler handler);

    ElementHandler createLabel(TextProvider provider, TooltipHandler handler, boolean splitter);

    default ElementHandler createText(String translationKey) {
        return createText(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")));
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
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createText(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", ""))));
    }

    default DrawableWidget createText(String translationKey, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createText(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        splitter));
    }

    default DrawableWidget createText(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createText(el -> text.get(), TooltipHandler.of(tooltips)));
    }

    default DrawableWidget createText(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createText(el -> text.get(), TooltipHandler.of(tooltips), splitter));
    }

    default DrawableWidget createText(TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createText(provider, handler));
    }

    default DrawableWidget createText(
            TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy, boolean splitter) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createText(provider, handler, splitter));
    }

    ElementHandler createText(TextProvider provider, TooltipHandler handler);

    ElementHandler createText(TextProvider provider, TooltipHandler handler, boolean splitter);

    default ElementHandler createText(
            TextProvider provider, TooltipHandler handler, @Nullable TextAlign overrideAlign) {
        return createText(provider, handler);
    }

    default ElementHandler createText(
            TextProvider provider, TooltipHandler handler, boolean splitter, @Nullable TextAlign overrideAlign) {
        return createText(provider, handler, splitter);
    }

    default DrawableWidget createBackground(int x, int y, int dx, int dy) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createBackground());
    }

    default DrawableWidget createBackground(@Nullable ColorSampler overrideColor, int x, int y, int dx, int dy) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createBackground(overrideColor));
    }

    ElementHandler createBackground();

    ElementHandler createBackground(@Nullable ColorSampler overrideColor);

    ElementHandler createPlate(boolean hasInteract, @Nullable ColorSampler overrideColor);

    default ElementHandler createPlate(boolean hasInteract) {
        return createPlate(hasInteract, null);
    }

    default ElementHandler createTitle(String translationKey) {
        return createTitle(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")));
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
        return DisplayWidget.instance(x, y, dx, dy)
                .setRenderHandler(createTitle(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", ""))));
    }

    default DrawableWidget createTitle(String translationKey, int x, int y, int dx, int dy, boolean splitter) {
        return DisplayWidget.instance(x, y, dx, dy)
                .setRenderHandler(createTitle(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        splitter));
    }

    default DrawableWidget createTitle(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy) {
        return DisplayWidget.instance(x, y, dx, dy)
                .setRenderHandler(createTitle(el -> text.get(), TooltipHandler.of(tooltips)));
    }

    default DrawableWidget createTitle(
            Supplier<Text> text, Supplier<List<Text>> tooltips, int x, int y, int dx, int dy, boolean splitter) {
        return DisplayWidget.instance(x, y, dx, dy)
                .setRenderHandler(createTitle(el -> text.get(), TooltipHandler.of(tooltips), splitter));
    }

    default DrawableWidget createTitle(TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createTitle(provider, handler));
    }

    default DrawableWidget createTitle(
            TextProvider provider, TooltipHandler handler, int x, int y, int dx, int dy, boolean splitter) {
        return DisplayWidget.instance(x, y, dx, dy).setRenderHandler(createTitle(provider, handler, splitter));
    }

    ElementHandler createTitle(TextProvider provider, TooltipHandler handler);

    ElementHandler createTitle(TextProvider provider, TooltipHandler handler, boolean splitter);

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
        return createButton(provider, handler, action, active, active);
    }

    default ElementHandler createExecuteButton(TextProvider provider, TooltipHandler handler, ButtonAction action) {
        return createButton(provider, handler, action, () -> true, () -> false);
    }

    default ElementHandler createExecuteButton(String translationKey, ButtonAction action) {
        return createButton(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                action,
                () -> true,
                () -> false);
    }

    default ElementHandler createExecuteButton(String translationKey, ButtonAction action, BooleanSupplier active) {
        return createButton(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                action,
                active,
                active);
    }

    default ElementHandler createExecuteButton(
            Supplier<Text> text, Supplier<List<Text>> tooltips, ButtonAction action, BooleanSupplier active) {
        return createButton(el -> text.get(), TooltipHandler.of(tooltips), action, active, active);
    }

    default DrawableWidget createExecuteButton(
            String translationKey, ButtonAction action, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createButton(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        action,
                        () -> true,
                        () -> false));
    }

    default DrawableWidget createExecuteButton(
            String translationKey, ButtonAction action, BooleanSupplier active, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createButton(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        action,
                        active,
                        active));
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
                .setElementHandler(createButton(el -> text.get(), TooltipHandler.of(tooltips), action, active, active));
    }

    default DrawableWidget createExecuteButton(
            Supplier<Text> text, Supplier<List<Text>> tooltips, ButtonAction action, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(
                        createButton(el -> text.get(), TooltipHandler.of(tooltips), action, () -> true, () -> false));
    }

    default DrawableWidget createExecuteButton(
            TextProvider provider,
            TooltipHandler handler,
            ButtonAction action,
            BooleanSupplier active,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createButton(provider, handler, action, active, active));
    }

    default ElementHandler createToggleButton(String translationKey, ValueAccessor<Boolean> accessor) {
        return createToggleButton(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                accessor,
                true);
    }

    default ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor) {
        return createToggleButton(provider, handler, accessor, true);
    }

    ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor, boolean frame);

    default ElementHandler createToggleButton(
            Supplier<Text> text, Supplier<List<Text>> tooltips, ValueAccessor<Boolean> accessor) {
        return createToggleButton(el -> text.get(), TooltipHandler.of(tooltips), accessor, true);
    }

    default ElementHandler createToggleButton(
            Supplier<Text> text, Supplier<List<Text>> tooltips, ValueAccessor<Boolean> accessor, boolean frame) {
        return createToggleButton(el -> text.get(), TooltipHandler.of(tooltips), accessor, frame);
    }

    default DrawableWidget createToggleButton(
            String translationKey, ValueAccessor<Boolean> accessor, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createToggleButton(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        accessor,
                        true));
    }

    default DrawableWidget createToggleButton(
            String translationKey, ValueAccessor<Boolean> accessor, boolean frame, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createToggleButton(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        accessor,
                        frame));
    }

    default DrawableWidget createToggleButton(
            String translationKey, ValueAccessor<Boolean> accessor, int x, int y, int dx, int dy, boolean frame) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createToggleButton(
                        TextProvider.of(Text.translatable(translationKey)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")),
                        accessor,
                        frame));
    }

    default DrawableWidget createToggleButton(
            Supplier<Text> text,
            Supplier<List<Text>> tooltips,
            ValueAccessor<Boolean> accessor,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createToggleButton(el -> text.get(), TooltipHandler.of(tooltips), accessor, true));
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
                .setElementHandler(createToggleButton(el -> text.get(), TooltipHandler.of(tooltips), accessor, frame));
    }

    default DrawableWidget createToggleButton(
            TextProvider provider,
            TooltipHandler handler,
            ValueAccessor<Boolean> accessor,
            boolean frame,
            int x,
            int y,
            int dx,
            int dy) {
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

    default <T> ElementHandler createInput(Value<T> string, @Nullable TextAlign overrideAlign) {
        return createInput(string);
    }

    default <T> DrawableWidget createInput(Value<T> string, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createInput(string));
    }

    default <T> DrawableWidget createInput(
            Value<T> string, @Nullable TextAlign overrideAlign, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createInput(string, overrideAlign));
    }

    default <T> ElementHandler createSlidingInput(Value<T> string, Function<Double, T> slider) {
        return createSlidingInput(string, WrapperFactory.of(slider, value -> 0.0));
    }

    <T> ElementHandler createSlidingInput(Value<T> string, WrapperFactory<Double, T> sliderFactory);

    default <T> ElementHandler createSlidingInput(
            Value<T> string, WrapperFactory<Double, T> sliderFactory, @Nullable TextAlign overrideAlign) {
        return createSlidingInput(string, sliderFactory);
    }

    default <T> ElementHandler createSlider(Value<T> value, WrapperFactory<Double, T> sliderFactory) {
        return createSlider(value, sliderFactory, null);
    }

    default <T> ElementHandler createSlider(
            Value<T> value, WrapperFactory<Double, T> sliderFactory, @Nullable TextAlign overrideAlign) {
        return createSlidingInput(value, sliderFactory, overrideAlign)
                .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                        "widget.gui.slider.tooltips",
                        "\u5de6\u952e\u8f93\u5165\u7f16\u8f91\n\u53f3\u952e\u8bbe\u7f6e\u4f4d\u7f6e")));
    }

    default <T> DrawableWidget createSlider(
            Value<T> value, WrapperFactory<Double, T> sliderFactory, int x, int y, int dx, int dy) {
        return createSlider(value, sliderFactory, null, x, y, dx, dy);
    }

    default <T> DrawableWidget createSlider(
            Value<T> value,
            WrapperFactory<Double, T> sliderFactory,
            @Nullable TextAlign overrideAlign,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(createSlidingInput(value, sliderFactory, overrideAlign)
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.slider.tooltips",
                                "\u5de6\u952e\u8f93\u5165\u7f16\u8f91\n\u53f3\u952e\u8bbe\u7f6e\u4f4d\u7f6e"))));
    }

    default <T> DrawableWidget createSlidingInput(
            Value<T> string, WrapperFactory<Double, T> sliderFactory, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy).setElementHandler(createSlidingInput(string, sliderFactory));
    }
}
