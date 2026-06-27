package me.matl114.hacks.utils;

import java.util.function.BooleanSupplier;
import me.matl114.hacks.modules.task.ModuleSettings;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.input.SimpleHotKey;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HotKeyUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isValidState() {
        if (mc.currentScreen == null) {
            return true;
        }
        if (ModuleSettings.INSTANCE.shouldNotExecuteConditionHotkey()) {
            return false;
        }
        return true;
    }

    public static SimpleHotKey.InputHandler wrapAsHandler(Runnable task) {
        return (ih, in) -> {
            if (isValidState()) {
                task.run();
                return true;
            }
            return false;
        };
    }

    public static SimpleHotKey.InputHandler wrapAsHandler(BooleanSupplier task) {
        return (ih, in) -> {
            if (isValidState()) {
                return task.getAsBoolean();
            }
            return false;
        };
    }

    public static SimpleHotKey.InputHandler asHandler(Runnable task) {
        return (ih, in) -> {
            task.run();
            return true;
        };
    }

    public static SimpleHotKey.InputHandler asHandler(BooleanSupplier task) {
        return (ih, in) -> task.getAsBoolean();
    }

    public static Runnable wrapFlagAsToggle(String[] path, FlagRef flagRef) {
        return wrapFlagAsToggle(String.join(".", path), flagRef);
    }

    public static Runnable wrapFlagAsToggle(String path, FlagRef flagRef) {
        return () -> {
            boolean result = !flagRef.get();
            flagRef.set(result);
            if (ModuleSettings.INSTANCE.moduleToggleNotify.get()) {
                Debug.chat(
                        Text.literal(result ? "[+]" : "[-]")
                                .formatted(result ? Formatting.GREEN : Formatting.RED)
                                .formatted(Formatting.BOLD),
                        Text.translatableWithFallback(path, path));
            }
        };
    }

    public static Runnable getToggleTask(Config config, String... path) {
        String commonPath = String.join(".", path);
        var flag = config.getBoolean(path);
        return HotKeyUtils.wrapFlagAsToggle(commonPath, flag);
    }

    public static SimpleHotKey.InputHandler getToggleHandler(Config config, String... path) {
        String commonPath = String.join(".", path);
        var flag = config.getBoolean(path);
        if (flag != null) {
            var toggleTask = HotKeyUtils.wrapFlagAsToggle(commonPath, flag);
            return (ih, m) -> {
                ClientPlayerEntity player = m.getClient().player;
                if (player != null && HotKeyUtils.isValidState()) {
                    toggleTask.run();
                    return ModuleSettings.INSTANCE.toggleKeysStopVanilla.get();
                }
                return false;
            };
        } else {
            throw new IllegalArgumentException("No Flag for " + commonPath);
        }
    }
}
