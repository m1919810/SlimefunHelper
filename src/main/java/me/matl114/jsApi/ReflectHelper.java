package me.matl114.jsApi;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@ApiMethod
public class ReflectHelper {

    public static <T extends Enum<T>> T getEnumValue(Class<T> enumClass, String name) {
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static <T extends Enum<T>> T getEnumParameter(Class<?> clazz, int pos, String value, int constructorId) {
        Constructor<?> con = clazz.getConstructors()[constructorId];
        return Enum.valueOf((Class<T>) con.getParameterTypes()[pos], value);
    }

    public static Class<?> getParameterType(Class<?> clazz, int pos, int constructorId) {
        return clazz.getConstructors()[constructorId].getParameterTypes()[pos];
    }

    public static List<Method> getMethods(Class<?> clazz, String methodName) {
        return Arrays.stream(clazz.getMethods())
                .filter(s -> Objects.equals(s.getName(), methodName))
                .toList();
    }

    public static <T extends Enum<T>> T getEnumParameter(Class<?> clazz, int pos, String value) {
        return Arrays.stream(clazz.getConstructors())
                .filter(con ->
                        con.getParameterCount() > pos && Enum.class.isAssignableFrom(con.getParameterTypes()[pos]))
                .findAny()
                .map(cls -> Enum.valueOf((Class<T>) (cls.getParameterTypes()[pos]), value))
                .get();
    }

    public static List<String> getEnumInfo(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        return Arrays.stream(clazz.getEnumConstants())
                .map(s -> ((Enum) s).name())
                .toList();
    }

    public static boolean isEnum(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        return Enum.class.isAssignableFrom(clazz);
    }

    public static void logClassInfo(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        Debug.chat(Text.literal("=== " + clazz.getSimpleName() + "的信息 ===").formatted(Formatting.YELLOW));
        String type;
        Debug.chat(Text.literal("类型: " + Modifier.toString(clazz.getModifiers())));
        Debug.chat(Text.literal("父类: " + clazz.getSuperclass()));
        Debug.chat(Text.literal("接口: " + Arrays.asList(clazz.getInterfaces())));
        Debug.chat(
                Text.literal("=== " + getClassNameForLog(clazz) + " 的构造器信息 ===").formatted(Formatting.GREEN));

        for (var con : clazz.getDeclaredConstructors()) {
            String str = getMethodInfo(con);
            Debug.chat(str);
            Debug.chat(Text.literal("=========").formatted(Formatting.GREEN));
        }
    }

    public static List<String> getClassInfo(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();

        return Arrays.stream(clazz.getDeclaredConstructors())
                .map(ReflectHelper::getMethodInfo)
                .toList();
    }

    private static String getClassNameForLog(Type type) {
        if (type instanceof Class clazz) {
            String className = clazz.getSimpleName();
            if (clazz.isEnum()) {
                className += "(Enum)";
            }
            return className;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            StringBuilder sb = new StringBuilder();

            // 原始类型，如 List
            Type rawType = parameterizedType.getRawType();
            sb.append(getClassNameForLog(rawType));

            // 类型参数，如 <String>
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length > 0) {
                sb.append("<");
                for (int i = 0; i < typeArguments.length; i++) {
                    sb.append(getClassNameForLog(typeArguments[i]));
                    if (i < typeArguments.length - 1) {
                        sb.append(", ");
                    }
                }
                sb.append(">");
            }
            return sb.toString();
        } else if (type instanceof TypeVariable) {
            // 类型变量，如 T、E
            return ((TypeVariable<?>) type).getName();
        } else if (type instanceof WildcardType) {
            // 通配符，如 ?、? extends T、? super T
            WildcardType wildcardType = (WildcardType) type;
            StringBuilder sb = new StringBuilder("?");

            Type[] upperBounds = wildcardType.getUpperBounds();
            Type[] lowerBounds = wildcardType.getLowerBounds();

            if (lowerBounds.length > 0) {
                sb.append(" super ");
                for (Type bound : lowerBounds) {
                    sb.append(getClassNameForLog(bound));
                }
            } else if (upperBounds.length > 0 && !(upperBounds.length == 1 && upperBounds[0] == Object.class)) {
                sb.append(" extends ");
                for (int i = 0; i < upperBounds.length; i++) {
                    sb.append(getClassNameForLog(upperBounds[i]));
                    if (i < upperBounds.length - 1) {
                        sb.append(" & ");
                    }
                }
            }
            return sb.toString();
        } else if (type instanceof GenericArrayType) {
            // 泛型数组，如 T[]
            GenericArrayType arrayType = (GenericArrayType) type;
            return getClassNameForLog(arrayType.getGenericComponentType()) + "[]";
        } else {
            // 其他情况，返回类型名称
            return type.getTypeName();
        }
    }

    public static void logMethodsInfo(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        Debug.chat(
                Text.literal("=== " + getClassNameForLog(clazz) + " 的方法信息 ===").formatted(Formatting.GREEN));
        for (var method : clazz.getMethods()) {
            String str = getMethodInfo(method);
            Debug.chat(str);
            Debug.chat(Text.literal("=========").formatted(Formatting.GREEN));
        }
    }

