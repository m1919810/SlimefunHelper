package me.matl114.managers;

import lombok.Getter;
import me.matl114.hacks.*;
import me.matl114.ModConfig;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.SimpleHotKey;
import me.matl114.managers.task.TaskManager;
import me.matl114.managers.task.ToggleManager;
import me.matl114.utils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ClientPlayerEntity;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public class TaskManagers {
    public static void init(){
        KeyCode.init();
    }

    public static SimpleHotKey.InputHandler getToggleHandler(String... path){
        String commonPath = String.join(".", path);
        FlagRef flagRef = toggleManager.getOrRegister(commonPath, false);
        return getToggleHandler(commonPath, flagRef);
    }
    public static Runnable getToggleTask(String... path){
        String commonPath = String.join(".", path);
        FlagRef flagRef = toggleManager.getOrRegister(commonPath, false);
        return ToggleManager.wrapFlagAsToggle(commonPath, flagRef);
    }
    public static SimpleHotKey.InputHandler getToggleHandler(String commonPath, FlagRef flagRef){
        Runnable toggleTask = ToggleManager.wrapFlagAsToggle(commonPath, flagRef);
        return m -> {
            ClientPlayerEntity player= m.getClient().player;
            if(player!=null && HotKeyUtils.isValidState()){
                toggleTask.run();
            }
            return true;
        };
    }


//    private static HashMap<String,Boolean> defaultToggles=new HashMap<>();
    public static final String PREFIX_BUTTON_TOGGLE ="button-toggle";
    public static final String PREFIX_BUTTON_TASKS = "button-task";
    public static final String PREFIX_HOTKEY = "hotkeys-toggle";
    public static final String PREFIX_SIMPLE = "simple-toggle";

    public static final String PREFIX_CONFIG = "toggle";
    public static final String PREFIX_HOTKEY_TASKS = "hotkeys";
    @Deprecated
    @Getter
    private static final ToggleManager toggleManager = ToggleManager.of();

    @Getter
    private static final TaskManager taskManager= TaskManager.of();

}
