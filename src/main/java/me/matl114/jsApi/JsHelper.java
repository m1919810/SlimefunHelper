package me.matl114.jsApi;

import java.lang.reflect.Modifier;
import java.util.*;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;

@ApiMethod
public class JsHelper {
    public static <T> T unwrap(Object what, Class<T> type) {
        return JsMacrosBridge.getInstance().unwrap(what, type);
    }

    public static <T extends Enum<T>> T toEnum(Object what, Class<T> type) {
        if (type.isInstance(what)) {
            return type.cast(what);
        } else {
            if (what instanceof String str) {
                return Enum.valueOf(type, str.toUpperCase(Locale.ROOT));
            } else {
                return unwrap(what, type);
            }
        }
    }

    private static WrappingMethod lookupWrappingMethod(String clazzName, String methodName) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            return Arrays.stream(clazz.getMethods())
                    .filter(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()))
                    .filter(m -> m.getParameterCount() == 1)
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst()
                    .map(m -> (WrappingMethod) (s -> {
                        return m.invoke(null, s);
                    }))
                    .orElseGet(() -> m -> {
                        throw new UnsupportedOperationException("Failed to find wrapper for :" + clazz.getSimpleName()
                                + ", use JavaUtils.getHelperFromRaw instead");
                    });
        } catch (Throwable e) {
            Debug.info("Fail to look up wrapping method : " + clazzName + "." + methodName);
            return m -> {
                throw new UnsupportedOperationException(
                        "Failed to find wrapper for :" + clazzName + ", use JavaUtils.getHelperFromRaw instead");
            };
        }
    }

    private static WrappingMethod lookupWrappingConstructor(String clazzName, Class<?> clazz2) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            return Arrays.stream(clazz.getConstructors())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .filter(m -> m.getParameterCount() == 1)
                    .filter(m -> clazz2.isAssignableFrom(m.getParameterTypes()[0]))
                    .findFirst()
                    .map(m -> (WrappingMethod) (m::newInstance))
                    .orElseGet(() -> m -> {
                        throw new UnsupportedOperationException("Failed to find wrapper for :" + clazz.getSimpleName()
                                + ", use JavaUtils.getHelperFromRaw instead");
                    });
        } catch (Throwable e) {
            Debug.info("Fail to look up wrapping constructor : " + clazzName);
            return m -> {
                throw new UnsupportedOperationException(
                        "Failed to find wrapper for :" + clazzName + ", use JavaUtils.getHelperFromRaw instead");
            };
        }
    }

    public static void runOnMainThread(Runnable runnable) {
        MinecraftClient.getInstance().execute(runnable);
    }

    public static <T> T wrap(Object object) throws Throwable {
        return (T) JsMacrosBridge.getInstance().wrap(object);
    }

    public static interface WrappingMethod {
        public Object create(Object args) throws Throwable;
    }

    public static void runSingleRepeat(Object object, String flag) {
        runSingleRepeat(object, flag, true);
    }

    public static void runSingleRepeat(Object object, String flag, boolean debug) {
        throw new UnsupportedOperationException();
    }
}
