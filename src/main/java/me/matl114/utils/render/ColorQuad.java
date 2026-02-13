package me.matl114.utils.render;

import java.awt.*;

public interface ColorQuad {
    public int get(int idx);

    public static ColorQuad of(Color color) {
        return new Const(color.getRGB());
    }

    public static ColorQuad of(int r, int g, int b, int a) {
        return new Const((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255));
    }

    public static ColorQuad of(int color) {
        return new Const(color);
    }

    public static ColorQuad ofXGradient(Color color1, Color color2) {
        return new Quad(color1.getRGB(), color1.getRGB(), color2.getRGB(), color2.getRGB());
    }

    public static ColorQuad ofYGradient(Color color1, Color color2) {
        return new Quad(color1.getRGB(), color2.getRGB(), color2.getRGB(), color1.getRGB());
    }

    public static ColorQuad ofXGradient(int color1, int color2) {
        return new Quad(color1, color1, color2, color2);
    }

    public static ColorQuad ofYGradient(int color1, int color2) {
        return new Quad(color1, color2, color2, color1);
    }

    public static ColorQuad ofGradient(int color1, int color2, int color3, int color4) {
        return new Quad(color1, color2, color3, color4);
    }

    public static ColorQuad ofGradient(Color color1, Color color2, Color color3, Color color4) {
        return new Quad(color1.getRGB(), color2.getRGB(), color3.getRGB(), color4.getRGB());
    }

    public static record Const(int val) implements ColorQuad {

        @Override
        public int get(int idx) {
            return val;
        }
    }

    public static record Quad(int val1, int val2, int val3, int val4) implements ColorQuad {
        @Override
        public int get(int idx) {
            return switch (idx & 3) {
                case 0 -> val1;
                case 1 -> val2;
                case 2 -> val3;
                case 3 -> val4;
                default -> throw new IndexOutOfBoundsException();
            };
        }
    }
}
