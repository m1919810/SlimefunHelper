package me.matl114.hacks.modules.task;

import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.gui.widget.TextFieldWidget;

public class ModuleSettings extends BaseModule {
    public static ModuleSettings INSTANCE;

    public ModuleSettings() {
        super("Modules");
        INSTANCE = this;
    }

    ModulePath moduleSettings = makePath(Configs.MISC_CONFIG, "module-settings");
    public final EnumRef<HotkeyPolicy> hotkeyPolicy = builder(
                    moduleSettings.add("hotkey-work-policy"), HotkeyPolicy.class)
            .defaultValue(HotkeyPolicy.ONLY_WHEN_NO_SCREEN)
            .build();

    public final FlagRef moduleToggleNotify = builder(moduleSettings.add("module-toggle-notify"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public boolean shouldNotExecuteConditionHotkey() {
        if (mc.currentScreen != null) {
            if (hotkeyPolicy.getValue() == HotkeyPolicy.RUN_IN_ALL_SCREEN) {
                return false;
            }
            if (checkNull()) {
                return true;
            }
            switch (hotkeyPolicy.getValue()) {
                case ONLY_WHEN_NO_SCREEN: {
                    return true;
                }
                case WHEN_NO_INPUT_SCREEN: {
                    if (mc.currentScreen.getFocused() instanceof TextFieldWidget textField
                            || mc.currentScreen.getFocused() instanceof DrawableWidget gui) {
                        return true;
                    }
                    return false;
                }
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public static enum HotkeyPolicy implements ConfigEnum {
        ONLY_WHEN_NO_SCREEN,
        WHEN_NO_INPUT_SCREEN,
        RUN_IN_ALL_SCREEN;

        @Override
        public String getConfigEnumType() {
            return "module_settings_hotkey_policy";
        }
    }
}
