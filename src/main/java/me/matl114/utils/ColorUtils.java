package me.matl114.utils;

import java.awt.*;

public class ColorUtils {
    public static Color getColor(int r, int g, int b, int a) {
        return new Color((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255), true);
    }

    public static Color getColor(int r, int g, int b) {
        return new Color(0XFF000000 | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255), true);
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color((color.getRGB() & 0x00FFFFFF) | (alpha << 24), true);
    }

    public static Color withAlpha(Color color, float alpha) {
        return withAlpha(color, (int) (alpha * 255));
    }
}
