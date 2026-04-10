package me.matl114.managers.config;

import java.util.HashSet;
import java.util.Set;

/**
 * any class or interface implement this interface directly must keep a static method void onLoad(Class) for auto register loading issues
 */
public interface AutoRegisterType {
    Set<Class<? extends AutoRegisterType>> registered = new HashSet<>();
}
