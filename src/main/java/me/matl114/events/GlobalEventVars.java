package me.matl114.events;

import net.minecraft.util.crash.CrashReport;

public class GlobalEventVars {
    public static CrashReport crashReport = null;
    public static boolean lastRenderNeedDisableGuiLight;

    public static boolean fetchThisTimeGuiLightStatus() {
        if (lastRenderNeedDisableGuiLight) {
            lastRenderNeedDisableGuiLight = false;
            return true;
        }
        return false;
    }
}
