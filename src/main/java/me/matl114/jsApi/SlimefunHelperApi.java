package me.matl114.jsApi;

import static me.matl114.utils.ASMUtils.*;
import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import me.matl114.hacks.*;
import me.matl114.utils.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.Method;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.library.BaseLibrary;
import xyz.wagyourtail.jsmacros.core.library.Library;
import xyz.wagyourtail.jsmacros.core.library.LibraryRegistry;

/**
 * this utility class is created for JsMacros invocation
 * JsMacros is fucking great
 */
public class SlimefunHelperApi {
    private static void initTask() {
        Class<?> currentJsApi;
        find:
        try {
            try{
                currentJsApi = Core.class;
                initXYZ();
                break find;
            }catch (Throwable e){

            }
            try{
                currentJsApi = com.jsmacrosce.jsmacros.core.Core.class;
                initCE();
                break find;
            }catch (Throwable e){

            }
            throw new IllegalStateException("No JsMacros instance found");
        } catch (Throwable e) {
            Debug.info("JsMacros library inject failed, caused by: ");
            e.printStackTrace();
            Debug.info("Running Mock js lib test");
            //            createSlimefunHelperApi(MockLibBase.class);
            //            for (var clazz : slimefunHelperApi){
            //                try{
            //                    clazz.newInstance();
            //                }catch (Throwable e1){
            //                    throw new RuntimeException(e1);
            //                }
            //            }
            Debug.info("Mock lib test success");
        }
    }

    private static void initXYZ(){
        Core jsMacrosInstance = Core.getInstance();
        JsMacrosBridge.Holder.bridge = new JsMacrosBridge.JsMacrosXYZ();
        LibraryRegistry registry = jsMacrosInstance.libraryRegistry;
        Class<?> baseLib = BaseLibrary.class;

        List<Class<?>> clazzes = createSlimefunHelperApi(baseLib, Library.class);
        for (var clazz : clazzes) {
            registry.addLibrary((Class<? extends BaseLibrary>) clazz);
        }
        Debug.info("Successfully injected jsMacros library");
    }

    private static void initCE(){
        com.jsmacrosce.jsmacros.core.Core jsMacrosInstance = com.jsmacrosce.jsmacros.core.Core.getInstance();
        JsMacrosBridge.Holder.bridge = new JsMacrosBridge.JsMacrosCE();
        com.jsmacrosce.jsmacros.core.library.LibraryRegistry registry = jsMacrosInstance.libraryRegistry;
        Class<?> baseLib = com.jsmacrosce.jsmacros.core.library.BaseLibrary.class;

        List<Class<?>> clazzes = createSlimefunHelperApi(baseLib, com.jsmacrosce.jsmacros.core.library.Library.class);
        for (var clazz : clazzes) {
            registry.addLibrary((Class<? extends com.jsmacrosce.jsmacros.core.library.BaseLibrary>) clazz);
        }
        Debug.info("Successfully injected jsMacrosCE library");
    }

    public static void init() {
        Tasks.scheduleDelayed(SlimefunHelperApi::initTask, 1);
    }

    public abstract static class MockLibBase {}

    private static List<Class<?>> slimefunHelperApi;

    public static synchronized List<Class<?>> createSlimefunHelperApi(Class<?> libBase, Class<?> libAnnotation) {
        if (slimefunHelperApi == null) {
            slimefunHelperApi = new ArrayList<>();
            List<Class<?>> apiClasses = List.of(
                ClientHelper.class,
                Consts.class,
                DataHelper.class,
                InputHelper.class,
                KeyBindingHelper.class,
                PacketHelper.class,
                RenderHelper.class,
                ReflectHelper.class,
                MovTasks.class,
                Tasks.class,
                CombatTasks.class,
                MineTasks.class,
                InvTasks.class,
                CommonUtils.class,
                JsHelper.class,
                RegistryHelper.class,
                Debug.class,
                ChatUtils.class,
                InventoryUtils.class,
                ItemStackHelper.class,
                CollectionUtils.class,
                FileHelper.class,
                RaycastUtils.class,
                WorldHelper.class,
                NBTHelper.class,
                EnumHelper.class,
                EntityHelper.class,
                ScreenHelper.class,
                ClientUtils.class,
                ItemStackUtils.class
            );

            for (Class<?> clazz : apiClasses) {
                slimefunHelperApi.add(buildLibForJsMacros(libBase, libAnnotation, clazz));
            }
            // todo: 适配PacketByteBufferHelper
        }
        return slimefunHelperApi;
    }

