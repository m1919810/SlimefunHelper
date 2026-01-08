package me.matl114.managers;

import me.matl114.gui.config.ConfigurateNewStyleScreen;
import net.minecraft.client.MinecraftClient;

public class HotKeyUtils {
    private static final Config.FlagRef hotkeyNoScreen = Configs.HOTKEY_CONFIG.getBoolean(Configs.HOTKEY_WORKS_ONLY_WHEN_NOT_AT_SCREEN);
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static boolean isValidState(){
        if(mc.currentScreen == null){
            return true;
        }
        if(hotkeyNoScreen.get()){
            return false;
        }
        return !(mc.currentScreen instanceof ConfigurateNewStyleScreen);
    }
}
