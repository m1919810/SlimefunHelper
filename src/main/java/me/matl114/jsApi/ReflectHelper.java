package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;

import java.util.Arrays;

@ApiMethod
public class ReflectHelper {

    public static <T extends Enum<T>> T getEnumValue(Class<T> enumClass, String name) {
        return Arrays.stream(enumClass.getEnumConstants()).filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }
}
