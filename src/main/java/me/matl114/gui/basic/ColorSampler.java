package me.matl114.gui.basic;

import java.awt.*;

public interface ColorSampler {
    public int getColorInt();

    default Color getColor() {
        return new Color(getColorInt());
    }

    public static ColorSampler WHITE = of(Color.WHITE.getRGB());

    public static ColorSampler of(int color) {
        Color color1 = new Color(color);
        return new ColorSampler() {

            @Override
            public int getColorInt() {
                return color;
            }

            public Color getColor() {
                return color1;
            }
        };
    }
}
