package me.matl114.utils;

import java.awt.*;
import java.util.Objects;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public class ColorUtils {
    public static Color getColor(int r, int g, int b, int a) {
        return new Color((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255), true);
    }

    public static int getColorInt(float r, float g, float b) {
        return getColorInt((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    public static int getColorInt(int r, int g, int b) {
        return 0XFF000000 | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    public static Color getColor(int r, int g, int b) {
        return new Color(0XFF000000 | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255), true);
    }

    public static int getColorInt(int r, int g, int b, int a) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color((color.getRGB() & 0x00FFFFFF) | (alpha << 24), true);
    }

    public static Color withAlpha(TextColor color, int alpha) {
        return new Color((color.getRgb() & 0x00FFFFFF) | (alpha << 24), true);
    }

    public static Color withAlpha(TextColor color, float alpha) {
        return withAlpha(color, (int) (alpha * 255));
    }

    public static int withAlphaInt(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static Color withAlpha(Color color, float alpha) {
        return withAlpha(color, (int) (alpha * 255));
    }

    public static int withAlphaInt(int color, float alpha) {
        return withAlphaInt(color, (int) (alpha * 255.0F));
    }

    public static int orWithAlphaInt(int color, int alpha) {
        return (color & 0XFF000000) == 0 ? withAlphaInt(color, alpha) : color;
    }

    public static TextColor color(Formatting formatting) {
        return Objects.requireNonNull(TextColor.fromFormatting(formatting));
    }

    public static TextColor color(Color color) {
        return TextColor.fromRgb(color.getRGB());
    }

    public static TextColor color(String str) {
        return TextColor.parse(str).getOrThrow();
    }
}
