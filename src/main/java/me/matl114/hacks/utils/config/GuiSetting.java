package me.matl114.hacks.utils.config;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.With;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.ConditionalWidget;
import me.matl114.gui.Constants;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicListWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.interfaces.ElementFactories;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.interfaces.ElementStyle;
import me.matl114.gui.interfaces.ElementType;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.hacks.utils.gui.*;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@With
public record GuiSetting(
        TextConfig textConfig,
        Map<Identifier, CustomGuiConfig> customConfig,
        AlignConfig alignConfig,
        Identifier backgroundStyle,
        Identifier titleStyle,
        Identifier labelStyle,
        Identifier buttonStyle,
        Identifier inputStyle,
        Identifier sliderStyle,
        boolean enableSlider)
        implements NBTParsable<GuiSetting>, GuiColorSamplier {

    public GuiSetting {
        Map<Identifier, CustomGuiConfig> configs = new LinkedHashMap<>();
        if (customConfig != null) configs.putAll(customConfig);
        for (ElementStyle style : ElementFactories.values()) {
            if (style.useConfig()) configs.putIfAbsent(style.id(), style.defaultedConfig());
        }
        customConfig = Map.copyOf(configs);
    }

    public static final GuiSetting DEFAULT = new GuiSetting(
            TextConfig.DEFAULT,
            defaultConfigs(),
            AlignConfig.DEFAULT,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            GuiStyle.CUSTOM,
            true);

    public static final GuiSetting SLIDER_DEFAULT = DEFAULT.withCustomConfig(Map.of(
            GuiStyle.CUSTOM,
            CustomGuiConfig.DEFAULT.withInactiveFrameColor(CustomGuiConfig.DEFAULT_INACTIVE_FRAME_COLOR)));

    private static final Codec<Map<Identifier, CustomGuiConfig>> CUSTOM_CONFIG_CODEC =
            Codec.unboundedMap(Identifier.CODEC, CustomGuiConfig.CODEC);

    public static final Codec<GuiSetting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    TextConfig.CODEC
                            .optionalFieldOf("text_config", TextConfig.DEFAULT)
                            .forGetter(GuiSetting::textConfig),
                    CUSTOM_CONFIG_CODEC
                            .optionalFieldOf("custom_config", defaultConfigs())
                            .forGetter(GuiSetting::customConfig),
                    AlignConfig.CODEC
                            .optionalFieldOf("align_config", AlignConfig.DEFAULT)
                            .forGetter(GuiSetting::alignConfig),
                    Identifier.CODEC
                            .optionalFieldOf("background_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::backgroundStyle),
                    Identifier.CODEC
                            .optionalFieldOf("title_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::titleStyle),
                    Identifier.CODEC
                            .optionalFieldOf("label_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::labelStyle),
                    Identifier.CODEC
                            .optionalFieldOf("button_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::buttonStyle),
                    Identifier.CODEC
                            .optionalFieldOf("input_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::inputStyle),
                    Identifier.CODEC
                            .optionalFieldOf("slider_style", GuiStyle.CUSTOM)
                            .forGetter(GuiSetting::sliderStyle),
                    Codec.BOOL.optionalFieldOf("enable_slider", true).forGetter(GuiSetting::enableSlider))
            .apply(instance, GuiSetting::new));

    public static final NBTType<GuiSetting> TYPE =
            new NBTType<>("guisetting", CODEC, GuiSetting::createWidget, DEFAULT);

    @Override
    public NBTType<GuiSetting> type() {
        return TYPE;
    }

    private static Map<Identifier, CustomGuiConfig> defaultConfigs() {
        Map<Identifier, CustomGuiConfig> result = new LinkedHashMap<>();
        for (ElementStyle style : ElementFactories.values()) {
            if (style.useConfig()) result.put(style.id(), style.defaultedConfig());
        }
        return result;
    }

    public CustomGuiConfig customConfig(Identifier id) {
        ElementStyle style = ElementFactories.get(id);
        CustomGuiConfig fallback = style == null ? CustomGuiConfig.DEFAULT : style.defaultedConfig();
        return customConfig.getOrDefault(id, fallback);
    }

    public GuiSetting withCustomConfig(Identifier id, CustomGuiConfig config) {
        Map<Identifier, CustomGuiConfig> configs = new LinkedHashMap<>(customConfig);
        configs.put(id, config);
        return withCustomConfig(configs);
    }

    public GuiSetting normalizeStyles() {
        GuiSetting result = this;
        for (ElementType type : ElementType.values()) {
            Identifier style = result.style(type);
            if (ElementFactories.get(style) == null) result = result.withStyle(type, GuiStyle.CUSTOM);
        }
        return result;
    }

    public Identifier style(ElementType type) {
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
    public WrapRGBA activeFrameColor() {
        return customConfig(buttonStyle).activeFrameColor();
    }

    @Override
    public WrapRGBA inactiveFrameColor() {
        return customConfig(buttonStyle).inactiveFrameColor();
    }

    @Override
    public WrapRGBA backgroundColor() {
        return customConfig(backgroundStyle).backgroundColor();
    }

    @Override
    public WrapRGBA plateColor() {
        return customConfig(backgroundStyle).plateColor();
    }

    @Override
    public WrapRGBA titleColor() {
        return customConfig(titleStyle).titleColor();
    }

    @Override
    public WrapRGBA labelColor() {
        return customConfig(labelStyle).labelColor();
    }

    @Override
    public WrapRGBA buttonActiveColor() {
        return customConfig(buttonStyle).buttonActiveColor();
    }

    @Override
    public WrapRGBA buttonInactiveColor() {
        return customConfig(buttonStyle).buttonInactiveColor();
    }

    @Override
    public WrapRGBA textColor() {
        return customConfig(inputStyle).textColor();
    }

    @Override
    public WrapRGBA textBackground() {
        return customConfig(inputStyle).textBackground();
    }

    @Override
    public WrapRGBA sliderColor() {
        return customConfig(sliderStyle).sliderColor();
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

    private GuiSetting withStyle(ElementType type, Identifier value) {
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
        return ValueAccessor.of(getter, value -> source.accept(updater.apply(value)));
    }

    private static <T> ConditionalWidget field(
            String key,
            ValueAccessor<T> accessor,
            BooleanSupplier condition,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory) {
        DrawableWidget widget = WidgetUtils.createValueAccessorList(List.of(Pair.of(key, accessor)), layout, factory);
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
        source.accept(source.get().normalizeStyles());
        int width = WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT.totalWidth();
        DrawableWidget widget = createEditorWidget(factory, source, 0, 0, width, 0);
        CenterScreen screen = new CenterScreen(widget);
        ScreenAccess.of(screen).addCloseFuture(Config::configSaveTasks);
        ScreenAccess.of(screen).openFromCurrent();
    }

    private static ValueAccessor<StringEnum> styleAccessor(AttrKeyValue<GuiSetting> source, ElementType type) {
        List<String> values =
                ElementFactories.ids().stream().map(Identifier::toString).toList();
        return accessor(source, () -> new StringEnum(source.get().style(type).toString(), values), value -> {
            Identifier id = Identifier.tryParse(value.value());
            return source.get().withStyle(type, id == null ? GuiStyle.CUSTOM : id);
        });
    }

    private static Identifier styleId(ValueAccessor<StringEnum> style) {
        Identifier id = Identifier.tryParse(style.getValue().value());
        return id == null ? GuiStyle.CUSTOM : id;
    }

    private static boolean usesConfig(ValueAccessor<StringEnum> style) {
        ElementStyle entry = ElementFactories.get(styleId(style));
        return entry != null && entry.useConfig();
    }

    private static CustomGuiConfig selectedConfig(AttrKeyValue<GuiSetting> source, ValueAccessor<StringEnum> style) {
        return source.get().customConfig(styleId(style));
    }

    private static GuiSetting updateConfig(
            AttrKeyValue<GuiSetting> source,
            ValueAccessor<StringEnum> style,
            Function<CustomGuiConfig, CustomGuiConfig> updater) {
        Identifier id = styleId(style);
        return source.get().withCustomConfig(id, updater.apply(source.get().customConfig(id)));
    }

    private static DrawableWidget createEditorWidget(
            ElementFactory factory, AttrKeyValue<GuiSetting> source, int x, int y, int dx, int dy) {
        WidgetUtils.ConfigScreenLayout layout = WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT.forWidth(dx);
        DynamicListWidget list = new DynamicListWidget(x, y, Math.max(1, dx));

        ValueAccessor<StringEnum> backgroundStyle = styleAccessor(source, ElementType.BACKGROUND);
        ValueAccessor<StringEnum> titleStyle = styleAccessor(source, ElementType.TITLE);
        ValueAccessor<StringEnum> labelStyle = styleAccessor(source, ElementType.LABEL);
        ValueAccessor<StringEnum> buttonStyle = styleAccessor(source, ElementType.BUTTON);
        ValueAccessor<StringEnum> inputStyle = styleAccessor(source, ElementType.INPUT);
        ValueAccessor<StringEnum> sliderStyle = styleAccessor(source, ElementType.SLIDER);
        ValueAccessor<Boolean> enableSlider = accessor(
                source, () -> source.get().enableSlider(), value -> source.get().withEnableSlider(value));

        list.addDrawableChild(factory.createTitle(
                () -> Text.translatable("widget.nbt-parsable.gui-config.edit-screen.title"),
                List::of,
                0,
                0,
                list.getWidth(),
                layout.buttonHeight()));

        addGroup(
                list,
                "text-config",
                List.of(
                        field(
                                "widget.gui-settings.text-config.color",
                                colorAccessor(
                                        source, () -> source.get().textConfig().color(), value -> source.get()
                                                .withTextConfig(source.get()
                                                        .textConfig()
                                                        .withColor(value))),
                                () -> true,
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.text-config.text_align",
                                alignAccessor(
                                        source, () -> source.get().textConfig().textAlign(), value -> source.get()
                                                .withTextConfig(source.get()
                                                        .textConfig()
                                                        .withTextAlign(value))),
                                () -> true,
                                layout,
                                factory)),
                layout,
                factory);
        addGroup(
                list,
                "background",
                List.of(
                        field(
                                "widget.gui-settings.background.background_style",
                                backgroundStyle,
                                () -> true,
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.background.background_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, backgroundStyle)
                                                .backgroundColor(),
                                        value -> updateConfig(
                                                source, backgroundStyle, config -> config.withBackgroundColor(value))),
                                () -> usesConfig(backgroundStyle),
                                layout,
                                factory)),
                layout,
                factory);
        addGroup(
                list,
                "title",
                List.of(
                        field("widget.gui-settings.title.title_style", titleStyle, () -> true, layout, factory),
                        field(
                                "widget.gui-settings.title.title_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, titleStyle).titleColor(),
                                        value -> updateConfig(
                                                source, titleStyle, config -> config.withTitleColor(value))),
                                () -> usesConfig(titleStyle),
                                layout,
                                factory)),
                layout,
                factory);
        addGroup(
                list,
                "label",
                List.of(
                        field("widget.gui-settings.label.label_style", labelStyle, () -> true, layout, factory),
                        field(
                                "widget.gui-settings.label.label_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, labelStyle).labelColor(),
                                        value -> updateConfig(
                                                source, labelStyle, config -> config.withLabelColor(value))),
                                () -> usesConfig(labelStyle),
                                layout,
                                factory)),
                layout,
                factory);
        addGroup(
                list,
                "button",
                List.of(
                        field("widget.gui-settings.button.button_style", buttonStyle, () -> true, layout, factory),
                        field(
                                "widget.gui-settings.button.active_frame_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, buttonStyle)
                                                .activeFrameColor(),
                                        value -> updateConfig(
                                                source, buttonStyle, config -> config.withActiveFrameColor(value))),
                                () -> usesConfig(buttonStyle),
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.button.inactive_frame_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, buttonStyle)
                                                .inactiveFrameColor(),
                                        value -> updateConfig(
                                                source, buttonStyle, config -> config.withInactiveFrameColor(value))),
                                () -> usesConfig(buttonStyle),
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.button.button_active_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, buttonStyle)
                                                .buttonActiveColor(),
                                        value -> updateConfig(
                                                source, buttonStyle, config -> config.withButtonActiveColor(value))),
                                () -> usesConfig(buttonStyle),
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.button.button_inactive_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, buttonStyle)
                                                .buttonInactiveColor(),
                                        value -> updateConfig(
                                                source, buttonStyle, config -> config.withButtonInactiveColor(value))),
                                () -> usesConfig(buttonStyle),
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.button.button_text_align",
                                alignAccessor(
                                        source,
                                        () -> source.get().alignConfig().buttonTextAlign(),
                                        value -> source.get()
                                                .withAlignConfig(source.get()
                                                        .alignConfig()
                                                        .withButtonTextAlign(value))),
                                () -> usesConfig(buttonStyle),
                                layout,
                                factory)),
                layout,
                factory);
        addGroup(
                list,
                "input",
                List.of(
                        field("widget.gui-settings.input.input_style", inputStyle, () -> true, layout, factory),
                        field(
                                "widget.gui-settings.input.text_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, inputStyle).textColor(),
                                        value -> updateConfig(
                                                source, inputStyle, config -> config.withTextColor(value))),
                                () -> usesConfig(inputStyle),
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.input.text_background",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, inputStyle).textBackground(),
                                        value -> updateConfig(
                                                source, inputStyle, config -> config.withTextBackground(value))),
                                () -> usesConfig(inputStyle),
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.input.input_text_align",
                                alignAccessor(
                                        source, () -> source.get().alignConfig().inputTextAlign(), value -> source.get()
                                                .withAlignConfig(source.get()
                                                        .alignConfig()
                                                        .withInputTextAlign(value))),
                                () -> usesConfig(inputStyle),
                                layout,
                                factory)),
                layout,
                factory);
        addGroup(
                list,
                "slider",
                List.of(
                        field("widget.gui-settings.slider.enable_slider", enableSlider, () -> true, layout, factory),
                        field(
                                "widget.gui-settings.slider.slider_style",
                                sliderStyle,
                                enableSlider::getValue,
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.slider.slider_color",
                                colorAccessor(
                                        source,
                                        () -> selectedConfig(source, sliderStyle)
                                                .sliderColor(),
                                        value -> updateConfig(
                                                source, sliderStyle, config -> config.withSliderColor(value))),
                                () -> enableSlider.getValue() && usesConfig(sliderStyle),
                                layout,
                                factory),
                        field(
                                "widget.gui-settings.slider.slider_text_align",
                                alignAccessor(
                                        source,
                                        () -> source.get().alignConfig().sliderTextAlign(),
                                        value -> source.get()
                                                .withAlignConfig(source.get()
                                                        .alignConfig()
                                                        .withSliderTextAlign(value))),
                                () -> enableSlider.getValue() && usesConfig(sliderStyle),
                                layout,
                                factory)),
                layout,
                factory);

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
                list, header, ValueAccessor.holder(true), fields, factory, list.getWidth() - height, 2, height, height);
    }
}
