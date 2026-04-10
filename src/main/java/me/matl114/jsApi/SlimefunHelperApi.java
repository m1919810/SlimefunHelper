package me.matl114.jsApi;

import static me.matl114.utils.ASMUtils.*;
import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import me.matl114.hacks.*;
import me.matl114.hacks.modules.HackModules;
import me.matl114.managers.Tasks;
import me.matl114.utils.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.Method;
import xyz.wagyourtail.jsmacros.client.JsMacrosClient;
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
            xyz:
            try {
                try {
                    currentJsApi = Core.class;
                } catch (Throwable e) {
                    break xyz;
                }
                initXYZ();
                break find;
            } catch (Throwable e) {
                Debug.info(e);
            }
            ce:
            try {
                try {
                    currentJsApi = com.jsmacrosce.jsmacros.core.Core.class;
                } catch (Throwable e) {
                    break ce;
                }
                initCE();
                break find;
            } catch (Throwable e) {
                Debug.info(e);
            }
            throw new IllegalStateException("No JsMacros instance found");
        } catch (Throwable e) {
            Debug.info("JsMacros library inject failed, caused by: ");
            e.printStackTrace();
            Debug.info("Running Mock js lib test");
            //            createSlimefunHelperApi(MockLibBase.class);
            //            for (var clazz : slimefunHelperApi) {
            //                try {
            //                    clazz.newInstance();
            //                } catch (Throwable e1) {
            //                    throw new RuntimeException(e1);
            //                }
            //            }
            Debug.info("Mock lib test success");
        }
    }

    private static void initXYZ() throws Exception {
        Field field = Arrays.stream(Core.class.getFields())
                .filter(s -> Modifier.isStatic(s.getModifiers()))
                .filter(s -> s.getType() == Core.class)
                .peek(s -> s.setAccessible(true))
                .findFirst()
                .orElse(null);
        if (field == null) {
            Class<?> jsMacrosClient;
            try {
                jsMacrosClient = JsMacrosClient.class;
            } catch (Throwable e) {
                jsMacrosClient = Class.forName("xyz.wagyourtail.jsmacros.client.JsMacros");
            }
            field = Arrays.stream(jsMacrosClient.getFields())
                    .filter(s -> Modifier.isStatic(s.getModifiers()))
                    .filter(s -> s.getType() == Core.class)
                    .peek(s -> s.setAccessible(true))
                    .findFirst()
                    .orElse(null);
        }
        if (field == null) throw new IllegalStateException("No valid core find");
        Core jsMacrosInstance = (Core) field.get(null);
        JsMacrosBridge.Holder.bridge = new JsMacrosBridge.JsMacrosXYZ(jsMacrosInstance);
        LibraryRegistry registry = jsMacrosInstance.libraryRegistry;
        Class<?> baseLib = BaseLibrary.class;

        List<Class<?>> clazzes = createSlimefunHelperApi(baseLib, Library.class, Core.class);
        for (var clazz : clazzes) {
            registry.addLibrary((Class<? extends BaseLibrary>) clazz);
        }
        Debug.info("Successfully injected jsMacros library");
    }

    private static void initCE() throws Exception {
        Field field = Arrays.stream(com.jsmacrosce.jsmacros.core.Core.class.getFields())
                .filter(s -> Modifier.isStatic(s.getModifiers()))
                .filter(s -> s.getType() == com.jsmacrosce.jsmacros.core.Core.class)
                .peek(s -> s.setAccessible(true))
                .findFirst()
                .orElse(null);
        if (field == null) {
            field = Arrays.stream(com.jsmacrosce.jsmacros.client.JsMacrosClient.class.getFields())
                    .filter(s -> Modifier.isStatic(s.getModifiers()))
                    .filter(s -> s.getType() == com.jsmacrosce.jsmacros.core.Core.class)
                    .peek(s -> s.setAccessible(true))
                    .findFirst()
                    .orElse(null);
        }
        if (field == null) throw new IllegalStateException("No valid core find");
        com.jsmacrosce.jsmacros.core.Core jsMacrosInstance = (com.jsmacrosce.jsmacros.core.Core) field.get(null);
        JsMacrosBridge.Holder.bridge = new JsMacrosBridge.JsMacrosCE(jsMacrosInstance);
        com.jsmacrosce.jsmacros.core.library.LibraryRegistry registry = jsMacrosInstance.libraryRegistry;
        Class<?> baseLib = com.jsmacrosce.jsmacros.core.library.BaseLibrary.class;

        List<Class<?>> clazzes = createSlimefunHelperApi(
                baseLib, com.jsmacrosce.jsmacros.core.library.Library.class, com.jsmacrosce.jsmacros.core.Core.class);
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

    public static synchronized List<Class<?>> createSlimefunHelperApi(
            Class<?> libBase, Class<?> libAnnotation, Class<?> runnerClass) {
        if (slimefunHelperApi == null) {
            slimefunHelperApi = new ArrayList<>();
            List<Class<?>> apiClasses = List.of(
                    Consts.class,
                    ClientHelper.class,
                    DataHelper.class,
                    InputHelper.class,
                    KeyBindingHelper.class,
                    PacketHelper.class,
                    RenderHelper.class,
                    ReflectHelper.class,
                    RegistryHelper.class,
                    NBTHelper.class,
                    FileHelper.class,
                    WorldHelper.class,
                    EnumHelper.class,
                    EntityHelper.class,
                    ScreenHelper.class,
                    MovTasks.class,
                    Tasks.class,
                    HackModules.class,
                    CombatTasks.class,
                    InteractionTasks.class,
                    MineTasks.class,
                    InvTasks.class,
                    JsHelper.class,
                    Debug.class,
                    CommonUtils.class,
                    RenderUtils.class,
                    ChatUtils.class,
                    InventoryUtils.class,
                    ItemStackHelper.class,
                    CollectionUtils.class,
                    RaycastUtils.class,
                    ClientUtils.class,
                    ScreenUtils.class,
                    NetworkUtils.class,
                    ItemStackUtils.class);

            for (Class<?> clazz : apiClasses) {
                slimefunHelperApi.add(buildLibForJsMacros(libBase, libAnnotation, runnerClass, clazz));
            }
            // todo: 适配PacketByteBufferHelper
        }
        return slimefunHelperApi;
    }

    public static synchronized Class<?> buildLibForJsMacros(
            Class<?> targetBaseClass, Class<?> libClass, Class<?> runerClass, Class<?> utilityClass) {
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
                    // 只有当不存在getter时才创建getter
                    // 只有当field为final的时候才创建field
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
            Constructor targetBaseClassContructor = targetBaseClass.getConstructors()[0];
            String typeDesc =
                    ByteCodeUtils.getMethodDescriptor("", targetBaseClassContructor.getParameterTypes(), void.class);
            // 生成构造函数，初始化final字段
            MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", typeDesc, null, null);
            constructor.visitCode();

            // 调用父类构造函数
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            for (var i = 0; i < targetBaseClassContructor.getParameterCount(); ++i) {
                constructor.visitVarInsn(ALOAD, i + 1);
            }
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, baseClassInternalName, "<init>", typeDesc, false);

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

            // 添加 getDelegate 方法
            String delegateMethodName = "getDelegate";
            String delegateReturnDesc = "()Ljava/lang/String;";
            MethodVisitor delegateMv = cw.visitMethod(ACC_PUBLIC, delegateMethodName, delegateReturnDesc, null, null);
            delegateMv.visitCode();
            delegateMv.visitLdcInsn(utilityClass.getName());
            delegateMv.visitInsn(ARETURN);
            delegateMv.visitMaxs(0, 0);
            delegateMv.visitEnd();
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
