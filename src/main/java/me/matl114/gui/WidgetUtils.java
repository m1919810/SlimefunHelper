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
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.interfaces.InputElement;
import me.matl114.gui.presets.lists.StringListModifyScreen;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.Refs;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.collections.MutableRecord;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.kv.ListAttrKeyValue;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class WidgetUtils {
    private static final int COLLAPSIBLE_ARROW_SIZE = 8;

    public static final ConfigScreenLayout DEFAULT_CONFIG_SCREEN_LAYOUT = new ConfigScreenLayout(140, 10, 180, 18, 2);

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
        }
        if (widget instanceof ExecutableWidget exe && exe.getHandler() instanceof InputElement input) {
            return input.isPreparingInput();
        }
        return widget instanceof InputElement input && input.isPreparingInput();
    }

    public record ConfigScreenLayout(
            int indexWidth, int blankWidth, int buttonWidth, int buttonHeight, int buttonBlank) {
        public int totalWidth() {
            return indexWidth + blankWidth + buttonWidth;
        }

        public ConfigScreenLayout forWidth(int width) {
            int availableWidth = Math.max(1, width - blankWidth);
            int adjustedIndexWidth = Math.min(indexWidth, Math.max(1, (availableWidth * 2) / 3));
            return new ConfigScreenLayout(
                    adjustedIndexWidth,
                    blankWidth,
                    Math.max(1, width - adjustedIndexWidth - blankWidth),
                    buttonHeight,
                    buttonBlank);
        }
    }

    public static void addConditionalWidgets(DynamicListWidget list, List<ConditionalWidget> widgets) {
        for (var conditional : widgets) {
            list.addDrawableChild(
                    new DynamicContentWidget<>(() -> conditional.shouldShow() ? conditional.widget() : null, 0, 0));
        }
    }

    public static void addCollapsibleGroup(
            DynamicListWidget list,
            DrawableWidget header,
            ValueAccessor<Boolean> expanded,
            List<ConditionalWidget> widgets,
            ElementFactory factory,
            int arrowX,
            int arrowY,
            int arrowWidth,
            int arrowHeight) {
        int headerX = header.x();
        int headerWidth = list.width();
        boolean hasAlwaysVisibleWidget =
                widgets.stream().anyMatch(widget -> widget.condition() == ConditionalWidget.ALWAYS_TRUE);

        SubScreenWidget headerSubScreen = new SubScreenWidget(headerX, 0, headerWidth, arrowY + arrowHeight);
        headerSubScreen.addDrawableChild(ExecutableWidget.instance(0, 0, headerWidth, arrowY + arrowHeight)
                .setElementHandler(new AbstractElement()
                        .withInputHandler(InputHandler.clickRun(() -> expanded.setValue(!expanded.getValue())))));
        headerSubScreen.addDrawableChild(header);
        int arrowSize = Math.min(COLLAPSIBLE_ARROW_SIZE, Math.min(arrowWidth, arrowHeight));
        int arrowOffsetX = (arrowWidth - arrowSize) / 2;
        int arrowOffsetY = (arrowHeight - arrowSize) / 2;
        headerSubScreen.addDrawableChild(ExecutableWidget.instance(arrowX, arrowY, arrowWidth, arrowHeight)
                .setRenderHandler(new AbstractElement()
                        .combineRender(factory.createLabel(TextProvider.of(Text.empty()), TooltipHandler.of(List.of())))
                        .combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.setShaderColor(Colors.WHITE);
                            context.drawGuiTexture(
                                    expanded.getValue()
                                            ? Constants.EXPAND_GUI_ON_SPRITE
                                            : Constants.EXPAND_GUI_OFF_SPRITE,
                                    arrowOffsetX,
                                    arrowOffsetY,
                                    0,
                                    arrowSize,
                                    arrowSize);
                            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        })));
        list.addDrawableChild(new DynamicContentWidget<>(
                () -> hasAlwaysVisibleWidget || widgets.stream().anyMatch(ConditionalWidget::shouldShow)
                        ? headerSubScreen
                        : null,
                0,
                0));
        for (var conditional : widgets) {
            list.addDrawableChild(new DynamicContentWidget<>(
                    () -> expanded.getValue() && conditional.shouldShow() ? conditional.widget() : null, 0, 0));
        }
    }

    public static DynamicListWidget createValueAccessorList(
            List<Pair<String, ValueAccessor<?>>> accessors, ConfigScreenLayout layout, ElementFactory factory) {
        int width = layout.totalWidth();
        DynamicListWidget list = new DynamicListWidget(0, 0, width);
        for (var accessorEntry : accessors) {
            ValueAccessor accessor = accessorEntry.getSecond();
            Ref tempRef = Refs.wrapInstance(accessor.getValue());
            String key = accessorEntry.getFirst();
            BaseAttrKeyValue keyValue = tempRef.createKeyValue(key);
            keyValue.setUpdater(accessor::getValue);
            keyValue.addListener(accessor::setValue);
            SubScreenWidget row = new SubScreenWidget(0, 0, width, layout.buttonHeight() + layout.buttonBlank());
            row.addDrawableChild(DisplayWidget.instance(0, 0, width, layout.buttonBlank() + layout.buttonHeight()));
            row.addDrawableChild(
                    createKeyValueWidget(Optional.ofNullable(accessor.getValue()), keyValue, layout, factory));
            list.addDrawableChild(row);
        }
        return list;
    }

    public static <T> KeyValueInputWidget<T> createKeyValueWidget(
            Ref<T> ref, String keyName, ConfigScreenLayout layout, ElementFactory factory) {
        return createKeyValueWidget(
                ref.hasDefaultValue() ? Optional.of(ref.getDefaultValue()) : Optional.empty(),
                ref.createKeyValue(keyName),
                layout,
                factory);
    }

    public static <T> KeyValueInputWidget<T> createKeyValueWidget(
            Optional<T> ref, AttrKeyValue<T> attrKeyValue, ConfigScreenLayout layout, ElementFactory factory) {
        return new DefaultedKeyValueInputWidget<T>(
                0,
                layout.buttonBlank(),
                layout.totalWidth(),
                layout.buttonHeight(),
                layout.indexWidth(),
                layout.blankWidth(),
                layout.buttonWidth(),
                ref,
                attrKeyValue,
                factory) {
            @Override
            public DrawableWidget createKeyLabel() {
                return ExecutableWidget.instance(0, 0, layout.indexWidth(), layout.buttonHeight())
                        .setElementHandler(factory.createLabel(
                                TextProvider.of(this.getTranslationName()), TooltipHandler.of(this::getTooltips)));
            }
        };
    }

    public static DrawableWidget createMutableRecordEditScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            MutableRecord configs,
            Function<String, String> translationKeyFunction,
            ConfigScreenLayout layout,
            ElementFactory factory) {
        List<Pair<String, ValueAccessor<?>>> accessors = new ArrayList<>();
        for (var re : configs.getComponents()) {
            String key = re.getFirst();
            ValueAccessor<?> access = ValueAccessor.of(() -> configs.get(key), (v) -> configs.set(key, v));
            String translationKey = translationKeyFunction.apply(key);
            accessors.add(Pair.of(translationKey, access));
        }
        return createValueAccessorsEditScreen(title, titleTooltips, accessors, layout, factory, false);
    }

    public static DrawableWidget createValueAccessorsEditScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<Pair<String, ValueAccessor<?>>> accessors,
            ConfigScreenLayout layout,
            ElementFactory factory) {
        return createValueAccessorsEditScreen(title, titleTooltips, accessors, layout, factory, false);
    }

    public static DrawableWidget createValueAccessorsEditScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<Pair<String, ValueAccessor<?>>> accessors,
            ConfigScreenLayout layout,
            ElementFactory factory,
            boolean filter) {
        int width = layout.totalWidth();
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, layout.buttonHeight())
                .setElementHandler(factory.createTitle(TextProvider.of(title), TooltipHandler.of(titleTooltips))));
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
                    createKeyValueWidget(Optional.of(access.getValue()), keyValue, layout, factory));
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
                            return ((totalX - widget.width()) / 2) - widget.x();
                        }),
                        ValueAccessor.of(
                                () -> {
                                    int y = ((totalY - widget.height()) / 2) - widget.y();
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
                        ValueAccessor.ofIgnore(() -> widget.y() + widget.height()),
                        ValueAccessor.of(
                                () -> {
                                    int currentTotalY = widget.y() + widget.height() - totalY;
                                    int pos = -overrideYAcc.getValue();
                                    return Math.clamp(((double) pos / currentTotalY), 0, 1);
                                },
                                (p) -> {
                                    int currentTotalY = widget.y() + widget.height() - totalY;
                                    int pos = (int) (currentTotalY * p);
                                    overrideYAcc.setValue(-pos);
                                })));
        DrawableWidget scrollableWidget = new DynamicContentWidget<>(
                () -> {
                    if (widget.y() + widget.height() > 1.14514 * totalY) {
                        return scroll;
                    } else {
                        return null;
                    }
                },
                ValueAccessor.ofIgnore(() -> {
                    return ((totalX + widget.width()) / 2) - widget.x();
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
                if (mouseX >= element.x() && mouseX <= element.x() + element.width()) {
                    xAccessor.accept((int) mouseX - element.x());
                    val = true;
                }
                if (mouseY >= element.y() && mouseY <= element.y() + element.height()) {
                    yAccessor.accept((int) mouseY - element.y());
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
            ElementFactory factory, ListAttrKeyValue<T> s, int x, int y, int dx, int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.run(() -> {
                            ScreenAccess.of(new StringListModifyScreen<>(
                                            s,
                                            listAttrKeyValue -> {
                                                s.setOriginValue(listAttrKeyValue.get());
                                            },
                                            factory))
                                    .openFromCurrent();
                        }))
                        .withTooltips(TooltipHandler.of(Constants.openListEditTooltips())));
    }

    public static <T> DrawableWidget createOpenListModifyScreenButton(
            ElementFactory factory,
            Supplier<ListAttrKeyValue<T>> attrCreator,
            Consumer<List<T>> listConsumer,
            int x,
            int y,
            int dx,
            int dy) {
        return ExecutableWidget.instance(x, y, dx, dy)
                .setElementHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.run(() -> {
                            var s = attrCreator.get();
                            ScreenAccess.of(new StringListModifyScreen<>(
                                            s,
                                            listAttrKeyValue -> {
                                                listConsumer.accept(listAttrKeyValue.get());
                                            },
                                            factory))
                                    .openFromCurrent();
                        }))
                        .withTooltips(TooltipHandler.of(Constants.openListEditTooltips())));
    }

    public static DrawableWidget withCondition(DrawableWidget widget, BooleanSupplier supplier) {
        return new DynamicContentWidget<>(() -> (supplier.getAsBoolean() ? widget : null), 0, 0);
    }
}
