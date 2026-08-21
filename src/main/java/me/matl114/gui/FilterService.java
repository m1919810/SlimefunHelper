package me.matl114.gui;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

public class FilterService {
    public static BiPredicate<String, RecipeEntry> RECIPE_FILTER = (str, i) -> {
        if (str == null || str.isEmpty()) return true;
        if (str.startsWith("@")) {
            String str1 = str.substring(1);
            return i.id().toLowerCase(Locale.ROOT).contains(str1.toLowerCase(Locale.ROOT));
        } else {
            return nameMatch(i.output().getName().getString().replaceAll("§.", ""), str);
        }
    };
    public static BiPredicate<String, ItemStack> ITEM_FILTER = (str, i) -> {
        if (str == null || str.isEmpty()) return true;
        return nameMatch(i.getName().getString().replaceAll("§.", ""), str);
    };

    public static Filter<String> RTYPE_ID_FILTER = (str, i, bl) -> {
        if (bl) {
            try {
                return Pattern.matches(str, i);
            } catch (Throwable e) {
                return false;
            }
        } else {
            return i.contains(str);
        }
    };

    public static boolean nameMatch(String name, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        filter = filter.toLowerCase(Locale.ROOT);
        name = name.toLowerCase(Locale.ROOT);
        if (name.contains(filter)) {
            return true;
        }
        String pinyin1 = PinyinHelper.toPinyin(name, PinyinStyleEnum.INPUT, "").toLowerCase(Locale.ROOT);
        if (pinyin1.contains(filter)) {
            return true;
        }
        pinyin1 = PinyinHelper.toPinyin(name, PinyinStyleEnum.FIRST_LETTER, "").toLowerCase(Locale.ROOT);
        return pinyin1.contains(filter);
    }

    protected static Identifier RESET_FILTER_TEXTURE = new Identifier("minecraft", "container/beacon/cancel");

    public static SubScreenWidget createFilter(
            ValueAccessor<String> accessor, Runnable updateListener, int x, int y, int dx, int dy) {
        return createFilter(accessor, (v) -> updateListener.run(), x, y, dx, dy);
    }

    public static SubScreenWidget createFilter(
            ValueAccessor<String> accessor, Consumer<String> updateListener, int x, int y, int dx, int dy) {

        var textField = McWidgetHelpers.createTextFieldEditBox(
                dy,
                0,
                dx - dy,
                dy,
                (t, r) -> {
                    if (!Objects.equals(accessor.getValue(), r)) {
                        accessor.setValue(r);
                        updateListener.accept(r);
                    }
                },
                accessor.getValue());
        var textFieldCleanerBackground = DisplayWidget.instance(0, 0, dy, dy)
                .setRenderHandler(new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.empty())
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.filter-service.reset-filter.tooltips", ""))));
        var textFieldCleaner = ExecutableWidget.instance(0, 0, dy, dy)
                .setElementHandler(IconElement.fixedGui(RESET_FILTER_TEXTURE, ButtonAction.run(() -> {
                            if (textField.getDelegate() != null) {
                                textField.getDelegate().setText("");
                            }
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.filter-service.reset-filter.tooltips", ""))));
        return new SubScreenWidget(x, y, dx, dy)
                .addDrawableChild(textField)
                .addDrawableChild(textFieldCleanerBackground)
                .addDrawableChild(textFieldCleaner);
    }

    public static SubScreenWidget createFilterWithRegex(
            ValueAccessor<String> accessor,
            ValueAccessor<Boolean> useRegex,
            Runnable acceptor,
            int x,
            int y,
            int dx,
            int dy) {
        return createFilterWithRegex(
                accessor, useRegex, (BiConsumer<String, Boolean>) (str, bl) -> acceptor.run(), x, y, dx, dy);
    }

    public static SubScreenWidget createFilterWithRegex(
            ValueAccessor<String> accessor,
            ValueAccessor<Boolean> useRegex,
            BiConsumer<String, Boolean> acceptor,
            int x,
            int y,
            int dx,
            int dy) {
        var textField = McWidgetHelpers.createTextFieldEditBox(
                dy,
                0,
                dx - 2 * dy,
                dy,
                (t, r) -> {
                    if (!Objects.equals(accessor.getValue(), r)) {
                        accessor.setValue(r);
                        acceptor.accept(r, useRegex.getValue());
                    }
                },
                accessor.getValue());
        var textFieldCleanerBackground = DisplayWidget.instance(0, 0, dy, dy)
                .setRenderHandler(new ButtonElement(TextProvider.of(Text.empty()), ButtonAction.empty())
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.filter-service.reset-filter.tooltips", ""))));
        var textFieldCleaner = ExecutableWidget.instance(0, 0, dy, dy)
                .setElementHandler(IconElement.fixedGui(RESET_FILTER_TEXTURE, ButtonAction.run(() -> {
                            if (textField.getDelegate() != null) {
                                textField.getDelegate().setText("");
                            }
                        }))
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.filter-service.reset-filter.tooltips", ""))));
        var toggleRegex = ExecutableWidget.instance(dx - dy, 0, dy, dy)
                .setElementHandler(new ButtonElement(
                                el -> {
                                    var text = Text.literal("(.*)");
                                    if (useRegex.getValue()) {
                                        text = text.withColor(Colors.GREEN);
                                    }
                                    return text;
                                },
                                ButtonAction.run(() -> {
                                    useRegex.setValue(!useRegex.getValue());
                                    acceptor.accept(accessor.getValue(), useRegex.getValue());
                                }))
                        .setActivePredicate((el) -> useRegex.getValue())
                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.gui.filter-service.use-regex.tooltips", ""))));
        return new SubScreenWidget(x, y, dx, dy)
                .addDrawableChild(textField)
                .addDrawableChild(textFieldCleanerBackground)
                .addDrawableChild(textFieldCleaner)
                .addDrawableChild(toggleRegex);
    }

    public interface Filter<T> extends BiPredicate<T, String> {
        public boolean isAccepted(T value, String string, boolean isRegex);

        default boolean test(T value, String string) {
            return isAccepted(value, string, false);
        }
    }
}
