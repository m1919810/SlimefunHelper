package me.matl114.utils;

import java.lang.ref.WeakReference;
import java.util.HashSet;

public class CustomClassLoader extends ClassLoader {
    static WeakReference<CustomClassLoader> instance = new WeakReference<>(null);

    public static synchronized CustomClassLoader getInstance() {
        if (instance.get() == null) {
            instance = new WeakReference<>(new CustomClassLoader(CustomClassLoader.class.getClassLoader()));
        }
        return instance.get();
    }

    private final HashSet<String> loadedClassNames = new HashSet<>();

    CustomClassLoader(ClassLoader parent) {
        super(parent);
        Debug.info("Creating new CustomClassLoader");
    }

    public Class<?> defineAccessClass(String name, byte[] bytes) throws ClassFormatError {
        this.loadedClassNames.add(name);
        return this.defineClass(name, bytes);
    }

    public Class loadAccessClass(String name) {
        if (this.loadedClassNames.contains(name)) {
            try {
                return super.loadClass(name, false);
            } catch (ClassNotFoundException var3) {
                throw new RuntimeException(var3);
            }
        } else {
            return null;
        }
    }

    public boolean isClassPresent(String name) {
        if (this.loadedClassNames.contains(name)) {
            return true;
        }
        return super.findLoadedClass(name) != null;
    }

    Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError {
        return this.defineClass(name, bytes, 0, bytes.length, this.getClass().getProtectionDomain());
    }
    //    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException{
    //        return super.loadClass(name, resolve);
    //    }
}