    public static List<String> getMethodsInfo(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();

        return Arrays.stream(clazz.getMethods())
                .map(ReflectHelper::getMethodInfo)
                .toList();
    }

    public static void logPrivateMethodsInfo(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        Debug.chat(Text.literal("=== " + getClassNameForLog(clazz) + " 的私有方法信息 ===")
                .formatted(Formatting.GREEN));
        for (var method : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                String str = getMethodInfo(method);
                Debug.chat(str);
                Debug.chat(Text.literal("=========").formatted(Formatting.GREEN));
            }
        }
    }

    public static List<String> getPrivateMethodInfo(Object what) {
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();

        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(s -> !Modifier.isPublic(s.getModifiers()))
                .map(ReflectHelper::getMethodInfo)
                .toList();
    }

    public static String getMethodInfo(Executable constructor) {
        StringBuilder sb = new StringBuilder();
        // 修饰符
        sb.append(Modifier.toString(constructor.getModifiers())).append(" ");
        // 构造器名
        // 构建泛型
        sb.append(getTypeParametersPrefix(constructor));
        sb.append(constructor.getAnnotatedReturnType().getType().getTypeName()).append(" ");

        sb.append(constructor.getName());
        // 参数列表
        sb.append("(");
        Type[] paramTypes = constructor.getGenericParameterTypes();
        Parameter[] parameters = constructor.getParameters();
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append(getClassNameForLog(paramTypes[i])).append(" ");
            if (i < parameters.length && parameters[i].isNamePresent()) {
                sb.append(parameters[i].getName());
            } else {
                sb.append("p").append(i);
            }
            if (i < paramTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");

        // 异常信息
        Type[] exceptionTypes = constructor.getExceptionTypes();
        if (exceptionTypes.length > 0) {
            sb.append(" throws ");
            for (int i = 0; i < exceptionTypes.length; i++) {
                sb.append(getClassNameForLog(exceptionTypes[i]));
                if (i < exceptionTypes.length - 1) {
                    sb.append(", ");
                }
            }
        }
        return sb.toString();
    }

    public static String getFieldInfo(Field field) {
        StringBuilder sb = new StringBuilder();
        // 修饰符
        sb.append(Modifier.toString(field.getModifiers())).append(" ");
        // 构造器名
        // 构建泛型
        sb.append(field.getAnnotatedType().getType().getTypeName()).append(" ");
        sb.append(field.getName());
        return sb.toString();
    }

    private static String getTypeParametersPrefix(Executable executable) {
        TypeVariable<?>[] typeParams = executable.getTypeParameters();
        if (typeParams.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<");
        for (int i = 0; i < typeParams.length; i++) {
            sb.append(typeParams[i].getName());
            if (i < typeParams.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("> ");
        return sb.toString();
    }

    private static boolean canCastTo(Object a, Class<?> b) {
        if (isPrimitiveType(b.getName())) {
            return a != null && (a.getClass() == b || getBoxedClass(b.getName()).isAssignableFrom(a.getClass()));
        } else if (isBoxedPrimitive(b.getName())) {
            return a == null
                    || b.isAssignableFrom(a.getClass())
                    || b.isAssignableFrom(getBoxedClass(a.getClass().getName()));
        } else {
            return a == null || b.isAssignableFrom(a.getClass());
        }
    }

    private static boolean canClassCastTo(Class a, Class<?> b) {
        if (isPrimitiveType(b.getName())) {
            return a == null || (a == b || getBoxedClass(b.getName()).isAssignableFrom(a));
        } else if (isBoxedPrimitive(b.getName())) {
            return a == null || b.isAssignableFrom(a) || b.isAssignableFrom(getBoxedClass(a.getName()));
        } else {
            return a == null || b.isAssignableFrom(a);
        }
    }

    public static boolean isPrimitiveType(String val) {
        return switch (val) {
            case "int", "void", "boolean", "long", "double", "float", "short", "byte", "char" -> true;
            default -> false;
        };
    }

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

    public static List<Method> findMethodByType(Object a, String name, Class... b) {
        return Arrays.stream(a.getClass().getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getName().equals(name))
                .filter(m -> m.getParameterCount() == b.length)
                .filter(m -> {
                    for (int i = 0; i < b.length; i++) {
                        if (!canClassCastTo(b[i], m.getParameterTypes()[i])) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    public static List<Method> findMethodByParam(Object a, String name, Object... b) {
        return Arrays.stream(a.getClass().getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getName().equals(name))
                .filter(m -> m.getParameterCount() == b.length)
                .filter(m -> {
                    for (int i = 0; i < b.length; i++) {
                        if (!canCastTo(b[i], m.getParameterTypes()[i])) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    public static List<Method> findPrivateMethodByParam(Object a, String name, Object... b) {
        return Arrays.stream(a.getClass().getDeclaredMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getName().equals(name))
                .filter(m -> m.getParameterCount() == b.length)
                .filter(m -> {
                    for (int i = 0; i < b.length; i++) {
                        if (!canCastTo(b[i], m.getParameterTypes()[i])) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    public static Object invoke(Object a, String name, Object... b) throws Throwable {
        Method m = findMethodByParam(a, name, b).get(0);
        return m.invoke(a, b);
    }
}
