package me.matl114.hooks;

import fi.dy.masa.litematica.util.EasyPlaceUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;

public class LitematicaHooks implements IHooks {
    @Getter
    boolean enabled;

    final MethodHandle easyPlaceHandle;

    public LitematicaHooks() {
        MethodHandle mh = null;
        try {
            Class<?> clazz = SchematicWorldHandler.class;
            var lookup = MethodHandles.privateLookupIn(EasyPlaceUtils.class, MethodHandles.lookup());
            Method method = EasyPlaceUtils.class.getDeclaredMethod(
                    "getClickPosition", BlockHitResult.class, BlockState.class, BlockState.class);
            method.setAccessible(true);
            mh = lookup.unreflect(method);
            enabled = true;
        } catch (Throwable e) {
            enabled = false;
        }
        easyPlaceHandle = mh;
    }

    public BlockHitResult getEasyPlaceClickedPosition(
            BlockHitResult blockHitResult, BlockState blockState, BlockState blockState2) {
        try {
            if (easyPlaceHandle != null) {
                return (BlockHitResult) easyPlaceHandle.invokeExact(blockHitResult, blockState, blockState2);
            } else {
                return null;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static LitematicaHooks instance;

    public static LitematicaHooks getInstance() {
        if (instance == null) {
            instance = new LitematicaHooks();
        }
        return instance;
    }
}
