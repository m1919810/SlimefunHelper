package me.matl114.hacks.utils.config;

import java.awt.*;
import java.util.Optional;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public record WrapColor(TextColor color) implements NBTParsable<WrapColor> {
    public WrapColor(Formatting formatting) {
        this(ColorUtils.color(formatting));
    }

    public WrapColor(Color color) {
        this(ColorUtils.color(color));
    }

    public WrapColor(String string) {
        this(ColorUtils.color(string));
    }

    public static WrapColor WHITE = new WrapColor(TextColor.fromFormatting(Formatting.WHITE));
    public static NBTType<WrapColor> TYPE =
            NBTTypes.createXMap("wrapcolor", NBTTypes.COLOR_TYPE, WrapperFactory.of(WrapColor::new, WrapColor::color));

    @Override
    public NBTType<WrapColor> type() {
        return TYPE;
    }

    public int asRGB() {
        return color.getRgb();
    }

    public int withAlpha(int alpha) {
        return ColorUtils.withAlphaInt(color.getRgb(), alpha);
    }

    @Override
    public <W> Optional<WrapColor> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof IntRef intRef) {
            return Optional.of(new WrapColor(TextColor.fromRgb(intRef.get())));
        }
        return Optional.empty();
    }
}
