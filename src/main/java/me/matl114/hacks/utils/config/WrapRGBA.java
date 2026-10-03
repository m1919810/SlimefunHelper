package me.matl114.hacks.utils.config;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import com.mojang.serialization.Codec;

public record WrapRGBA(TextColor textColor, int alpha) implements NBTParsable<WrapRGBA> {
    public static final NBTType<WrapRGBA> TYPE = new NBTType<>(
            "wraprgba",
            RecordCodecBuilder.<WrapRGBA>create(instance -> instance.group(
                            TextColor.CODEC.fieldOf("color").forGetter(WrapRGBA::textColor),
                            Codec.INT.fieldOf("alpha").forGetter(WrapRGBA::alpha))
                    .apply(instance, WrapRGBA::new)),
            (factory, value, x, y, dx, dy) -> {
                SubScreenWidget widget = SubScreenWidget.instance(x, y, dx, dy);
                int colorWidth = dx * 2 / 3;
                int alphaWidth = dx - colorWidth;
                WrapperFactory<TextColor, WrapRGBA> colorFactory =
                        WrapperFactory.of(value.get()::withTextColor, WrapRGBA::textColor);
                WrapperFactory<Integer, WrapRGBA> alphaFactory =
                        WrapperFactory.of(value.get()::withAlpha, WrapRGBA::alpha);
                return widget
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(value, colorFactory, NBTTypes.COLOR_TYPE)
                                .generateValueWidget(factory, 0, 0, colorWidth, dy))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(value, alphaFactory, NBTTypes.INT_TYPE)
                                .generateValueWidget(factory, colorWidth, 0, alphaWidth, dy));
            },
            new WrapRGBA(TextColor.fromFormatting(Formatting.WHITE), 255));

    public WrapRGBA withTextColor(TextColor textColor) {
        return new WrapRGBA(textColor, alpha);
    }

    public WrapRGBA withAlpha(int alpha) {
        if(alpha < 0 || alpha > 255){
            throw new IllegalArgumentException();
        }
        return new WrapRGBA(textColor, alpha);
    }

    public WrapRGBA(WrapColor color) {
        this(color.color(), 255);
    }

    public WrapRGBA(String value, int alpha){
        this(ColorUtils.color(value), alpha);
    }

    @Override
    public NBTType<WrapRGBA> type() {
        return TYPE;
    }

    @Override
    public <W> Optional<WrapRGBA> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef<?> nbtRef && nbtRef.get() instanceof WrapColor wrapColor) {
            return Optional.of(this.withTextColor(wrapColor.color()));
        }
        return Optional.empty();
    }

    public int color(){
        return ColorUtils.withAlphaInt(textColor.getRgb(), alpha);
    }
}
