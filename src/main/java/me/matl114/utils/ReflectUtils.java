package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class ReflectUtils {
    // port from matlib
    /**
     * Sets a field value recursively by searching through the class hierarchy.
     *
     * <p>This method searches for a field with the specified name starting from
     * the target object's class and traversing up the inheritance hierarchy.
     * The field is made accessible before setting the value.
     *
     * @param target The object whose field should be set
     * @param declared The name of the field to set
     * @param value The value to set the field to
     * @return true if the field was found and set successfully, false otherwise
     */
    public static boolean setFieldRecursively(Object target, String declared, Object value) {
        return setFieldRecursively(target, target.getClass(), declared, value);
    }
    /**
     * Sets a field value recursively by searching through a specific class and its superclasses.
     *
     * <p>This method searches for a field with the specified name starting from
     * the given class and traversing up the inheritance hierarchy. The field is
     * made accessible before setting the value.
     *
     * @param target The object whose field should be set
     * @param clazz The class to start searching from
     * @param decleared The name of the field to set
     * @param value The value to set the field to
     * @return true if the field was found and set successfully, false otherwise
     */
    public static boolean setFieldRecursively(Object target, Class clazz, String decleared, Object value) {
        try {
            Field _hasType = clazz.getDeclaredField(decleared);
            _hasType.setAccessible(true);
            _hasType.set(target, value);
            return true;
        } catch (Throwable e) {
        }
        clazz = clazz.getSuperclass();
        if (clazz == null) {
            return false;
        } else {
            return setFieldRecursively(target, clazz, decleared, value);
        }
    }
    /**
     * Gets a field recursively by searching through the class hierarchy.
     *
     * <p>This method searches for a field with the specified name starting from
     * the given class and traversing up the inheritance hierarchy. The field is
     * made accessible before being returned.
     *
     * @param clazz The class to start searching from
     * @param fieldName The name of the field to find
     * @return A Pair containing the field and the class where it was found, or null if not found
     */
    public static Pair<Field, Class> getFieldsRecursively(Class clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return Pair.of(field, clazz);
        } catch (Throwable e) {
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                return null;
            } else {
                return getFieldsRecursively(clazz, fieldName);
            }
        }
    }
    /**
     * Gets all fields recursively from a class, its interfaces, and superclasses.
     *
     * <p>This method collects all declared fields from the given class, all its
     * interfaces, and all superclasses. Each field is made accessible before
     * being added to the list.
     *
     * @param clazz The class to get fields from
     * @return A list of all accessible fields from the class hierarchy
     */
    public static List<Field> getAllFieldsRecursively(Class clazz) {
        List<Field> fieldList = new ArrayList<>();
        if (clazz == null) {
            return fieldList;
        }
        Field[] fields = clazz.getDeclaredFields();
        for (Field f : fields) {
            fieldList.add(f);
            try {
                f.setAccessible(true);
            } catch (Throwable e) {
                continue;
            }
        }
        for (Class<?> classes : clazz.getInterfaces()) {
            fieldList.addAll(getAllFieldsRecursively(classes));
        }
        fieldList.addAll(getAllFieldsRecursively(clazz.getSuperclass()));
        return fieldList;
    }
    /**
     * Gets all methods recursively from a class, its interfaces, and superclasses.
     *
     * <p>This method collects all declared methods from the given class, all its
     * interfaces, and all superclasses. Each method is made accessible before
     * being added to the list.
     *
     * @param clazz The class to get methods from
     * @return A list of all accessible methods from the class hierarchy
     */
    public static List<Method> getAllMethodsRecursively(Class clazz) {
        List<Method> fieldList = new ArrayList<>();
        if (clazz == null) {
            return fieldList;
        }
        Method[] fields = clazz.getDeclaredMethods();
        for (Method f : fields) {
            fieldList.add(f);
            try {
                f.setAccessible(true);
            } catch (Throwable e) {
                continue;
            }
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            // should include abstract methods as well ,
            fieldList.addAll(getAllMethodsRecursively(iface));
        }
        fieldList.addAll(getAllMethodsRecursively(clazz.getSuperclass()));
        return fieldList;
    }
    /**
     * Gets all default methods recursively from an interface and its superinterfaces.
     *
     * <p>This method collects all default methods, private methods, and static methods
     * from the given interface and all its superinterfaces.
     *
     * @param iface The interface to get methods from
     * @return A list of all default, private, and static methods from the interface hierarchy
     */
    public static List<Method> getAllDefaultMethodRecursively(Class iface) {
        List<Method> methodList = new ArrayList<>();
        for (Method method : iface.getMethods()) {
            int mod = method.getModifiers();
            if (method.isDefault() || Modifier.isPrivate(mod) || Modifier.isStatic(mod)) { // 只添加 在当前类已经实现的 方法
                try {
                    methodList.add(method);
                } catch (Throwable ignored) {
                }
            }
        }
        for (var iiface : iface.getInterfaces()) {
            methodList.addAll(getAllDefaultMethodRecursively(iiface));
        }
        return methodList;
    }

    /**
     * Gets all directly implemented interfaces recursively from a class and its superclasses.
     *
     * <p>This method returns only the directly implemented interfaces, not the
     * superinterfaces of those interfaces.
     *
     * @param clazz The class to get interfaces from
     * @return A list of all directly implemented interfaces from the class hierarchy
     */
    public static List<Class> getAllInterfacesRecursively(Class clazz) {
        List<Class> fieldList = new ArrayList<>();
        if (clazz == null) {
            return fieldList;
        }
        Class[] fields = clazz.getInterfaces();
        for (Class f : fields) {
            fieldList.add(f);
        }
        fieldList.addAll(getAllInterfacesRecursively(clazz.getSuperclass()));
        return fieldList;
    }
    /**
     * Gets all assignable interfaces recursively from a class and its superclasses.
     *
     * <p>This method returns all interfaces that can be assigned from the given class,
     * including superinterfaces, using a Set to avoid duplicates.
     *
     * @param clazz The class to get assignable interfaces from
     * @return A list of all assignable interfaces from the class hierarchy
     */
    public static List<Class> getAllAssignableInterface(Class clazz) {
        Set<Class> fieldList = new HashSet<>();
        if (clazz == null) {
            return fieldList.stream().toList();
        }
        Class[] fields = clazz.getInterfaces();
        for (Class f : fields) {
            fieldList.addAll(getAllAssignableInterface(f));
        }
        fieldList.addAll(getAllAssignableInterface(clazz.getSuperclass()));
        return fieldList.stream().toList();
    }
    /**
     * Gets all superclasses recursively from a class.
     *
     * <p>This method traverses up the inheritance hierarchy and collects all
     * superclasses, including the class itself.
     *
     * @param clazz The class to get superclasses from
     * @return A list of all superclasses including the class itself
     */
    public static List<Class> getAllSuperClassRecursively(Class clazz) {
        List<Class> fieldList = new ArrayList<>();
        while (clazz != null) {
            fieldList.add(clazz);
            clazz = clazz.getSuperclass();
        }
        ;
        return fieldList;
    }
    /**
     * Gets a method recursively by searching through the class hierarchy.
     *
     * <p>This method searches for a method with the specified name and parameter types
     * starting from the given class, traversing through interfaces and superclasses.
     * The method is made accessible before being returned.
     *
     * @param clazz The class to start searching from
     * @param fieldName The name of the method to find
     * @param parameterTypes The parameter types of the method
     * @return A Pair containing the method and the class where it was found, or null if not found
     */
    public static Pair<Method, Class> getMethodsRecursively(Class clazz, String fieldName, Class... parameterTypes) {
        try {
            Method field = clazz.getDeclaredMethod(fieldName, parameterTypes);
            field.setAccessible(true);
            return Pair.of(field, clazz);
        } catch (Throwable e) {
            for (var itf : clazz.getInterfaces()) {
                var re = getMethodsRecursively(itf, fieldName, parameterTypes);
                if (re != null) {
                    return re;
                }
            }
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                return null;
            } else {
                return getMethodsRecursively(clazz, fieldName, parameterTypes);
            }
        }
    }
    /**
     * Gets a method by name recursively by searching through the class hierarchy.
     *
     * <p>This method searches for a method with the specified name starting from
     * the given class and traversing up the inheritance hierarchy. The method is
     * made accessible before being returned.
     *
     * @param clazz The class to start searching from
     * @param fieldName The name of the method to find
     * @return A Pair containing the method and the class where it was found, or null if not found
     */
    public static Pair<Method, Class> getMethodsByName(Class clazz, String fieldName) {

        Method[] field = clazz.getDeclaredMethods();
        for (Method m : field) {
            try {
                if (m.getName().equals(fieldName)) {
                    m.setAccessible(true);
                    return Pair.of(m, clazz);
                }
            } catch (Throwable e) {
            }
        }

        clazz = clazz.getSuperclass();
        if (clazz == null) {
            return null;
        } else {
            return getMethodsByName(clazz, fieldName);
        }
    }

    public static Map<Field, Object> dumpObject(Object value) {
        List<Field> fields = getAllFieldsRecursively(value.getClass());
        Map<Field, Object> re = new HashMap<>();
        for (var f : fields) {
            if (!Modifier.isStatic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    re.put(f, f.get(value));
                } catch (Throwable e) {
                    re.put(f, "Error while dumping: " + e.getMessage());
                }
            }
        }
        return re;
    }

    public static Map<Field, Object> dumpObjectRecursively(Object value) {
        // todo left as not done
        return dumpObject(value);
    }

    /**
     * Checks if a string represents a Java primitive type.
     *
     * @param val The string to check
     * @return true if the string represents a primitive type, false otherwise
     */
    public static boolean isPrimitiveType(String val) {
        return switch (val) {
            case "int", "void", "boolean", "long", "double", "float", "short", "byte", "char" -> true;
            default -> false;
        };
    }
    /**
     * Checks if a class name represents a boxed primitive type.
     *
     * @param className The class name to check (in internal format, e.g., "java/lang/Integer")
     * @return true if the class name represents a boxed primitive type, false otherwise
     */
    public static boolean isBoxedPrimitive(String className) {
        return switch (className) {
            case "java/lang/Integer",
                    "java/lang/Boolean",
                    "java/lang/Long",
                    "java/lang/Double",
                    "java/lang/Float",
                    "java/lang/Short",
                    "java/lang/Byte",
                    "java/lang/Character",
                    "java/lang/Void" -> true;
            default -> false;
        };
    }
    /**
     * Converts a boxed primitive class name to its unboxed primitive type name.
     *
     * @param boxedClassName The boxed primitive class name (in internal format)
     * @return The unboxed primitive type name
     * @throws IllegalArgumentException if the class name is not a boxed primitive type
     */
    public static String getUnboxedClassName(String boxedClassName) {
        return switch (boxedClassName) {
            case "java/lang/Integer" -> "int";
            case "java/lang/Boolean" -> "boolean";
            case "java/lang/Long" -> "long";
            case "java/lang/Double" -> "double";
            case "java/lang/Float" -> "float";
            case "java/lang/Short" -> "short";
            case "java/lang/Byte" -> "byte";
            case "java/lang/Character" -> "char";
            case "java/lang/Void" -> "void";
            default -> throw new IllegalArgumentException("Not a boxed primitive class: " + boxedClassName);
        };
    }

    /**
     * Converts a boxed primitive class name to its unboxed primitive type name.
     *
     * @param boxedClassName The boxed primitive class name (in internal format)
     * @return The unboxed primitive type name
     * @throws IllegalArgumentException if the class name is not a boxed primitive type
     */
    public static Class<?> getUnboxedClass(String boxedClassName) {
        return switch (boxedClassName) {
            case "java/lang/Integer" -> int.class;
            case "java/lang/Boolean" -> boolean.class;
            case "java/lang/Long" -> long.class;
            case "java/lang/Double" -> double.class;
            case "java/lang/Float" -> float.class;
            case "java/lang/Short" -> short.class;
            case "java/lang/Byte" -> byte.class;
            case "java/lang/Character" -> char.class;
            case "java/lang/Void" -> void.class;
            default -> throw new IllegalArgumentException("Not a boxed primitive class: " + boxedClassName);
        };
    }
    /**
     * Converts a primitive type name to its boxed class name.
     *
     * @param primitive The primitive type name
     * @return The boxed class name (in internal format)
     * @throws IllegalArgumentException if the primitive type is not supported
     */
    public static String getBoxedClassName(String primitive) {
        switch (primitive) {
            case "int":
                return "java/lang/Integer";
            case "boolean":
                return "java/lang/Boolean";
            case "long":
                return "java/lang/Long";
            case "double":
                return "java/lang/Double";
            case "float":
                return "java/lang/Float";
            case "short":
                return "java/lang/Short";
            case "byte":
                return "java/lang/Byte";
            case "char":
                return "java/lang/Character";
            case "void":
                return "java/lang/Void"; // 注意：void 也有对应的包装类 Void
            default:
                throw new IllegalArgumentException("Unsupported primitive type: " + primitive);
        }
    }

    /**
     * Converts a primitive type name to its boxed class name.
     *
     * @param primitive The primitive type name
     * @return The boxed class name (in internal format)
     * @throws IllegalArgumentException if the primitive type is not supported
     */
    public static Class<?> getBoxedClass(String primitive) {
        switch (primitive) {
            case "int":
                return Integer.class;
            case "boolean":
                return Boolean.class;
            case "long":
                return Long.class;
            case "double":
                return Double.class;
            case "float":
                return Float.class;
            case "short":
                return Short.class;
            case "byte":
                return Byte.class;
            case "char":
                return Character.class;
            case "void":
                return Void.class; // 注意：void 也有对应的包装类 Void
            default:
                throw new IllegalArgumentException("Unsupported primitive type: " + primitive);
        }
    }
    /**
     * Gets a method by name and parameter types recursively.
     *
     * <p>This method searches for a method with the specified name and parameter types
     * starting from the given class and traversing up the inheritance hierarchy.
     * Parameter types are matched using exact type equality or assignability.
     *
     * @param clazz The class to start searching from
     * @param methodName The name of the method to find
     * @param parameterTypes The parameter types of the method
     * @return A Pair containing the method and the class where it was found, or null if not found
     */
    public static Pair<Method, Class> getMethodByParams(Class clazz, String methodName, Class[] parameterTypes) {
        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method m : methods) {
                Class[] params = m.getParameterTypes();
                if (!methodName.equals(m.getName())) {
                    continue;
                }
                boolean match = true;
                if (params.length == parameterTypes.length) {
                    int len = params.length;
                    for (int i = 0; i < len; i++) {
                        if (params[i] == parameterTypes[i] || params[i].isAssignableFrom(parameterTypes[i])) {
                            continue;
                        } else {
                            match = false;
                        }
                    }
                } else {
                    match = false;
                }
                if (match) {
                    m.setAccessible(true);

                    return Pair.of(m, clazz);
                }
            }
        } catch (Throwable e) {
        }
        clazz = clazz.getSuperclass();
        if (clazz == null) {
            return null;
        }
        return getMethodByParams(clazz, methodName, parameterTypes);
    }
    /**
     * Gets a constructor by parameter types.
     *
     * <p>This method searches for a constructor with the specified parameter types
     * in the given class. Parameter types are matched using exact type equality
     * or assignability. The constructor is made accessible before being returned.
     *
     * @param clazz The class to search for constructors
     * @param parameterTypes The parameter types of the constructor
     * @return The matching constructor, or null if not found
     */
    public static Constructor getConstructorByParams(Class clazz, Class... parameterTypes) {
        Constructor[] constructors = clazz.getDeclaredConstructors();
        for (Constructor c : constructors) {
            Class[] params = c.getParameterTypes();
            boolean match = true;
            if (params.length == parameterTypes.length) {
                int len = params.length;
                for (int i = 0; i < len; i++) {
                    if (params[i] == parameterTypes[i] || params[i].isAssignableFrom(parameterTypes[i])) {

                    } else match = false;
                }
            } else {
                match = false;
            }
            if (match) {
                c.setAccessible(true);
                return c;
            }
        }
        return null;
    }
    /**
     * Checks if a class is extended from a class whose name ends with the specified suffix.
     *
     * <p>This method recursively checks if the given class or any of its superclasses
     * has a name that ends with the specified suffix.
     *
     * @param clazz The class to check
     * @param s The suffix to check for
     * @return true if the class or any superclass name ends with the suffix, false otherwise
     */
    public static boolean isExtendedFrom(Class clazz, String s) {
        if (clazz == null) {
            return false;
        } else {
            if (clazz.getName().endsWith(s)) {
                return true;
            } else {
                return isExtendedFrom(clazz.getSuperclass(), s);
            }
        }
    }
    /**
     * Gets the first field that is assignable to the specified type.
     *
     * <p>This method searches for a field whose type is assignable to the specified
     * field type, starting from the given class and traversing up the inheritance
     * hierarchy. The field is made accessible before being returned.
     *
     * @param clazz The class to start searching from
     * @param fieldType The type that the field should be assignable to
     * @return The first matching field, or null if not found
     */
    public static Field getFirstFitField(Class<?> clazz, Class<?> fieldType) {
        try {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        } catch (Exception e) {
        }
        if (clazz.getSuperclass().getSuperclass() != null) {
            return getFirstFitField(clazz.getSuperclass(), fieldType);
        }
        return null;
    }
    /**
     * Gets the first field that is assignable to the specified type and matches the static modifier.
     *
     * <p>This method searches for a field whose type is assignable to the specified
     * field type and has the specified static modifier, starting from the given class
     * and traversing up the inheritance hierarchy. The field is made accessible before being returned.
     *
     * @param clazz The class to start searching from
     * @param fieldType The type that the field should be assignable to
     * @param isStatic Whether the field should be static (true) or non-static (false)
     * @return The first matching field, or null if not found
     */
    public static Field getFirstFitField(Class<?> clazz, Class<?> fieldType, boolean isStatic) {
        try {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if ((Modifier.isStatic(field.getModifiers()) == isStatic)
                        && fieldType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        } catch (Exception e) {
        }
        if (clazz.getSuperclass().getSuperclass() != null) {
            return getFirstFitField(clazz.getSuperclass(), fieldType);
        }
        return null;
    }
    /**
     * Gets the value of the first field that is assignable to the specified type.
     *
     * <p>This method finds the first field whose type is assignable to the specified
     * field type and returns its value from the given object.
     *
     * @param object The object to get the field value from
     * @param clazz The class to search for fields in
     * @param fieldType The type that the field should be assignable to
     * @return The field value, or null if not found or an error occurs
     */
    public static Object getFieldValue(Object object, Class<?> clazz, Class<?> fieldType) {
        try {
            if (object != null && !clazz.isInstance(object)) {
                return false;
            }
            Field field = getFirstFitField(clazz, fieldType);
            field.setAccessible(true);
            return field.get(object);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Gets all fields that are assignable to the specified type recursively.
     *
     * <p>This method collects all fields whose type is assignable to the specified
     * field type from the given class and all its superclasses. Each field is made
     * accessible before being added to the array.
     *
     * @param clazz The class to search for fields in
     * @param fieldType The type that the fields should be assignable to
     * @return An array of all matching fields
     */
    public static Field[] getAllFitFields(Class<?> clazz, Class<?> fieldType) {
        if (clazz == null) {
            return new Field[0];
        }
        List<Field> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (fieldType.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        fields.addAll(
                Arrays.stream(getAllFitFields(clazz.getSuperclass(), fieldType)).toList());
        return fields.toArray(Field[]::new);
    }
    /**
     * Gets the value of the first field that is assignable to the specified type and matches the static modifier.
     *
     * <p>This method finds the first field whose type is assignable to the specified
     * field type and has the specified static modifier, then returns its value from the given object.
     *
     * @param object The object to get the field value from
     * @param clazz The class to search for fields in
     * @param fieldType The type that the field should be assignable to
     * @param isStatic Whether the field should be static (true) or non-static (false)
     * @return The field value, or null if not found or an error occurs
     */
    public static Object getFieldValue(Object object, Class<?> clazz, Class<?> fieldType, boolean isStatic) {
        try {
            if (object != null && !clazz.isInstance(object)) {
                return false;
            }
            Field field = getFirstFitField(clazz, fieldType, isStatic);
            field.setAccessible(true);
            return field.get(object);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Sets the value of the first field that is assignable to the specified type.
     *
     * <p>This method finds the first field whose type is assignable to the specified
     * field type and sets its value in the given object.
     *
     * @param object The object to set the field value in
     * @param tar The value to set
     * @param clazz The class to search for fields in
     * @param fieldType The type that the field should be assignable to
     * @return true if the field was found and set successfully, false otherwise
     */
    public static boolean setFirstFitField(Object object, Object tar, Class<?> clazz, Class<?> fieldType) {
        try {
            if (object != null && !clazz.isInstance(object)) {
                return false;
            }
            Field field = getFirstFitField(clazz, fieldType);
            field.setAccessible(true);
            field.set(object, tar);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Sets the value of the first field that is assignable to the specified type and matches the static modifier.
     *
     * <p>This method finds the first field whose type is assignable to the specified
     * field type and has the specified static modifier, then sets its value in the given object.
     *
     * @param object The object to set the field value in
     * @param tar The value to set
     * @param clazz The class to search for fields in
     * @param fieldType The type that the field should be assignable to
     * @param isStatic Whether the field should be static (true) or non-static (false)
     * @return true if the field was found and set successfully, false otherwise
     */
    public static boolean setFirstFitField(
            Object object, Object tar, Class<?> clazz, Class<?> fieldType, boolean isStatic) {
        try {
            if (object != null && !clazz.isInstance(object)) {
                return false;
            }
            Field field = getFirstFitField(clazz, fieldType, isStatic);
            field.setAccessible(true);
            field.set(object, tar);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Copies the value of the first matching field from one object to another.
     *
     * <p>This method finds the first field whose type is assignable to the specified
     * field type, gets its value from the source object, and sets it in the target object.
     *
     * @param to The target object to copy the field value to
     * @param from The source object to copy the field value from
     * @param clazz The class to search for fields in
     * @param fieldType The type that the field should be assignable to
     * @return true if the field was found and copied successfully, false otherwise
     */
    public static boolean copyFirstField(Object to, Object from, Class<?> clazz, Class<?> fieldType) {
        return setFirstFitField(to, getFieldValue(from, clazz, fieldType), clazz, fieldType);
    }

    /**
     * Finds a class by its fully qualified name.
     *
     * @param name The fully qualified class name
     * @return The Class object, or null if the class cannot be found
     */
    public static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static Field getField(Class<?> clazz, String name) {
        try {
            return clazz.getField(name);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static Field getFieldPrivate(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets a public method from a class.
     *
     * @param clazz The class to search for the method
     * @param name The name of the method
     * @param clazzs The parameter types of the method
     * @return The Method object, or null if the method cannot be found
     */
    public static Method getMethod(Class<?> clazz, String name, Class<?>... clazzs) {
        try {
            return clazz.getMethod(name, clazzs);
        } catch (Throwable e) {
            return null;
        }
    }
    /**
     * Gets a declared method (including private methods) from a class.
     *
     * @param clazz The class to search for the method
     * @param name The name of the method
     * @param clazzes The parameter types of the method
     * @return The Method object, or null if the method cannot be found
     */
    public static Method getMethodPrivate(Class<?> clazz, String name, Class<?>... clazzes) {
        try {
            Method method = clazz.getDeclaredMethod(name, clazzes);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets a MethodHandle for a public method.
     *
     * @param clazz The class to search for the method
     * @param name The name of the method
     * @param argments The parameter types of the method
     * @return The MethodHandle, or null if the method cannot be found
     */
    public static MethodHandle getMethodHandle(Class<?> clazz, String name, Class<?>... argments) {
        try {
            Method method = clazz.getMethod(name, argments);
            return MethodHandles.lookup().unreflect(method);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets a MethodHandle for a declared method (including private methods).
     *
     * @param clazz The class to search for the method
     * @param name The name of the method
     * @param argments The parameter types of the method
     * @return The MethodHandle, or null if the method cannot be found
     */
    public static MethodHandle getMethodHandlePrivate(Class<?> clazz, String name, Class<?>... argments) {
        try {
            Method method = clazz.getDeclaredMethod(name, argments);
            return MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).unreflect(method);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets a VarHandle for a public field.
     *
     * @param clazz The class to search for the field
     * @param name The name of the field
     * @return The VarHandle, or null if the field cannot be found
     */
    public static VarHandle getVarHandle(Class<?> clazz, String name) {
        try {
            Field field = clazz.getField(name);
            return MethodHandles.lookup().unreflectVarHandle(field);
        } catch (Throwable e) {
            return null;
        }
    }

    public static VarHandle getVarHandle(Field field) {
        field.setAccessible(true);
        try {
            return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup())
                    .unreflectVarHandle(field);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets a VarHandle for a declared field (including private fields).
     *
     * @param clazz The class to search for the field
     * @param name The name of the field
     * @return The VarHandle, or null if the field cannot be found
     */
    public static VarHandle getVarHandlePrivate(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            return MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).unreflectVarHandle(field);
        } catch (Throwable e) {
            return null;
        }
    }
    /**
     * Gets a MethodHandle for a declared method using private lookup.
     *
     * @param clazz The class to search for the method
     * @param name The name of the method
     * @param args The parameter types of the method
     * @return The MethodHandle, or null if the method cannot be found
     */
    public static MethodHandle getPrivateMethodHandle(Class<?> clazz, String name, Class<?>... args) {
        try {
            Method method = clazz.getDeclaredMethod(name, args);
            return MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).unreflect(method);
        } catch (Throwable e) {
            return null;
        }
    }

    public static final Map<String, Integer> objectInvocationIndex = Map.of(
            "getClass",
            -1,
            "hashCode",
            -2,
            "equals",
            -3,
            "clone",
            -4,
            "toString",
            -5,
            "notify",
            -6,
            "notifyAll",
            -7,
            "wait",
            -8,
            "wait0",
            -9,
            "finalize",
            -10);

    /**
     * Checks if a method is a base Object method.
     *
     * @param method The method to check
     * @return true if the method is a base Object method, false otherwise
     */
    public static boolean isBaseMethod(Method method) {
        return objectInvocationIndex.containsKey(method.getName()); // Object.class.equals(method.getDeclaringClass());
    }
    /**
     * Gets the index of a base Object method.
     *
     * @param method The method to get the index for
     * @return The index of the base method
     * @throws IllegalArgumentException if the method is not a base Object method
     */
    public static int getBaseMethodIndex(Method method) {
        String methodName = method.getName();
        if (objectInvocationIndex.containsKey(methodName)) {
            return objectInvocationIndex.get(methodName);
        } else {
            throw new IllegalArgumentException("Not a base Method!");
        }
    }
    /**
     * Invokes a base Object method by its index.
     *
     * <p>This method provides a way to invoke Object methods without using reflection.
     * The index corresponds to the method's position in the objectInvocationIndex map.
     *
     * @param target The object to invoke the method on
     * @param index The index of the base method to invoke
     * @param args The arguments to pass to the method
     * @return The result of the method invocation, or null for void methods
     * @throws IllegalArgumentException if the index is not valid or the method is not supported
     */
    public static Object invokeBaseMethod(Object target, int index, Object[] args) {
        switch (index) {
            case -1:
                return target.getClass();
            case -2:
                return target.hashCode();
            case -3:
                return target.equals(args[0]);
            case -4:
                throw new IllegalStateException("clone method not supported");
            case -5:
                return target.toString();
            case -6:
                target.notify();
                return null;
            case -7:
                target.notifyAll();
                return null;
            case -8:
                try {
                    switch (args.length) {
                        case 0:
                            target.wait();
                            return null;
                        case 1:
                            target.wait((Long) args[0]);
                            return null;
                        case 2:
                            target.wait();
                            return null;
                        default:
                            throw new IllegalArgumentException("Wrong argument count for wait!");
                    }
                } catch (InterruptedException e) {
                }
                return null;
            case -9:
                throw new IllegalArgumentException("wait0 method not supported");
            case -10:
                throw new IllegalArgumentException("finalize method not supported");
        }
        return null;
    }

    /**
     * Creates a map of enum constants by their names.
     *
     * @param clazz The enum class
     * @return A map where keys are enum constant names and values are the enum constants
     */
    public static <T extends Enum> Map<String, T> getEnumMap(Class<T> clazz) {
        return Arrays.stream(clazz.getEnumConstants()).collect(Collectors.toMap(i -> ((Enum) i).name(), s -> (T) s));
    }
}
