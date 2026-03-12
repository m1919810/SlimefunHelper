package me.matl114.managers.input;

import java.util.function.BooleanSupplier;
import me.matl114.gui.config.ConfigurateNewStyleScreen;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.MinecraftClient;

public class HotKeyUtils {
    private static final FlagRef hotkeyNoScreen =
            Configs.HOTKEY_CONFIG.getBoolean(Configs.HOTKEY_WORKS_ONLY_WHEN_NOT_AT_SCREEN);
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isValidState() {
        if (mc.currentScreen == null) {
            return true;
        }
        if (hotkeyNoScreen.get()) {
            return false;
        }
        return !(mc.currentScreen instanceof ConfigurateNewStyleScreen);
    }

    public static SimpleHotKey.InputHandler wrapAsHandler(Runnable task) {
        return (in) -> {
            if (isValidState()) {
                task.run();
                return true;
            }
            return false;
        };
    }

    public static SimpleHotKey.InputHandler wrapAsHandler(BooleanSupplier task) {
        return (in) -> {
            if (isValidState()) {
                return task.getAsBoolean();
            }
            return false;
        };
    }

    public static SimpleHotKey.InputHandler asHandler(Runnable task) {
        return (in) -> {
            task.run();
            return true;
        };
    }

    public static SimpleHotKey.InputHandler asHandler(BooleanSupplier task) {
        return in -> task.getAsBoolean();
    }
}
