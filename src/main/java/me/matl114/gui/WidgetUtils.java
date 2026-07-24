package me.matl114.gui;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.RefKeyValueInputWidget;
import me.matl114.gui.elements.ColorLabelTextElement;
import me.matl114.gui.elements.TextFieldElement;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.Refs;
import me.matl114.utils.collections.MutableRecord;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class WidgetUtils {
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

    public static boolean isInputWidget(DrawableWidget widget) {
        if (widget instanceof ContentDelegateWidget<?> content
                && content.getDelegate() instanceof TextFieldWidget textField) {
            return true;
        } else if (widget instanceof ExecutableWidget exe && exe.getHandler() instanceof TextFieldElement textField) {
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

    public static DynamicListWidget createConfigScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<BaseModule.WrapperConfigRef<?>> configs,
            ConfigScreenLayout layout,
            ConfigScreenPalette palette) {
        int width = layout.totalWidth();
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, layout.buttonHeight())
                .setElementHandler(new ColorLabelTextElement(
                                TextProvider.of(title),
                                () -> palette.titleTextColor().getColorInt(),
                                () -> palette.titleBackgroundColor().getColorInt())
                        .withTooltips(TooltipHandler.of(titleTooltips))));

        for (var configWidget : configs) {
            SubScreenWidget keyValueRow =
                    new SubScreenWidget(0, 0, width, layout.buttonHeight() + layout.buttonBlank());
            keyValueRow.addDrawableChild(
                    DisplayWidget.instance(0, 0, width, layout.buttonBlank() + layout.buttonHeight()));
            keyValueRow.addDrawableChild(createKeyValueWidget(configWidget, layout, palette));
            DynamicContentWidget<?> contentWidget = new DynamicContentWidget<>(
                    () -> configWidget.showPredicate().getAsBoolean() ? keyValueRow : null, 0, 0);
            listWidget.addDrawableChild(contentWidget);
        }

        return listWidget;
    }

    public static DynamicListWidget createModuleConfigScreen(
            BaseModule baseModule, Text title, Supplier<List<Text>> titleTooltips, ConfigScreenPalette palette) {
        return createConfigScreen(
                title, titleTooltips, baseModule.getEditableConfig(), DEFAULT_CONFIG_SCREEN_LAYOUT, palette);
    }

    public static void openModuleConfigScreen(
            BaseModule baseModule, Text title, Supplier<List<Text>> titleTooltips, ConfigScreenPalette palette) {
        ScreenAccess.of(new CenterScreen(createModuleConfigScreen(baseModule, title, titleTooltips, palette)))
                .openFromCurrent();
    }

    private static SubScreenWidget createKeyValueWidget(
            BaseModule.WrapperConfigRef<?> wrapper, ConfigScreenLayout layout, ConfigScreenPalette palette) {
        return createKeyValueWidget(wrapper.ref(), wrapper.keyName(), layout, palette);
    }

    private static SubScreenWidget createKeyValueWidget(
            Ref<?> ref, String keyName, ConfigScreenLayout layout, ConfigScreenPalette palette) {
        return new RefKeyValueInputWidget(
                0,
                layout.buttonBlank(),
                layout.totalWidth(),
                layout.buttonHeight(),
                layout.indexWidth(),
                layout.blankWidth(),
                layout.buttonWidth(),
                ref,
                keyName) {
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
        int width = layout.totalWidth();
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, layout.buttonHeight())
                .setElementHandler(new ColorLabelTextElement(
                                TextProvider.of(title),
                                () -> palette.titleTextColor().getColorInt(),
                                () -> palette.titleBackgroundColor().getColorInt())
                        .withTooltips(TooltipHandler.of(titleTooltips))));

        for (var configWidget : configs.getComponents()) {
            Ref tempRef = Refs.wrapInstance(configWidget.getSecond());
            String key = configWidget.getFirst();
            tempRef.setDefaultValue(configWidget.getSecond());
            tempRef.addUpdateListener(s -> configs.set(key, s));
            SubScreenWidget keyValueRow =
                    new SubScreenWidget(0, 0, width, layout.buttonHeight() + layout.buttonBlank());
            keyValueRow.addDrawableChild(
                    DisplayWidget.instance(0, 0, width, layout.buttonBlank() + layout.buttonHeight()));
            keyValueRow.addDrawableChild(
                    createKeyValueWidget(tempRef, translationKeyFunction.apply(key), layout, palette));
            DynamicContentWidget<?> contentWidget = new DynamicContentWidget<>(() -> keyValueRow, 0, 0);
            listWidget.addDrawableChild(contentWidget);
        }

        return listWidget;
    }

    public static DynamicSubScreenWidget createCenterScreenWidget(DrawableWidget widget, int totalX, int totalY) {
        ValueAccessor<Integer> overrideYAcc = ValueAccessor.holder(0);
        ValueAccessor<Boolean> yLock = ValueAccessor.holder(false);
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
        return re;
    }
}
