package me.matl114.utils;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class FunctionUtils {
    public static <T> Predicate<T> catchException(Consumer<T> consumer) {
        return (v) -> {
            try {
                consumer.accept(v);
                return true;
            } catch (Throwable e) {
                return false;
            }
        };
    }

    public static <T> Predicate<T> catchException(Runnable runnable) {
        return (v) -> {
            try {
                runnable.run();
                return true;
            } catch (Throwable e) {
                return false;
            }
        };
    }
}
