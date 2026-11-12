package me.matl114.hooks;

import io.github.reserveword.imblocker.common.IMManager;
import io.github.reserveword.imblocker.common.gui.FocusContainer;
import io.github.reserveword.imblocker.common.gui.FocusableObject;
import lombok.Getter;

public class IMBlockerHooks implements IHooks {
    @Getter
    boolean enabled = false;

    public IMBlockerHooks() {
        try {
            Class<?> clazz = FocusableObject.class;
            clazz = FocusContainer.class;
            clazz = IMManager.class;
            enabled = true;
        } catch (Throwable ex) {
            enabled = false;
        }
    }

    /**
     * Sets IMBlocker's preferred IME state for the Minecraft GUI focus container.
     */
    public void setPreferredState(boolean preferredState) {
        if (enabled) {
            FocusContainer.MINECRAFT.setPreferredState(preferredState);
        }
    }

    /**
     * Sets the current IME conversion state to English or Chinese where supported.
     */
    public void setEnglishState(boolean isEnglish) {
        if (enabled) {
            IMManager.setEnglishState(isEnglish);
        }
    }

    public static IMBlockerHooks instance;

    public static IMBlockerHooks getInstance() {
        if (instance == null) {
            instance = new IMBlockerHooks();
        }
        return instance;
    }
}
