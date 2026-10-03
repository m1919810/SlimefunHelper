package me.matl114.hacks.utils.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.matl114.hacks.utils.config.WrapRGBA;

public record TextConfig(WrapRGBA color) {
    public static final TextConfig DEFAULT = new TextConfig(CustomGuiConfig.DEFAULT_TEXT_COLOR);

    public static final Codec<TextConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    WrapRGBA.TYPE.typeCodec()
                            .optionalFieldOf("color", CustomGuiConfig.DEFAULT_TEXT_COLOR)
                            .forGetter(TextConfig::color))
            .apply(instance, TextConfig::new));

    // TODO: Add configurable font selection when font rendering support is implemented.
    public TextConfig withColor(WrapRGBA value) {
        return new TextConfig(value);
    }
}
