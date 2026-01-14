package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;

@ApiMethod
public class EnumHelper {
    public static <T extends Enum<T>> T getEnum(Class<T> enumClass, String name) {
        return Enum.valueOf(enumClass, name);
    }

    public static <T extends Enum<T>> T getEnum(Class<T> enumClass, int idx) {
        return enumClass.getEnumConstants()[idx];
    }

    public static boolean isEnum(Object what){
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        return Enum.class.isAssignableFrom(clazz);
    }
}
