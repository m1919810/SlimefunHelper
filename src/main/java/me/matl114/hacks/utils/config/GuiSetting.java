package me.matl114.hacks.utils.config;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.With;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.ConditionalWidget;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicListWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.interfaces.ElementType;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.hacks.utils.gui.*;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;

@With
public record GuiSetting(
        TextConfig textConfig,
        CustomGuiConfig customConfig,
        AlignConfig alignConfig,
        GuiStyle backgroundStyle,
        GuiStyle titleStyle,
        GuiStyle labelStyle,
        GuiStyle buttonStyle,
        GuiStyle inputStyle,
        GuiStyle sliderStyle,
        boolean enableSlider)
        implements NBTParsable<GuiSetting>, GuiColorSamplier {

    public static final GuiSetting DEFAULT = new GuiSetting(
            TextConfig.DEFAULT,
            CustomGuiConfig.DEFAULT,
            AlignConfig.DEFAULT,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            true);

    public static final Codec<GuiSetting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    TextConfig.CODEC.optionalFieldOf("text_config", TextConfig.DEFAULT).forGetter(GuiSetting::textConfig),
                    CustomGuiConfig.CODEC
                            .optionalFieldOf("custom_config", CustomGuiConfig.DEFAULT)
                            .forGetter(GuiSetting::customConfig),
                    AlignConfig.CODEC
                            .optionalFieldOf("align_config", AlignConfig.DEFAULT)
                            .forGetter(GuiSetting::alignConfig),
                    CodecUtils.enumCodec(GuiStyle.class)
                            .optionalFieldOf("background_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::backgroundStyle),
                    CodecUtils.enumCodec(GuiStyle.class)
                            .optionalFieldOf("title_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::titleStyle),
                    CodecUtils.enumCodec(GuiStyle.class)
                            .optionalFieldOf("label_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::labelStyle),
                    CodecUtils.enumCodec(GuiStyle.class)
                            .optionalFieldOf("button_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::buttonStyle),
                    CodecUtils.enumCodec(GuiStyle.class)
                            .optionalFieldOf("input_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::inputStyle),
                    CodecUtils.enumCodec(GuiStyle.class)
                            .optionalFieldOf("slider_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::sliderStyle),
                    Codec.BOOL.optionalFieldOf("enable_slider", true).forGetter(GuiSetting::enableSlider))
            .apply(instance, GuiSetting::new));

    public static final NBTType<GuiSetting> TYPE = new NBTType<>(
            "guisetting", CODEC, GuiSetting::createWidget, DEFAULT);

    @Override
    public NBTType<GuiSetting> type() {
        return TYPE;
    }

    public GuiStyle style(ElementType type) {
        return switch (type) {
            case BACKGROUND -> backgroundStyle;
            case TITLE -> titleStyle;
            case LABEL -> labelStyle;
            case BUTTON -> buttonStyle;
            case INPUT -> inputStyle;
            case SLIDER -> sliderStyle;
        };
    }

    @Override
    public WrapRGBA frameColor() {
        return customConfig.frameColor();
    }

    @Override
    public WrapRGBA backgroundColor() {
        return customConfig.backgroundColor();
    }

    @Override
    public WrapRGBA plateColor() {
        return customConfig.plateColor();
    }

    @Override
    public WrapRGBA titleColor() {
        return customConfig.titleColor();
    }

    @Override
    public WrapRGBA labelColor() {
        return customConfig.labelColor();
    }

    @Override
    public WrapRGBA buttonActiveColor() {
        return customConfig.buttonActiveColor();
    }

    @Override
    public WrapRGBA buttonInactiveColor() {
        return customConfig.buttonInactiveColor();
    }

    @Override
    public WrapRGBA textColor() {
        return customConfig.textColor();
    }

    @Override
    public WrapRGBA textBackground() {
        return customConfig.textBackground();
    }

    @Override
    public WrapRGBA sliderColor() {
        return customConfig.sliderColor();
    }

    @Override
    public AlignType textAlign() {
        return textConfig.textAlign();
    }

    @Override
    public AlignType buttonTextAlign() {
        return alignConfig.buttonTextAlign();
    }

    @Override
    public AlignType inputTextAlign() {
        return alignConfig.inputTextAlign();
    }

    @Override
    public AlignType sliderTextAlign() {
        return alignConfig.sliderTextAlign();
    }

    private GuiSetting withStyle(ElementType type, GuiStyle value) {
        return switch (type) {
            case BACKGROUND -> withBackgroundStyle(value);
            case TITLE -> withTitleStyle(value);
            case LABEL -> withLabelStyle(value);
            case BUTTON -> withButtonStyle(value);
            case INPUT -> withInputStyle(value);
            case SLIDER -> withSliderStyle(value);
        };
    }


    private static <T> ValueAccessor<T> accessor(
            AttrKeyValue<GuiSetting> source, Supplier<T> getter, Function<T, GuiSetting> updater) {
        return ValueAccessor.of(getter, value -> source.setOriginValue(updater.apply(value)));
    }

    private static <T> ConditionalWidget field(
            String key,
            ValueAccessor<T> accessor,
            BooleanSupplier condition,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory) {
        DrawableWidget widget = WidgetUtils.createValueAccessorList(
                        List.of(Pair.of(key, accessor)), layout, factory);
        return new ConditionalWidget(condition, widget);
    }

    private static DrawableWidget createWidget(
            ElementFactory factory, AttrKeyValue<GuiSetting> source, int x, int y, int dx, int dy) {
        int iconWidth = Math.min(dx, dy);
        int buttonWidth = dx - iconWidth;
        ButtonAction openEditor = ButtonAction.run(() -> openEditorScreen(factory, source));
        SubScreenWidget widget = new SubScreenWidget(x, y, dx, dy);
        widget.addDrawableChild(factory.createExecuteButton(
                () -> Text.translatable("widget.nbt-parsable.gui-config.open-edit-screen"),
                () -> ChatUtils.parseTooltipsTranslation(
                        "widget.nbt-parsable.gui-config.open-edit-screen.tooltips", ""),
                openEditor,
                0,
                0,
                buttonWidth,
                dy));
        if (iconWidth > 0) {
            widget.addDrawableChild(factory.createElement(
                            IconElement.fixedGui(Constants.EDITOR_SPRITE, openEditor),
                            null,
                            null,
                            buttonWidth,
                            0,
                            iconWidth,
                            dy));
        }
        return widget;
    }

    private static void openEditorScreen(ElementFactory factory, AttrKeyValue<GuiSetting> source) {
        int width = WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT.totalWidth();
        DrawableWidget widget = createEditorWidget(factory, source, 0, 0, width, 0);
        CenterScreen screen = new CenterScreen(widget);
        ScreenAccess.of(screen).addCloseFuture(Config::configSaveTasks);
        ScreenAccess.of(screen).openFromCurrent();
    }

    private static DrawableWidget createEditorWidget(
            ElementFactory factory, AttrKeyValue<GuiSetting> source, int x, int y, int dx, int dy) {
        WidgetUtils.ConfigScreenLayout layout = WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT.forWidth(dx);
        DynamicListWidget list = new DynamicListWidget(x, y, Math.max(1, dx));

        ValueAccessor<GuiStyle> backgroundStyle = accessor(source, () -> source.get().backgroundStyle(),
                value -> source.get().withStyle(ElementType.BACKGROUND, value));
        ValueAccessor<GuiStyle> titleStyle = accessor(source, () -> source.get().titleStyle(),
                value -> source.get().withStyle(ElementType.TITLE, value));
        ValueAccessor<GuiStyle> labelStyle = accessor(source, () -> source.get().labelStyle(),
                value -> source.get().withStyle(ElementType.LABEL, value));
        ValueAccessor<GuiStyle> buttonStyle = accessor(source, () -> source.get().buttonStyle(),
                value -> source.get().withStyle(ElementType.BUTTON, value));
        ValueAccessor<GuiStyle> inputStyle = accessor(source, () -> source.get().inputStyle(),
                value -> source.get().withStyle(ElementType.INPUT, value));
        ValueAccessor<GuiStyle> sliderStyle = accessor(source, () -> source.get().sliderStyle(),
                value -> source.get().withStyle(ElementType.SLIDER, value));
        ValueAccessor<Boolean> enableSlider = accessor(
                source, () -> source.get().enableSlider(), value -> source.get().withEnableSlider(value));

        list.addDrawableChild(factory.createTitle(
                () -> Text.translatable("widget.nbt-parsable.gui-config.edit-screen.title"),
                List::of,
                0,
                0,
                list.getWidth(),
                layout.buttonHeight()));

        addGroup(list, "text-config", List.of(field(
                        "widget.gui-settings.text-config.color",
                        colorAccessor(source, () -> source.get().textConfig().color(), value -> source.get()
                                .withTextConfig(source.get().textConfig().withColor(value))),
                        () -> true,
                        layout,
                        factory),
                field("widget.gui-settings.text-config.text_align",
                        alignAccessor(source, () -> source.get().textConfig().textAlign(), value -> source.get()
                                .withTextConfig(source.get().textConfig().withTextAlign(value))),
                        () -> true,
                        layout,
                        factory)), layout, factory);
        addGroup(list, "background", List.of(
                field("widget.gui-settings.background.background_style", backgroundStyle, () -> true, layout, factory),
                field("widget.gui-settings.background.background_color", colorAccessor(source, () -> source.get().customConfig().backgroundColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withBackgroundColor(value))),
                        () -> backgroundStyle.getValue() == GuiStyle.CUSTOM, layout, factory)),
                layout, factory);
        addGroup(list, "title", List.of(
                field("widget.gui-settings.title.title_style", titleStyle, () -> true, layout, factory),
                field("widget.gui-settings.title.title_color", colorAccessor(source, () -> source.get().customConfig().titleColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withTitleColor(value))),
                        () -> titleStyle.getValue() == GuiStyle.CUSTOM, layout, factory)),
                layout, factory);
        addGroup(list, "label", List.of(
                field("widget.gui-settings.label.label_style", labelStyle, () -> true, layout, factory),
                field("widget.gui-settings.label.label_color", colorAccessor(source, () -> source.get().customConfig().labelColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withLabelColor(value))),
                        () -> labelStyle.getValue() == GuiStyle.CUSTOM, layout, factory)),
                layout, factory);
        addGroup(list, "button", List.of(
                field("widget.gui-settings.button.button_style", buttonStyle, () -> true, layout, factory),
                field("widget.gui-settings.button.frame_color", colorAccessor(source, () -> source.get().customConfig().frameColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withFrameColor(value))),
                        () -> buttonStyle.getValue() == GuiStyle.CUSTOM, layout, factory),
                field("widget.gui-settings.button.button_active_color", colorAccessor(source, () -> source.get().customConfig().buttonActiveColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withButtonActiveColor(value))),
                        () -> buttonStyle.getValue() == GuiStyle.CUSTOM, layout, factory),
                field("widget.gui-settings.button.button_inactive_color", colorAccessor(source, () -> source.get().customConfig().buttonInactiveColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withButtonInactiveColor(value))),
                        () -> buttonStyle.getValue() == GuiStyle.CUSTOM, layout, factory),
                field("widget.gui-settings.button.button_text_align", alignAccessor(source, () -> source.get().alignConfig().buttonTextAlign(),
                        value -> source.get().withAlignConfig(source.get().alignConfig().withButtonTextAlign(value))),
                        () -> buttonStyle.getValue() == GuiStyle.CUSTOM, layout, factory)),
                layout, factory);
        addGroup(list, "input", List.of(
                field("widget.gui-settings.input.input_style", inputStyle, () -> true, layout, factory),
                field("widget.gui-settings.input.text_color", colorAccessor(source, () -> source.get().customConfig().textColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withTextColor(value))),
                        () -> inputStyle.getValue() == GuiStyle.CUSTOM, layout, factory),
                field("widget.gui-settings.input.text_background", colorAccessor(source, () -> source.get().customConfig().textBackground(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withTextBackground(value))),
                        () -> inputStyle.getValue() == GuiStyle.CUSTOM, layout, factory),
                field("widget.gui-settings.input.input_text_align", alignAccessor(source, () -> source.get().alignConfig().inputTextAlign(),
                        value -> source.get().withAlignConfig(source.get().alignConfig().withInputTextAlign(value))),
                        () -> inputStyle.getValue() == GuiStyle.CUSTOM, layout, factory)),
                layout, factory);
        addGroup(list, "slider", List.of(
                field("widget.gui-settings.slider.enable_slider", enableSlider, () -> true, layout, factory),
                field("widget.gui-settings.slider.slider_style", sliderStyle, enableSlider::getValue, layout, factory),
                field("widget.gui-settings.slider.slider_color", colorAccessor(source, () -> source.get().customConfig().sliderColor(),
                        value -> source.get().withCustomConfig(source.get().customConfig().withSliderColor(value))),
                        () -> enableSlider.getValue() && sliderStyle.getValue() == GuiStyle.CUSTOM,
                        layout, factory),
                field("widget.gui-settings.slider.slider_text_align", alignAccessor(source, () -> source.get().alignConfig().sliderTextAlign(),
                        value -> source.get().withAlignConfig(source.get().alignConfig().withSliderTextAlign(value))),
                        () -> enableSlider.getValue() && sliderStyle.getValue() == GuiStyle.CUSTOM,
                        layout, factory)),
                layout, factory);

        return list;
    }

    private static ValueAccessor<WrapRGBA> colorAccessor(
            AttrKeyValue<GuiSetting> source, Supplier<WrapRGBA> getter, Function<WrapRGBA, GuiSetting> updater) {
        return accessor(source, getter, updater);
    }

    private static ValueAccessor<AlignType> alignAccessor(
            AttrKeyValue<GuiSetting> source, Supplier<AlignType> getter, Function<AlignType, GuiSetting> updater) {
        return accessor(source, getter, updater);
    }

    private static void addGroup(
            DynamicListWidget list,
            String name,
            List<ConditionalWidget> fields,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory) {
        int height = 12;
        DrawableWidget header = factory.createLabel(
                () -> Text.translatable("widget.gui-settings.group." + name),
                List::of,
                0,
                2,
                list.getWidth() - height,
                height,
                true);
        WidgetUtils.addCollapsibleGroup(
                list,
                header,
                ValueAccessor.holder(true),
                fields,
                factory,
                list.getWidth() - height,
                2,
                height,
                height);
    }
}
