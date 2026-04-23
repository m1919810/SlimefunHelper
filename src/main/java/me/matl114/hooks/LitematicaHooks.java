package me.matl114.hooks;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

public class LitematicaHooks implements IHooks {
    @Getter
    boolean enabled;

    final InvokeContext context;

    public LitematicaHooks() {
        MethodHandle mh = null;
        InvokeContext ctx;
        try {
            Class<?> clazz = SchematicWorldHandler.class;
            enabled = true;
            ctx = new Success();
        } catch (Throwable e) {
            enabled = false;
            ctx = new Empty();
        }
        context = ctx;
    }

    public BlockHitResult getEasyPlaceClickedPosition(
            BlockHitResult blockHitResult, BlockState blockState, BlockState blockState2) {
        return context.getEasyPlaceClickedPosition(blockHitResult, blockState, blockState2);
    }

    public World getSchematicWorld() {
        return context.getSchematicWorld();
    }

    public boolean isEasyPlaceEnabled() {
        return context.isEasyPlaceEnabled();
    }

    public static LitematicaHooks instance;

    public static LitematicaHooks getInstance() {
        if (instance == null) {
            instance = new LitematicaHooks();
        }
        return instance;
    }

    public static interface InvokeContext {
        BlockHitResult getEasyPlaceClickedPosition(
                BlockHitResult blockHitResult, BlockState blockState, BlockState blockState2);

        World getSchematicWorld();

        public boolean isEasyPlaceEnabled();
    }

    private static class Empty implements InvokeContext {

        @Override
        public BlockHitResult getEasyPlaceClickedPosition(
                BlockHitResult blockHitResult, BlockState blockState, BlockState blockState2) {
            return null;
        }

        @Override
        public World getSchematicWorld() {
            return null;
        }

        public boolean isEasyPlaceEnabled() {
            return false;
        }
    }

    private static class Success implements InvokeContext {
        public static final MethodHandle easyPlaceHandle;

        static {
            try {
                var lookup = MethodHandles.privateLookupIn(EasyPlaceUtils.class, MethodHandles.lookup());
                Method method = EasyPlaceUtils.class.getDeclaredMethod(
                        "getClickPosition", BlockHitResult.class, BlockState.class, BlockState.class);
                method.setAccessible(true);
                easyPlaceHandle = lookup.unreflect(method);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public BlockHitResult getEasyPlaceClickedPosition(
                BlockHitResult blockHitResult, BlockState blockState, BlockState blockState2) {
            try {
                return (BlockHitResult) easyPlaceHandle.invokeExact(blockHitResult, blockState, blockState2);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public World getSchematicWorld() {
            return (World) SchematicWorldHandler.getSchematicWorld();
        }

        @Override
        public boolean isEasyPlaceEnabled() {
            return Configs.Generic.EASY_PLACE_MODE.getBooleanValue();
        }
    }
}