    public static synchronized Class<?> buildLibForJsMacros(Class<?> targetBaseClass, Class<?> libClass, Class<?> utilityClass) {
        try {
            // 检查是否有ApiMethod注解
            boolean hasApiMethodAnnotation = false;
            if (utilityClass.getAnnotation(ApiMethod.class) != null) {
                hasApiMethodAnnotation = true;
            }
            Class libraryClass = libClass;
            boolean hasLibraryAnnotation = libraryClass != null;


            String className = utilityClass.getName() + "LibImpl";
            String internalName = className.replace('.', '/');
            String baseClassInternalName = targetBaseClass.getName().replace('.', '/');
            String utilityClassInternalName = utilityClass.getName().replace('.', '/');

            // 创建ClassWriter
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            // 定义类头部
            cw.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, internalName, null, baseClassInternalName, null);

            cw.visitSource(null, null);
            if (hasLibraryAnnotation) {
                // 创建注解描述符
                String annotationDesc = "L" + libraryClass.getName().replace('.', '/') + ";";

                // 创建AnnotationVisitor
                AnnotationVisitor av = cw.visitAnnotation(annotationDesc, true);

                // 设置value属性为原始类的SimpleName
                av.visit("value", utilityClass.getSimpleName());

                av.visitEnd();
            }
            // 收集需要处理的方法和字段
            Set<java.lang.reflect.Method> targetMethods = new HashSet<>();
            Set<String> getterMethods = new HashSet<>();
            Set<Field> targetFields = new HashSet<>();

            for (java.lang.reflect.Method method : utilityClass.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                // 只处理public static方法
                if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)) {
                    // 检查是否需要根据@ApiMethod过滤
                    if (!hasApiMethodAnnotation) {
                        if (method.getAnnotation(ApiMethod.class) == null) {
                            continue; // 没有@ApiMethod注解，跳过
                        }
                    }
                    targetMethods.add(method);
                    if (method.getParameterCount() == 0
                            && method.getReturnType() != void.class
                            && method.getName().startsWith("get")) {
                        getterMethods.add(method.getName());
                    }

                    // 创建对应的实例方法
                    Method asmMethod = Method.getMethod(method);
                    Type[] argTypes = asmMethod.getArgumentTypes();

                    // 生成方法描述符
                    StringBuilder methodDesc = new StringBuilder("(");
                    for (Type argType : argTypes) {
                        methodDesc.append(argType.getDescriptor());
                    }
                    methodDesc.append(")").append(asmMethod.getReturnType().getDescriptor());

                    MethodVisitor mv =
                            cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDesc.toString(), null, null);
                    mv.visitCode();
                    var args = method.getParameterTypes();
                    // 加载参数
                    int localVarIndex = 1;
                    for (int i = 0; i < args.length; i++) {
                        localVarIndex += createSuitableLoad(mv, Type.getInternalName(args[i]), localVarIndex);
                    }

                    // 调用原始静态方法
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            utilityClassInternalName,
                            method.getName(),
                            methodDesc.toString(),
                            utilityClass.isInterface());

                    // 返回结果
                    createSuitableReturn(mv, Type.getInternalName(method.getReturnType()));
                    // 计算最大栈和局部变量
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
            }

            // 处理字段
            for (java.lang.reflect.Field field : utilityClass.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                // 只处理public static字段
                if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)) {
                    // 检查是否需要根据@ApiMethod过滤
                    if (!hasApiMethodAnnotation) {
                        if (field.getAnnotation(ApiMethod.class) == null) {
                            continue; // 没有@ApiMethod注解，跳过
                        }
                    }

                    // 创建对应的实例字段
                    // todo: 只有当不存在getter时才创建getter
                    // todo: 只有当field为final的时候才创建field
                    String fieldDesc = Type.getDescriptor(field.getType());
                    if (Modifier.isFinal(modifiers)) {
                        targetFields.add(field);
                        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, field.getName(), fieldDesc, null, null);
                    }

                    String getterMethodName = "get" + field.getName();
                    // create getter if no exist
                    if (!getterMethods.contains(getterMethodName)) {
                        getterMethods.add(getterMethodName);
                        var mv = cw.visitMethod(ACC_PUBLIC | ACC_FINAL, getterMethodName, "()" + fieldDesc, null, null);
                        mv.visitCode();
                        mv.visitFieldInsn(
                                GETSTATIC,
                                Type.getInternalName(field.getDeclaringClass()),
                                field.getName(),
                                ByteCodeUtils.toJvmType(field.getType()));
                        ASMUtils.createSuitableReturn(mv, Type.getInternalName(field.getType()));
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                    }
                }
            }

            // 处理方法

            // 生成构造函数，初始化final字段
            MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();

            // 调用父类构造函数
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, baseClassInternalName, "<init>", "()V", false);

            // 初始化所有final字段
            for (java.lang.reflect.Field field : utilityClass.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && targetFields.contains(field)) {

                    // 获取字段值
                    Object fieldValue = field.get(null); // 静态字段
                    String fieldDesc = Type.getDescriptor(field.getType());

                    // 加载this
                    constructor.visitVarInsn(Opcodes.ALOAD, 0);

                    // 加载字段值
                    constructor.visitFieldInsn(
                            GETSTATIC,
                            Type.getInternalName(field.getDeclaringClass()),
                            field.getName(),
                            ByteCodeUtils.toJvmType(field.getType()));
                    // 存储到字段
                    constructor.visitFieldInsn(Opcodes.PUTFIELD, internalName, field.getName(), fieldDesc);
                }
            }

            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();

            // 完成类定义
            cw.visitEnd();

            // 加载生成的类
            byte[] bytecode = cw.toByteArray();

            // 使用自定义类加载器加载
            CustomClassLoader classLoader = CustomClassLoader.getInstance();
            classLoader.defineAccessClass(className, bytecode);

            return classLoader.loadAccessClass(className);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
