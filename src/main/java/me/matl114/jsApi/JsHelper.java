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

    //    private static final Map<Class, WrappingMethod> jsInstanceWrappers ;
    //    static{
    //        Map<Class, WrappingMethod> wrappers = ImmutableMap.<Class, WrappingMethod>builder()
    //            .put(Entity.class,
    // lookupWrappingMethod("xyz.wagyourtail.jsmacros.client.api.helpers.world.entity.EntityHelper", "create"))
    //            .put(ItemStack.class,
    // lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper",
    // ItemStack.class))
    //            .put(NbtElement.class,
    // lookupWrappingMethod("xyz.wagyourtail.jsmacros.client.api.helpers.NBTElementHelper", "wrap"))
    //            .put(Block.class,
    // lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.world.BlockHelper", Block.class))
    //            .put(BlockPos.class,
    // lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.world.BlockPosHelper", BlockPos.class))
    //            .put(Packet.class,
    // lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.PacketByteBufferHelper", Packet.class))
    //            .put(PacketByteBuf.class,
    // lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.PacketByteBufferHelper",
    // PacketByteBuf.class))
    //            .build();
    //        jsInstanceWrappers = wrappers;
    //    }


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
