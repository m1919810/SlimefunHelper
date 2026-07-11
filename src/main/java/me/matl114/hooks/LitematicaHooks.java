package me.matl114.hooks;

import com.google.common.base.Preconditions;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class LitematicaHooks implements IHooks {
    public abstract BlockHitResult getEasyPlaceClickedPosition(
            BlockHitResult blockHitResult, BlockState blockState, BlockState blockState2);

    public abstract World getSchematicWorld();

    public abstract boolean isEasyPlaceEnabled();

    public abstract boolean isPositionWithinRange(BlockPos pos);

    public static LitematicaHooks instance;

    public static LitematicaHooks getInstance() {
        if (instance == null) {
            try {
                instance = new Impl();
            } catch (Throwable e) {
                instance = new Default();
            }
        }
        return instance;
    }

    public static class Impl extends LitematicaHooks {
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

        public Impl() {
            Class<?> clazz = SchematicWorldHandler.class;
            Preconditions.checkNotNull(DataManager.getRenderLayerRange());
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

        @Override
        public boolean isPositionWithinRange(BlockPos pos) {
            return DataManager.getRenderLayerRange().isPositionWithinRange(pos);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    public static class Default extends LitematicaHooks {

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public BlockHitResult getEasyPlaceClickedPosition(
                BlockHitResult blockHitResult, BlockState blockState, BlockState blockState2) {
            return null;
        }

        @Override
        public World getSchematicWorld() {
            return null;
        }

        @Override
        public boolean isEasyPlaceEnabled() {
            return false;
        }

        @Override
        public boolean isPositionWithinRange(BlockPos pos) {
            return false;
        }
    }
}
