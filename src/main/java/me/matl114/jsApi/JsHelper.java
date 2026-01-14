package me.matl114.jsApi;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.NotImplementedException;
import org.graalvm.polyglot.Value;
import xyz.wagyourtail.jsmacros.client.api.classes.inventory.Inventory;
import xyz.wagyourtail.jsmacros.client.api.classes.math.Pos2D;
import xyz.wagyourtail.jsmacros.client.api.classes.math.Pos3D;
import xyz.wagyourtail.jsmacros.client.api.library.impl.FJavaUtils;
import xyz.wagyourtail.jsmacros.core.MethodWrapper;
import xyz.wagyourtail.jsmacros.core.helpers.BaseHelper;
import xyz.wagyourtail.jsmacros.core.library.impl.FGlobalVars;
import xyz.wagyourtail.jsmacros.js.library.impl.FWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;

@ApiMethod
public class JsHelper {
    public static <T> T unwrap(Object what, Class<T> type){
        if(type.isInstance(what)){
            return type.cast(what);
        }else {
            if(what instanceof BaseHelper<?> base){
                var raw = base.getRaw();
                return type.cast(raw);
            }
            if(what instanceof Pos3D pos3){
                return type.cast(new Vec3d(pos3.x, pos3.y, pos3.z));
            }
            if(what instanceof Pos2D pos2D){
                return type.cast(new Vec2f((float) pos2D.x, (float) pos2D.y));
            }
            if(what instanceof Inventory<?> inventory){
                return type.cast( inventory.getRawContainer());
            }
            try{
                return type.cast(what);
            }catch (Throwable e){
                throw new UnsupportedOperationException("This type is not supported to unwrap: "+what.getClass().getName());
            }
//            try{
//                Method method = what.getClass().getMethod("getRaw");
//                method.setAccessible(true);
//                return (T) method.invoke(what);
//            }catch (Throwable e){
//                return (T)what;
//            }
        }
    }
    private static WrappingMethod lookupWrappingMethod(String clazzName, String methodName){
        try{
            Class<?> clazz = Class.forName(clazzName);
            return Arrays.stream(clazz.getMethods()).
                filter(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 1)
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .map(m -> (WrappingMethod) (s -> {
                    return m.invoke(null, s);
                }))
                .orElseGet(() -> m -> {
                    throw new UnsupportedOperationException("Failed to find wrapper for :" + clazz.getSimpleName()+", use JavaUtils.getHelperFromRaw instead");
                })
                ;
        }catch (Throwable e){
            Debug.info("Fail to look up wrapping method : " + clazzName + "." + methodName);
            return m -> {throw new UnsupportedOperationException("Failed to find wrapper for :" + clazzName+", use JavaUtils.getHelperFromRaw instead");};
        }
    }
    private static WrappingMethod lookupWrappingConstructor(String clazzName, Class<?> clazz2){
        try {
            Class<?> clazz = Class.forName(clazzName);
            return Arrays.stream(clazz.getConstructors()).
                filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 1)
                .filter(m -> clazz2.isAssignableFrom(m.getParameterTypes()[0]))
                .findFirst()
                .map(m -> (WrappingMethod) (m::newInstance))
                .orElseGet(() -> m -> {
                    throw new UnsupportedOperationException("Failed to find wrapper for :" + clazz.getSimpleName()+", use JavaUtils.getHelperFromRaw instead");
                })
                ;
        }catch (Throwable e){
            Debug.info("Fail to look up wrapping constructor : " + clazzName);
            return m -> {throw new UnsupportedOperationException("Failed to find wrapper for :" + clazzName +", use JavaUtils.getHelperFromRaw instead");};
        }
    }


    public static void runOnMainThread(Runnable runnable){
        MinecraftClient.getInstance().execute(runnable);
    }

//    private static final Map<Class, WrappingMethod> jsInstanceWrappers ;
//    static{
//        Map<Class, WrappingMethod> wrappers = ImmutableMap.<Class, WrappingMethod>builder()
//            .put(Entity.class, lookupWrappingMethod("xyz.wagyourtail.jsmacros.client.api.helpers.world.entity.EntityHelper", "create"))
//            .put(ItemStack.class, lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper", ItemStack.class))
//            .put(NbtElement.class, lookupWrappingMethod("xyz.wagyourtail.jsmacros.client.api.helpers.NBTElementHelper", "wrap"))
//            .put(Block.class, lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.world.BlockHelper", Block.class))
//            .put(BlockPos.class, lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.world.BlockPosHelper", BlockPos.class))
//            .put(Packet.class, lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.PacketByteBufferHelper", Packet.class))
//            .put(PacketByteBuf.class, lookupWrappingConstructor("xyz.wagyourtail.jsmacros.client.api.helpers.PacketByteBufferHelper", PacketByteBuf.class))
//            .build();
//        jsInstanceWrappers = wrappers;
//    }
    private static final Supplier<?> javaUtilInstance = Suppliers.memoize(()->{
        try{
            return new FJavaUtils();
        }catch (Throwable e){
            throw new RuntimeException("Failed to instantiate JavaUtil Instance", e);
        }
    });
    public static <T> T wrap(Object object) throws Throwable{
        FJavaUtils javaUtils = (FJavaUtils) javaUtilInstance.get();
        Object ret = javaUtils.getHelperFromRaw(object);
        if(ret != null){
            return (T) ret;
        }
        if(object instanceof Vec3d vec3d){
            return (T) new Pos3D(vec3d);
        }
        if(object instanceof Vec2f vec2f){
            return (T) new Pos2D(vec2f.x, vec2f.y);
        }
        if(object instanceof HandledScreen handledScreen){
            return (T) Inventory.create(handledScreen);
        }

        throw new UnsupportedOperationException("This type of instance is not supported to wrap"+", use JavaUtils.getHelperFromRaw instead");
    }

    public static interface WrappingMethod{
        public  Object create(Object args) throws Throwable;
    }

    public static void runSingleRepeat(Object object, String flag){
        runSingleRepeat(object, flag, true);
    }

    public static void runSingleRepeat(Object object, String flag, boolean debug) {
        throw new NotImplementedException();
    }


}
