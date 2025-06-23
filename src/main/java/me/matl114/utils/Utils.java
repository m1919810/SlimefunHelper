package me.matl114.utils;

import me.matl114.SlimefunHelper;
import net.minecraft.util.Identifier;


public class Utils {
    public static int parseIntOrDefault(String value,int defaultValue){
        try{
            return Integer.parseInt(value);
        }catch (Throwable e){
            return defaultValue;
        }
    }

    public static Identifier getNamespaceKey(String id) {
        return new Identifier(SlimefunHelper.MOD_ID, id);
    }
}
