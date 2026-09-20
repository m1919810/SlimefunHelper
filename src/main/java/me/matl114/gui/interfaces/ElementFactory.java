package me.matl114.gui.interfaces;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ElementHandler;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public interface ElementFactory {
    default ElementHandler createLabel(String translationKey) {
        return createLabel(
                TextProvider.of(Text.translatable(translationKey)),
                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(translationKey + ".tooltips", "")));
    }

    default ElementHandler createLabel(Supplier<Text> text, Supplier<List<Text>> tooltips) {
        return createLabel(el -> text.get(), TooltipHandler.of(tooltips));
    }

    ElementHandler createLabel(TextProvider provider, TooltipHandler handler);

    ElementHandler createTitle(TextProvider provider, TooltipHandler handler);

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

    default ElementHandler createToggleButton(
            TextProvider provider, TooltipHandler handler, ValueAccessor<Boolean> accessor) {
        return createButton(
                provider,
                handler,
                ButtonAction.run(() -> accessor.setValue(!accessor.getValue())),
                accessor::getValue,
                accessor::getValue);
    }

    ElementHandler createElement(
            ElementHandler handler, @Nullable TooltipHandler tooltips, @Nullable ButtonAction action);

    // todo: create Slider
    // todo: create string input
    <T> ElementHandler createInput(Value<T> string);

    <T> ElementHandler createSlidingInput(Value<T> string, Function<Double, T> slider);
}
