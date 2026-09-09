package me.matl114.gui;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.*;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.DefaultedKeyValueInputWidget;
import me.matl114.gui.complex.config.KeyValueInputWidget;
import me.matl114.gui.elements.AdvancedScrollElement;
import me.matl114.gui.elements.ColorLabelTextElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.elements.TextFieldElement;
import me.matl114.gui.presets.lists.StringListModifyScreen;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.Refs;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.collections.MutableRecord;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.kv.ListAttrKeyValue;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class WidgetUtils {
    public static final ConfigScreenLayout DEFAULT_CONFIG_SCREEN_LAYOUT = new ConfigScreenLayout(140, 10, 180, 18, 2);

    public static WidgetUtils.ConfigScreenPalette DEFAULT_PALETTE = new WidgetUtils.ConfigScreenPalette(
            () -> Colors.WHITE,
            () -> new WrapColor(("#984FDB")).withAlpha(255),
            () -> Colors.WHITE,
            () -> new WrapColor(("#323232")).withAlpha(255));

    public static DrawableWidget getFocusedWidget(DrawableWidget drawable) {
        DrawableWidget current = drawable;
        while (true) {
            if (current instanceof SubSelectable subScreen) {
                current = subScreen.getSelected();
            } else if (current instanceof ContentDelegateWidget delegate
                    && delegate.getDelegate() instanceof DrawableWidget draw) {
                current = draw;
            } else {
                break;
            }
        }
        return current;
    }

    public static List<DrawableWidget> getWidgetHierarchy(DrawableWidget drawable) {
        List<DrawableWidget> layers = new ArrayList<>();
        DrawableWidget current = drawable;
        while (true) {
            if (current instanceof SubSelectable subScreen) {
                var selected = subScreen.getSelected();
                layers.add(current);
                current = selected;
            } else if (current instanceof ContentDelegateWidget delegate
                    && delegate.getDelegate() instanceof DrawableWidget draw) {
                layers.add(current);
                current = draw;
            } else {
                break;
            }
        }
        if (current != null) {
            layers.add(current);
        }
        return layers;
    }

    public static boolean isInputWidget(DrawableWidget widget) {
        if (widget instanceof ContentDelegateWidget<?> content && (content.getDelegate() instanceof TextFieldAccess)) {
            return true;
        } else if (widget instanceof ExecutableWidget exe && exe.getHandler() instanceof TextFieldElement) {
            return true;
        }
        return false;
    }

    public record ConfigScreenLayout(
            int indexWidth, int blankWidth, int buttonWidth, int buttonHeight, int buttonBlank) {
        public int totalWidth() {
            return indexWidth + blankWidth + buttonWidth;
        }
    }

    public record ConfigScreenPalette(
            ColorSampler titleTextColor,
            ColorSampler titleBackgroundColor,
            ColorSampler keyTextColor,
            ColorSampler keyBackgroundColor) {}

    public static <T> KeyValueInputWidget<T> createKeyValueWidget(
            Ref<T> ref, String keyName, ConfigScreenLayout layout, ConfigScreenPalette palette) {
        return createKeyValueWidget(
                ref.hasDefaultValue() ? Optional.of(ref.getDefaultValue()) : Optional.empty(),
                ref.createKeyValue(keyName),
                layout,
                palette);
    }

    public static <T> KeyValueInputWidget<T> createKeyValueWidget(
            Optional<T> ref, AttrKeyValue<T> attrKeyValue, ConfigScreenLayout layout, ConfigScreenPalette palette) {
        return new DefaultedKeyValueInputWidget<T>(
                0,
                layout.buttonBlank(),
                layout.totalWidth(),
                layout.buttonHeight(),
                layout.indexWidth(),
                layout.blankWidth(),
                layout.buttonWidth(),
                ref,
                attrKeyValue) {
            @Override
            public DrawableWidget createKeyLabel() {
                return ExecutableWidget.instance(0, layout.buttonBlank(), layout.indexWidth(), layout.buttonHeight())
                        .setElementHandler(new ColorLabelTextElement(
                                        TextProvider.of(this.getTranslationName()),
                                        () -> palette.keyTextColor().getColorInt(),
                                        () -> palette.keyBackgroundColor().getColorInt())
                                .withTooltips(TooltipHandler.of(this::getTooltips)));
            }
        };
    }

    public static DrawableWidget createMutableRecordEditScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            MutableRecord configs,
            Function<String, String> translationKeyFunction,
            ConfigScreenLayout layout,
            ConfigScreenPalette palette) {
        List<Pair<String, ValueAccessor<?>>> accessors = new ArrayList<>();
        for (var re : configs.getComponents()) {
            String key = re.getFirst();
            ValueAccessor<?> access = ValueAccessor.of(() -> configs.get(key), (v) -> configs.set(key, v));
            String translationKey = translationKeyFunction.apply(key);
            accessors.add(Pair.of(translationKey, access));
        }
        return createValueAccessorsEditScreen(title, titleTooltips, accessors, layout, palette);
    }

    public static DrawableWidget createValueAccessorsEditScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<Pair<String, ValueAccessor<?>>> accessors,
            ConfigScreenLayout layout,
            ConfigScreenPalette palette) {
        return createValueAccessorsEditScreen(title, titleTooltips, accessors, layout, palette, false);
    }

    public static DrawableWidget createValueAccessorsEditScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<Pair<String, ValueAccessor<?>>> accessors,
            ConfigScreenLayout layout,
            ConfigScreenPalette palette,
            boolean filter) {
        int width = layout.totalWidth();
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, layout.buttonHeight())
                .setElementHandler(new ColorLabelTextElement(
                                TextProvider.of(title),
                                () -> palette.titleTextColor().getColorInt(),
                                () -> palette.titleBackgroundColor().getColorInt())
                        .withTooltips(TooltipHandler.of(titleTooltips))));
        List<Pair<String, MutableBoolean>> showFlag = new ArrayList<>();
        if (filter) {
            // add filter,
            ValueAccessor<String> filterInput = ValueAccessor.holder("");
            DrawableWidget widget = FilterService.createFilter(
                    filterInput,
                    (str) -> {
                        if (str == null || str.isEmpty()) {
                            for (var re : showFlag) {
                                re.getSecond().setValue(true);
                            }
                        } else {
                            for (var re : showFlag) {
                                String realString = ChatUtils.parseTranslation(re.getFirst());
                                if (FilterService.nameMatch(realString, str)) {
                                    re.getSecond().setValue(true);
                                } else {
                                    re.getSecond().setValue(false);
                                }
                            }
                        }
                    },
                    0,
                    layout.buttonBlank(),
                    width,
                    layout.buttonHeight());
            listWidget.addDrawableChild(widget);
        }
        for (var configWidget : accessors) {
            ValueAccessor access = configWidget.getSecond();
            var re = access.getValue();
            Ref tempRef = Refs.wrapInstance(re);
            String key = configWidget.getFirst();
            SubScreenWidget keyValueRow =
                    new SubScreenWidget(0, 0, width, layout.buttonHeight() + layout.buttonBlank());
            keyValueRow.addDrawableChild(
                    DisplayWidget.instance(0, 0, width, layout.buttonBlank() + layout.buttonHeight()));
            var keyValue = tempRef.createKeyValue(key);
            keyValue.setUpdater(access::getValue);
            keyValue.addListener(access::setValue);
            keyValueRow.addDrawableChild(
                    createKeyValueWidget(Optional.of(access.getValue()), keyValue, layout, palette));
            MutableBoolean bl = new MutableBoolean(true);
            showFlag.add(Pair.of(key, bl));
            DynamicContentWidget<?> contentWidget =
                    new DynamicContentWidget<>(() -> bl.getValue() ? keyValueRow : null, 0, 0);
            listWidget.addDrawableChild(contentWidget);
        }

        return listWidget;
    }

    public static DrawableWidget createCenterScreenWidget(DrawableWidget widget, int totalX, int totalY) {
        ValueAccessor<Integer> overrideYAcc = ValueAccessor.holder(0);
        ValueAccessor<Boolean> yLock = ValueAccessor.holder(false);
        SubScreenWidget subScreenWidget = new SubScreenWidget(0, 0, totalX, totalY);
        var re =
                new DynamicSubScreenWidget(
                        ValueAccessor.ofIgnore(() -> {
                            return ((totalX - widget.getWidth()) / 2) - widget.getX();
                        }),
                        ValueAccessor.of(
                                () -> {
                                    int y = ((totalY - widget.getHeight()) / 2) - widget.getY();
                                    if (y < 0) {
                                        yLock.setValue(false);
                                        return overrideYAcc.getValue();
                                    } else {
                                        yLock.setValue(true);
                                        overrideYAcc.setValue(y);
                                        return y;
                                    }
                                },
                                (y) -> {
                                    if (!yLock.getValue()) {
                                        overrideYAcc.setValue(Math.min(y, 0));
                                        ;
                                    }
                                })) {
                    @Override
                    public void render0(
                            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
                        context.enableScissor(0, 0, totalX, totalY);
                        super.render0(context, mouseX, mouseY, delta, disableSelect);
                        context.disableScissor();
                    }
                };
        re.addDrawableChild(widget);
        subScreenWidget.addDrawableChild(re);
        ExecutableWidget scroll = ExecutableWidget.instance(0, 0, 12, totalY)
                .setElementHandler(new AdvancedScrollElement(
                        ValueAccessor.ofIgnore(totalY),
                        ValueAccessor.ofIgnore(() -> widget.getY() + widget.getHeight()),
                        ValueAccessor.of(
                                () -> {
                                    int currentTotalY = widget.getY() + widget.getHeight() - totalY;
                                    int pos = -overrideYAcc.getValue();
                                    return Math.clamp(((double) pos / currentTotalY), 0, 1);
                                },
                                (p) -> {
                                    int currentTotalY = widget.getY() + widget.getHeight() - totalY;
                                    int pos = (int) (currentTotalY * p);
                                    overrideYAcc.setValue(-pos);
                                })));
        DrawableWidget scrollableWidget = new DynamicContentWidget<>(
                () -> {
                    if (widget.getY() + widget.getHeight() > 1.14514 * totalY) {
                        return scroll;
                    } else {
                        return null;
                    }
                },
                ValueAccessor.ofIgnore(() -> {
                    return ((totalX + widget.getWidth()) / 2) - widget.getX();
                }),
                ValueAccessor.ofIgnore(0));
        subScreenWidget.addDrawableChild(scrollableWidget);
        return subScreenWidget;
    }

    public static InputHandler createGridPosSelectInputHandler(IntConsumer xAccessor, IntConsumer yAccessor) {
        return new InputHandler() {
            @Override
            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                boolean val = false;
                if (mouseX >= element.getX() && mouseX <= element.getX() + element.getWidth()) {
                    xAccessor.accept((int) mouseX - element.getX());
                    val = true;
                }
                if (mouseY >= element.getY() && mouseY <= element.getY() + element.getHeight()) {
                    yAccessor.accept((int) mouseY - element.getY());
                    val = true;
                }
                return val;
            }

            @Override
            public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                if (type == Type.MOUSE_START_DRAG) {
                    return element.isMouseOver(mouseX, mouseY);
                }
                if (type == Type.MOUSE_DRAG) {
                    return onClick(element, mouseX, mouseY, button);
                }
                return InputHandler.super.onAction(element, mouseX, mouseY, button, type);
            }
        };
    }

    public static <T> DrawableWidget createOpenListModifyScreenButton(
            ListAttrKeyValue<T> s, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.run(() -> {
                            ScreenAccess.of(new StringListModifyScreen<>(s, listAttrKeyValue -> {
                                        s.setOriginValue(listAttrKeyValue.getOriginValue());
                                    }))
                                    .openFromCurrent();
                        }))
                        .withTooltips(TooltipHandler.of(Constants.openListEditTooltips())));
    }

    public static <T> DrawableWidget createOpenListModifyScreenButton(
            Supplier<ListAttrKeyValue<T>> attrCreator, Consumer<List<T>> listConsumer, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.run(() -> {
                            var s = attrCreator.get();
                            ScreenAccess.of(new StringListModifyScreen<>(s, listAttrKeyValue -> {
                                        listConsumer.accept(listAttrKeyValue.getOriginValue());
                                    }))
                                    .openFromCurrent();
                        }))
                        .withTooltips(TooltipHandler.of(Constants.openListEditTooltips())));
    }

    public static DrawableWidget withCondition(DrawableWidget widget, BooleanSupplier supplier) {
        return new DynamicContentWidget<>(() -> (supplier.getAsBoolean() ? widget : null), 0, 0);
    }
}
