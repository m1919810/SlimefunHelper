package me.matl114.hacks.modules.task;

import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;


public class ConfigSystem extends BaseModule {
    public static final String[] OPEN_MENU_HOTKEY = new String[]{"hotkeys", "open-menu"};
    public ConfigSystem() {

    }

    public final KeyBindRef keyBind = hotkey(OPEN_MENU_HOTKEY)
        .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_G))
        .registerHotkey(HotKeyUtils.wrapAsHandler(this::openConfigMenu))
        .build();

    public void openConfigMenu(){
        Tasks.openConfigNewStyleScreen();
    }

    public void openConfigScreen(Config config){
        Tasks.openConfigScreen(config);
    }
}
