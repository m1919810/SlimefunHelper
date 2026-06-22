package me.matl114.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.RefKeyValueInputWidget;
import me.matl114.gui.elements.ColorLabelTextElement;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.hacks.api.BaseModule;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class WidgetUtils {
    public static final ConfigScreenLayout DEFAULT_CONFIG_SCREEN_LAYOUT = new ConfigScreenLayout(140, 10, 180, 18, 2);

    public record ConfigScreenLayout(
            int indexWidth, int blankWidth, int buttonWidth, int buttonHeight, int buttonBlank) {
        public int totalWidth() {
            return indexWidth + blankWidth + buttonWidth;
        }
    }

    public record ConfigScreenPalette(
            Supplier<Integer> titleTextColor,
            Supplier<Integer> titleBackgroundColor,
            Supplier<Integer> keyTextColor,
            Supplier<Integer> keyBackgroundColor) {}

    public static Screen createConfigScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<BaseModule.WrapperConfigRef<?>> configs,
            Consumer<Consumer<DrawableWidget>> customWidgets,
            ConfigScreenLayout layout,
            ConfigScreenPalette palette) {
        int width = layout.totalWidth();
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, layout.buttonHeight())
                .setElementHandler(new ColorLabelTextElement(
                                TextProvider.of(title),
                                () -> palette.titleTextColor().get(),
                                () -> palette.titleBackgroundColor().get())
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

        if (customWidgets != null) {
            customWidgets.accept(listWidget::addDrawableChild);
        }
        return new CenterScreen(listWidget);
    }

    public static Screen createModuleConfigScreen(
            BaseModule baseModule, Text title, Supplier<List<Text>> titleTooltips, ConfigScreenPalette palette) {
        return createConfigScreen(
                title,
                titleTooltips,
                baseModule.getEditableConfig(),
                baseModule::addCustomWidgets,
                DEFAULT_CONFIG_SCREEN_LAYOUT,
                palette);
    }

    public static void openModuleConfigScreen(
            BaseModule baseModule, Text title, Supplier<List<Text>> titleTooltips, ConfigScreenPalette palette) {
        ScreenAccess.of(createModuleConfigScreen(baseModule, title, titleTooltips, palette))
                .openFromCurrent();
    }

    private static SubScreenWidget createKeyValueWidget(
            BaseModule.WrapperConfigRef<?> wrapper, ConfigScreenLayout layout, ConfigScreenPalette palette) {
        return new RefKeyValueInputWidget(
                0,
                layout.buttonBlank(),
                layout.totalWidth(),
                layout.buttonHeight(),
                layout.indexWidth(),
                layout.blankWidth(),
                layout.buttonWidth(),
                wrapper.ref(),
                wrapper.keyName()) {
            @Override
            public DrawableWidget createKeyLabel() {
                return ExecutableWidget.instance(0, layout.buttonBlank(), layout.indexWidth(), layout.buttonHeight())
                        .setElementHandler(new ColorLabelTextElement(
                                        TextProvider.of(this.getTranslationName()),
                                        () -> palette.keyTextColor().get(),
                                        () -> palette.keyBackgroundColor().get())
                                .withTooltips(TooltipHandler.of(this::getTooltips)));
            }
        };
    }
}
