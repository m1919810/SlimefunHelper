package me.matl114.Utils;

public class Utils {
    public static int parseIntOrDefault(String value,int defaultValue){
        try{
            return Integer.parseInt(value);
        }catch (Throwable e){
            return defaultValue;
        }
    }
}
