package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ByteCodeUtils {
    /**
     * Converts a Java Class to its JVM type descriptor.
     *
     * <p>This method uses ASM's Type.getDescriptor() to convert a Class object
     * to its corresponding JVM type descriptor format.
     *
     * @param clazz The Java Class to convert
     * @return The JVM type descriptor string
     */
    public static String toJvmType(Class<?> clazz) {
        return Type.getDescriptor(clazz);
    }
    /**
     * Converts a Java class name to its JVM type descriptor.
     *
     * <p>This method converts Java class names to JVM type descriptors:
     * <ul>
     *   <li>Primitive types: "int" → "I", "boolean" → "Z", etc.</li>
     *   <li>Reference types: "java.lang.String" → "Ljava/lang/String;"</li>
     *   <li>Arrays: "int[]" → "[I", "String[]" → "[Ljava/lang/String;"</li>
     * </ul>
     *
     * @param clazzName The Java class name to convert
     * @return The JVM type descriptor string
     */
    public static String toJvmType(String clazzName){
        if(clazzName.charAt(0) == '['){
            //is array
            return clazzName.replace('.','/');
        }
        return switch (clazzName) {
            case "void" -> "V";
            case "int" -> "I";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "double" -> "D";
            case "float" -> "F";
            case "long" -> "J";
            default -> "L" + clazzName.replace(".", "/") + ";";
        };
    }

    /**
     * Converts a JVM type descriptor to a Java class name.
     *
     * <p>This method converts JVM type descriptors back to Java class names:
     * <ul>
     *   <li>Primitive types: "I" → "int", "Z" → "boolean", etc.</li>
     *   <li>Reference types: "Ljava/lang/String;" → "java.lang.String"</li>
     *   <li>Arrays: "[I" → "int[]", "[Ljava/lang/String;" → "java.lang.String[]"</li>
     * </ul>
     *
     * @param jvm The JVM type descriptor to convert
     * @return The Java class name
     */
    public static String fromJvmType(String jvm){
        if(jvm.charAt(0) == '['){
            //is array
            return jvm.replace('/','.');
        }
        return switch (jvm) {
            case "V" -> "void";
            case "I" -> "int";
            case "Z" -> "boolean";
            case "B" -> "byte";
            case "C" -> "char";
            case "S" -> "short";
            case "D" -> "double";
            case "F" -> "float";
            case "J" -> "long";
            default -> jvm.substring(1, jvm.length()-1).replace('/','.');
        };
    }
    /**
     * Gets the component type information for a class.
     *
     * <p>This method analyzes a class to determine if it's an array and extracts
     * the component type information. For arrays, it returns the array dimensions
     * and the base component type. For non-arrays, it returns an empty string
     * for dimensions and the class name.
     *
     * @param clazz The class to analyze
     * @return A Pair where the first element is the array dimensions (empty string for non-arrays)
     *         and the second element is the component type name
     */
    public static Pair<String, String> getComponentType(Class<?> clazz){
        if(clazz.isArray()){
            //is array
            StringBuilder builder = new StringBuilder();
            while(clazz.isArray()){
                builder.append('[');
                clazz = clazz.getComponentType();
            }
            return Pair.of(builder.toString(), clazz.getName());
        }else {
            return Pair.of("",clazz.getName());
        }
    }
    /**
     * Converts a JVM primitive type descriptor character to its Java primitive type name.
     *
     * <p>This method maps JVM primitive type descriptor characters to their corresponding
     * Java primitive type names. If the character is not a primitive type descriptor,
     * it returns null.
     *
     * @param descriptor The JVM primitive type descriptor character
     * @return The Java primitive type name, or null if not a primitive type
     */
    public static String getPrimitiveType(char descriptor) {
        return switch (descriptor) {
            case 'Z' -> "boolean";
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            case 'V' -> "void";
            default ->
                //not a primitive
                // throw new RuntimeException("Not a primitive descriptor:"+descriptor);
                null;
        };
    }
    /**
     * Generates a JVM method descriptor from a Method object.
     *
     * <p>This method creates a JVM method descriptor string in the format:
     * methodName(parameterTypes)returnType
     * where parameterTypes and returnType are in JVM descriptor format.
     *
     * @param method The Method object to generate a descriptor for
     * @return The JVM method descriptor string
     */
    public static String getMethodDescriptor(Method method) {
        var builder = new StringBuilder();
        builder.append(method.getName());
        builder.append("(");
        for (var arg : method.getParameterTypes()) {
            builder.append(toJvmType(arg));
        }
        builder.append(")");
        builder.append(toJvmType(method.getReturnType()));
        return builder.toString();
    }

    /**
     * Generates a JVM method descriptor from method components.
     *
     * <p>This method creates a JVM method descriptor string in the format:
     * methodName(parameterTypes)returnType
     * where parameterTypes and returnType are in JVM descriptor format.
     *
     * @param name The method name
     * @param arguments The parameter types of the method
     * @param returnType The return type of the method
     * @return The JVM method descriptor string
     */
    public static String getMethodDescriptor(String name, Class[] arguments, Class returnType){
        var builder = new StringBuilder();
        builder.append(name);
        builder.append("(");
        for (var arg : arguments) {
            builder.append(toJvmType(arg));
        }
        builder.append(")");
        builder.append(toJvmType(returnType));
        return builder.toString();
    }


    /**
     * Extracts the method name from a JVM method descriptor.
     *
     * <p>This method parses a JVM method descriptor and returns the method name
     * by extracting the substring before the opening parenthesis.
     *
     * @param descriptor The JVM method descriptor string
     * @return The method name
     */
    public static String parseMethodNameFromDescriptor(String descriptor){
        return descriptor.substring(0, descriptor.indexOf('('));
    }

//    /**
//     * Parses a JVM method descriptor into its components.
//     *
//     * <p>This method parses a JVM method descriptor and returns a Triplet containing:
//     * <ul>
//     *   <li>The method name</li>
//     *   <li>An array of parameter type descriptors</li>
//     *   <li>The return type descriptor</li>
//     * </ul>
//     *
//     * <p>The method handles complex type descriptors including arrays and reference types
//     * by properly parsing the JVM descriptor format.
//     *
//     * @param descriptor The JVM method descriptor string
//     * @return A Triplet containing (methodName, parameterTypes[], returnType)
//     */
//    public static Triplet<String,String[],String> parseMethodDescriptor(String descriptor){
//        int index = descriptor.indexOf('(');
//        String name = descriptor.substring(0, index);
//        int index2 = descriptor.indexOf(')');
//        String params = descriptor.substring(index+1, index2);
//        String retType = descriptor.substring(index2 +1);
//        List<String> paramsList = new ArrayList<>();
//        StringBuilder builder = new StringBuilder();
//        int len = params.length();
//        boolean recordingPath =false;
//        for (int i=0; i< len; ++i){
//            char c = params.charAt(i);
//            if(recordingPath){
//                builder.append(c);
//                if (c == ';') {
//                    recordingPath = false;
//                    paramsList.add(builder.toString());
//                }
//            }else {
//                if('L' == c || '[' == c){
//                    recordingPath = true;
//                    builder = new StringBuilder();
//                    builder.append(c);
//                }else {
//                    paramsList.add(String.valueOf(c));
//                }
//            }
//        }
//        return new Triplet(name, paramsList.toArray(String[]::new), retType);
//    }
    /**
     * Extracts the field name from a JVM field descriptor.
     *
     * <p>This method parses a JVM field descriptor and attempts to extract a field name.
     * The method handles different types of descriptors:
     * <ul>
     *   <li>Reference types ending with ';'</li>
     *   <li>Array types containing '['</li>
     *   <li>Primitive types</li>
     * </ul>
     *
     * <p>Note: This method assumes the descriptor format includes a field name prefix,
     * which may not always be the case for pure type descriptors.
     *
     * @param descriptor The JVM field descriptor string
     * @return The extracted field name, or a partial type name if no field name is found
     */
    public static String parseFieldNameFromDescriptor(String descriptor){
        if(descriptor.charAt(descriptor.length()-1) == ';'){
            int isArray = descriptor.indexOf('[');
            if(isArray > 0 ){
                //class array
                return descriptor.substring(0, isArray);
            }else {
                //class type
                String firstLevel = descriptor.substring(0, descriptor.indexOf('/'));
                return firstLevel.substring(0, firstLevel.lastIndexOf('L'));
            }
        }else {
            int isArray = descriptor.indexOf('[');
            if(isArray > 0 ){
                //primitive type array
                return descriptor.substring(0, isArray);
            }
            //primitive type
            return descriptor.substring(0, descriptor.length()-1);
        }
    }
    /**
     * Generates a JVM field descriptor from field name and type.
     *
     * <p>This method creates a JVM field descriptor by concatenating the field name
     * with the JVM type descriptor of the field type.
     *
     * @param fieldName The name of the field
     * @param fieldType The type of the field
     * @return The JVM field descriptor string
     */
    public static String getFieldDescriptor(String fieldName, Class<?> fieldType){
        return fieldName + toJvmType(fieldType);
    }



}
