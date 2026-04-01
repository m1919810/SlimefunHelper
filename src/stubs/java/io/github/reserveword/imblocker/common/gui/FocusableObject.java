package io.github.reserveword.imblocker.common.gui;

import java.awt.*;

public interface FocusableObject {
    /**
     * <p>Called from parent focus transfer node, deliver the focus to this
     * focusable element.
     *
     * <p>If this element is an instance of {@code FocusContainer}, the focus
     * may be transferred to its {@code focusedWidget} if present.
     *
     * <p><b>This method will modify the focus transfer path and must be called
     * with proper validation.</b>
     */
    default void deliverFocus() {}

    /**
     * <p>Notify this element to lose its focus. <b>This method should be called
     * before assigning new {@code focusOwner}</b>.
     *
     * <p>This method is originally designed to sync the focus state of its
     * corresponding Minecraft input context, however it's not practical to do
     * that because of their chaotic architectures.
     */
    default void lostFocus() {}

    /**
     * Whether this focusable element is the <b>REAL</b> ultimate destination of
     * keyboard inputs.
     */
    default boolean isTrulyFocused() {
        return false;
    }
    /**
     * <p>Update the states of IME with this element's preferred states.
     *
     * <p><b>This method can only be called if this element {@code isTrulyFocused}</b>.
     */
    default void updateIMState() {}

    /**
     * <p>Update the English state (conversion status on Windows platform) of IME
     * with this element's preferred English state.
     *
     * <p><b>This method can only be called if this element {@code isTrulyFocused}</b>.
     */
    default void updateEnglishState() {}

    /**
     * Get the expected IME state of this element.
     *
     * @return {@code true} if this element wishes to enable the IME, {@code false}
     * if this element wishes to disable the IME.
     */
    default boolean getPreferredState() {
        return false;
    }

    default boolean getPreferredEnglishState() {
        return false;
    }

    default int getFontHeight() {
        return 0;
    }
}
