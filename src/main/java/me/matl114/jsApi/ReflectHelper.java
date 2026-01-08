package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@ApiMethod
public class ReflectHelper {

    public static <T extends Enum<T>> T getEnumValue(Class<T> enumClass, String name) {
        return Arrays.stream(enumClass.getEnumConstants()).filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }

    public static <T extends Enum<T>> T getEnumParameter(Class<?> clazz, int pos, String value, int constructorId){
        Constructor<?> con = clazz.getConstructors()[constructorId];
        return Enum.valueOf((Class<T>) con.getParameterTypes()[pos], value);
    }

    public static Class<?> getParameterType(Class<?> clazz, int pos, int constructorId){
        return clazz.getConstructors()[constructorId].getParameterTypes()[pos];
    }

    public static List<Method> getMethods(Class<?> clazz, String methodName){
        return Arrays.stream(clazz.getMethods()).filter(s -> Objects.equals(s.getName(), methodName)).toList();
    }

    public static <T extends Enum<T>> T getEnumParameter(Class<?> clazz, int pos, String value){
        return Arrays.stream(clazz.getConstructors()).filter(con -> con.getParameterCount() > pos  && Enum.class.isAssignableFrom(con.getParameterTypes()[pos])).findAny().map(cls -> Enum.valueOf((Class<T>) (cls.getParameterTypes()[pos]), value)).get();
    }
    public static List<String> getEnumInfo(Object what){
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        return Arrays.stream(clazz.getEnumConstants()).map(s -> ((Enum)s).name()).toList();
    }

    public static boolean isEnum(Object what){
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        return Enum.class.isAssignableFrom(clazz);
    }

    public static void logClassInfo(Object what){
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        Debug.chat( Text.literal("=== " + getClassNameForLog(clazz)+" 的构造器信息 ===").formatted(Formatting.GREEN));

        for (var con : clazz.getDeclaredConstructors()) {
            logConstructorInfo(con);
            Debug.chat(Text.literal("=========").formatted(Formatting.GREEN));
        }
    }
    private static String getClassNameForLog(Class<?> clazz){
        String className = clazz.getSimpleName();
        if (clazz.isEnum()) {
            className += "(Enum)";
        }
        return className;
    }

    public static void logConstructorInfo(Constructor<?> constructor){
        logMethodInfo(constructor);
    }

    public static void logMethodsInfo(Object what){
        Class<?> clazz = what instanceof Class<?> ? (Class<?>) what : what.getClass();
        Debug.chat( Text.literal("=== " + getClassNameForLog(clazz)+" 的方法信息 ===").formatted(Formatting.GREEN));
        for (var method : clazz.getMethods()){
            logMethodInfo(method);
            Debug.chat(Text.literal("=========").formatted(Formatting.GREEN));
        }
    }

    public static void logMethodInfo(Executable constructor){
        StringBuilder sb = new StringBuilder();
        // 修饰符
        sb.append(Modifier.toString(constructor.getModifiers())).append(" ");
        // 构造器名
        sb.append(constructor.getDeclaringClass().getSimpleName());
        // 参数列表
        sb.append("(");
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Parameter[] parameters = constructor.getParameters();
        for (int i = 0; i < paramTypes.length; i++) {
            sb.append(getClassNameForLog(paramTypes[i])).append(" ");
            if(i < parameters.length && parameters[i] .isNamePresent()){
                sb.append(parameters[i].getName());
            }else {
                sb.append("p").append(i);
            }
            if (i < paramTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");

        // 异常信息
        Class<?>[] exceptionTypes = constructor.getExceptionTypes();
        if (exceptionTypes.length > 0) {
            sb.append(" throws ");
            for (int i = 0; i < exceptionTypes.length; i++) {
                sb.append(exceptionTypes[i].getSimpleName());
                if (i < exceptionTypes.length - 1) {
                    sb.append(", ");
                }
            }
        }

        Debug.chat(sb.toString());
    }
}
