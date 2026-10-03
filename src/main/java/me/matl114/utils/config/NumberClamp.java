package me.matl114.utils.config;

import java.util.function.Predicate;

public record NumberClamp<T extends Number & Comparable<T>>(T min, T max) implements Predicate<T> {
    @Override
    public boolean test(T t) {
        if (t == null) return false;
        if(min != null && min.compareTo(t) > 0) return false;
        if(max != null && max.compareTo(t) < 0) return false;
        return true;
    }
}
