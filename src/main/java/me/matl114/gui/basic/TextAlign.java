package me.matl114.gui.basic;

public enum TextAlign {
    LEFT,
    MIDDLE,
    RIGHT;

    public static TextAlign fromLegacy(int alignment) {
        return switch (alignment) {
            case -1 -> LEFT;
            case 1 -> RIGHT;
            default -> MIDDLE;
        };
    }
}
