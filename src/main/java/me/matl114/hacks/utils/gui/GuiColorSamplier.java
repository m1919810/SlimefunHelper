package me.matl114.hacks.utils.gui;

import me.matl114.hacks.utils.config.WrapRGBA;

public interface GuiColorSamplier {
    WrapRGBA activeFrameColor();

    WrapRGBA inactiveFrameColor();

    WrapRGBA backgroundColor();

    WrapRGBA plateColor();

    WrapRGBA titleColor();

    WrapRGBA labelColor();

    WrapRGBA buttonActiveColor();

    WrapRGBA buttonInactiveColor();

    WrapRGBA textColor();

    default WrapRGBA inputTextColor() {
        return textColor();
    }

    WrapRGBA textBackground();

    WrapRGBA sliderColor();

    default AlignType textAlign() {
        return AlignType.MIDDLE;
    }

    default AlignType buttonTextAlign() {
        return AlignType.MIDDLE;
    }

    default AlignType inputTextAlign() {
        return AlignType.LEFT;
    }

    default AlignType sliderTextAlign() {
        return AlignType.MIDDLE;
    }
}
