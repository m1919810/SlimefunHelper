package me.matl114.hooks;

import io.github.reserveword.imblocker.common.gui.FocusableObject;
import lombok.Getter;

public class IMBlockerHooks implements IHooks {
    @Getter
    boolean enabled = false;

    public IMBlockerHooks() {
        try {
            Class<?> clazz = FocusableObject.class;
            enabled = true;
        } catch (Throwable ex) {
            enabled = false;
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
