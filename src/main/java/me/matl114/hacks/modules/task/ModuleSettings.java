package me.matl114.hacks.modules.task;

import java.util.List;
import me.matl114.accessors.events.ChatHudAccess;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.commands.MainCommand;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.presets.single.KeyBindConfigurateWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.StringFormat;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.Debug;
import net.minecraft.text.Text;

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

    public final FlagRef toggleKeysStopVanilla = builder(moduleSettings.add("toggle-keys-stop-vanilla"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef moduleToggleNotify = builder(moduleSettings.add("module-toggle-notify"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final NBTRef<StringFormat> moduleOnNotifyFormat = builder(
                    moduleSettings.add("module-on-notify-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("name"), "&a&l[+] &f{name}", true))
            .build();

    public final NBTRef<StringFormat> moduleOffNotify = builder(
                    moduleSettings.add("module-off-notify-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("name"), "&c&l[-] &f{name}", true))
            .build();

    public final FlagRef moduleToggleCompress =
            flagBuilder(moduleSettings.add("compress-module-toggle-message")).build();

    public final NBTRef<StringFormat> moduleLogMessageFormat = builder(
                    moduleSettings.add("module-log-message-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("module_name", "message"), "&c[{module_name}] &f{message}", true))
            .updateListener(s -> BaseModule.logFormat = s)
            .build();

    public final StringRef moduleCommandPrefix = builder(moduleSettings.add("module-command-prefix"), StringRef.TYPE)
            .defaultValue("!!")
            .updateListener(s -> MainCommand.MAIN_PREFIX = s)
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
                    var focused = mc.currentScreen.getFocused();
                    if (focused instanceof TextFieldAccess) {
                        return true;
                    }
                    if (focused instanceof DrawableWidget widget) {
                        var focus = WidgetUtils.getFocusedWidget(widget);
                        if (WidgetUtils.isInputWidget(WidgetUtils.getFocusedWidget(focus))) {
                            return true;
                        }
                        var list = WidgetUtils.getWidgetHierarchy(widget);
                        if (list.stream().anyMatch(s -> s instanceof KeyBindConfigurateWidget)) {
                            return true;
                        }
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

    public static final String TOGGLE_UNIQUE_ID = "slimefunhelper:module_toggle/";

    public void sendToggleMessage(String message, boolean result) {
        if (checkNull()) return;
        if (moduleToggleNotify.get()) {
            StringFormat format = result ? moduleOnNotifyFormat.get() : moduleOffNotify.get();
            ChatHudAccess access = ChatHudAccess.of(mc.inGameHud.getChatHud());
            String uniqueId = TOGGLE_UNIQUE_ID + message;
            if (moduleToggleCompress.get()) {
                access.clearUniqueMessages(uniqueId);
            }
            access.setUniqueMessageId(uniqueId);
            Debug.chat(format.formatText(Text.translatableWithFallback(message, message)));
            access.setUniqueMessageId(null);
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
