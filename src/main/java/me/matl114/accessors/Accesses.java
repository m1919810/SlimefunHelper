package me.matl114.accessors;

public class Accesses {
    public static <T, W> W of(T val, Class<W> clazz) {
        return clazz.cast(val);
    }
}
