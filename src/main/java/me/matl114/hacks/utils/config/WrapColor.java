package me.matl114.hacks.utils.config;

import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public record WrapColor(TextColor color) implements NBTParsable<WrapColor> {
    public static WrapColor WHITE = new WrapColor(TextColor.fromFormatting(Formatting.WHITE));
    public static NBTType<WrapColor> TYPE = NBTTypes.createXMap(
            WrapColor.class, NBTTypes.COLOR_TYPE, WrapperFactory.of(WrapColor::new, WrapColor::color));

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
}
