package me.matl114.managers.api;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import me.matl114.utils.ReflectUtils;

/**
 * any class or interface implement this interface directly must keep a static method void onLoad(Class) for auto register loading issues
 */
public interface AutoRegisterType {
    Set<Class<? extends AutoRegisterType>> registered = new HashSet<>();

    public static void registerClassSupport(Class<?> clazz) {
        if (AutoRegisterType.class.isAssignableFrom(clazz)) {
            if (!AutoRegisterType.registered.contains(clazz)) {
                try {
                    Method method = ReflectUtils.getMethodsRecursively(clazz, "onLoad", Class.class)
                            .getFirst();
                    method.invoke(null, clazz);
                    AutoRegisterType.registered.add((Class<? extends AutoRegisterType>) clazz);
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Target class which implement AutoRegisterType does not implement public static void onLoad(Class) method");
                }
            }
        }
    }
}
