package io.github.reserveword.imblocker.common.gui;

import java.awt.*;

public interface MinecraftTextFieldWidget extends FocusableObject {
    default void setPreferredEnglishState(boolean state) {}

    default boolean getPrimaryEnglishState() {
        return false;
    }

    default int getPaddingX() {
        return 4;
    }

    default void checkVisibility(long lastGameRenderTime) {}
}
