package me.matl114.hacks.utils.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.matl114.utils.CodecUtils;

public record AlignConfig(AlignType buttonTextAlign, AlignType inputTextAlign, AlignType sliderTextAlign) {
    public static final AlignConfig DEFAULT = new AlignConfig(AlignType.MIDDLE, AlignType.LEFT, AlignType.MIDDLE);

    public static final Codec<AlignConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    CodecUtils.enumCodec(AlignType.class)
                            .optionalFieldOf("button_text_align", AlignType.MIDDLE)
                            .forGetter(AlignConfig::buttonTextAlign),
                    CodecUtils.enumCodec(AlignType.class)
                            .optionalFieldOf("input_text_align", AlignType.LEFT)
                            .forGetter(AlignConfig::inputTextAlign),
                    CodecUtils.enumCodec(AlignType.class)
                            .optionalFieldOf("slider_text_align", AlignType.MIDDLE)
                            .forGetter(AlignConfig::sliderTextAlign))
            .apply(instance, AlignConfig::new));

    public AlignConfig withButtonTextAlign(AlignType value) {
        return new AlignConfig(value, inputTextAlign, sliderTextAlign);
    }

    public AlignConfig withInputTextAlign(AlignType value) {
        return new AlignConfig(buttonTextAlign, value, sliderTextAlign);
    }

    public AlignConfig withSliderTextAlign(AlignType value) {
        return new AlignConfig(buttonTextAlign, inputTextAlign, value);
    }
}
