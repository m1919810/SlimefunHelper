package me.matl114.hacks.utils.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.With;
import me.matl114.hacks.utils.config.WrapRGBA;
import me.matl114.gui.basic.ColorSampler;

@With
public record CustomGuiConfig(
        WrapRGBA titleColor,
        WrapRGBA labelColor,
        WrapRGBA backgroundColor,
        WrapRGBA plateColor,
        WrapRGBA buttonActiveColor,
        WrapRGBA buttonInactiveColor,
        WrapRGBA frameColor,
        WrapRGBA textColor,
        WrapRGBA textBackground,
        WrapRGBA sliderColor)
        implements GuiColorSamplier {
    public static final WrapRGBA DEFAULT_MODULE_LIST_COLOR = new WrapRGBA("#984FDB", 255);
    public static final WrapRGBA DEFAULT_TITLE_COLOR = new WrapRGBA("#984FDB", 255);
    public static final WrapRGBA DEFAULT_LABEL_COLOR = new WrapRGBA("#323232", 255);
    public static final WrapRGBA DEFAULT_BACKGROUND_COLOR = new WrapRGBA("#323232", 192);
    public static final WrapRGBA DEFAULT_BUTTON_ACTIVE_COLOR = new WrapRGBA("#323232", 216);
    public static final WrapRGBA DEFAULT_BUTTON_INACTIVE_COLOR = new WrapRGBA("#323232", 128);
    public static final WrapRGBA DEFAULT_TEXT_COLOR = new WrapRGBA("#FFFFFF", 255);
    public static final WrapRGBA DEFAULT_TEXT_BACKGROUND = new WrapRGBA("#000000", 255);
    public static final WrapRGBA DEFAULT_SLIDER_COLOR = DEFAULT_TITLE_COLOR.withAlpha(192);

    public static final CustomGuiConfig DEFAULT = new CustomGuiConfig(

            DEFAULT_TITLE_COLOR,
            DEFAULT_LABEL_COLOR,
            DEFAULT_BACKGROUND_COLOR,
            DEFAULT_TEXT_COLOR,
            DEFAULT_BUTTON_ACTIVE_COLOR,
            DEFAULT_BUTTON_INACTIVE_COLOR,
            DEFAULT_MODULE_LIST_COLOR,
            DEFAULT_TEXT_COLOR,
            DEFAULT_TEXT_BACKGROUND,
            DEFAULT_SLIDER_COLOR);

    public static final Codec<CustomGuiConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(

                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("title_color", DEFAULT_TITLE_COLOR)
                            .forGetter(CustomGuiConfig::titleColor),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("label_color", DEFAULT_LABEL_COLOR)
                            .forGetter(CustomGuiConfig::labelColor),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("background_color", DEFAULT_BACKGROUND_COLOR)
                            .forGetter(CustomGuiConfig::backgroundColor),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("plate_color", DEFAULT_TEXT_COLOR)
                            .forGetter(CustomGuiConfig::plateColor),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("button_active_color", DEFAULT_BUTTON_ACTIVE_COLOR)
                            .forGetter(CustomGuiConfig::buttonActiveColor),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("button_inactive_color", DEFAULT_BUTTON_INACTIVE_COLOR)
                        .forGetter(CustomGuiConfig::buttonInactiveColor),
                    WrapRGBA.TYPE.typeCodec()
                        .optionalFieldOf("frame_color", DEFAULT_MODULE_LIST_COLOR)
                            .forGetter(CustomGuiConfig::frameColor),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("text_color", DEFAULT_TEXT_COLOR)
                            .forGetter(CustomGuiConfig::textColor),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("text_background", DEFAULT_TEXT_BACKGROUND)
                            .forGetter(CustomGuiConfig::textBackground),
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("slider_color", DEFAULT_SLIDER_COLOR)
                            .forGetter(CustomGuiConfig::sliderColor))
            .apply(instance, CustomGuiConfig::new));

}
