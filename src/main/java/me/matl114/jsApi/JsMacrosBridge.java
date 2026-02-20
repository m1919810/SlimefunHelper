package me.matl114.jsApi;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import xyz.wagyourtail.jsmacros.client.api.classes.inventory.Inventory;
import xyz.wagyourtail.jsmacros.client.api.classes.math.Pos2D;
import xyz.wagyourtail.jsmacros.client.api.classes.math.Pos3D;
import xyz.wagyourtail.jsmacros.client.api.helper.inventory.ItemStackHelper;
import xyz.wagyourtail.jsmacros.client.api.helper.world.BlockDataHelper;
import xyz.wagyourtail.jsmacros.client.api.library.impl.FJavaUtils;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.helpers.BaseHelper;
import xyz.wagyourtail.jsmacros.core.language.EventContainer;

public interface JsMacrosBridge {

    public static JsMacrosBridge getInstance() {
        return Holder.bridge;
    }

    public static class Holder {
        static JsMacrosBridge bridge;
    }

    default <T> T unwrap(Object what, Class<T> type) {
        if (type.isInstance(what)) {
            return type.cast(what);
        } else return forceUnwrap(what, type);
    }

    public <T> T forceUnwrap(Object what, Class<T> type);

    public Object wrap(Object what);

    public Object newBlockData(BlockState b, BlockEntity e, BlockPos bp);

    public Object createInventory();

    public ItemStack unwrapItemStack(Object what);

    public Object wrapItemStack(ItemStack what);

    public boolean isItemEmpty(Object what);

    public Object getRunningCtxBinding(Object what) throws Throwable;

    public static class JsMacrosXYZ implements JsMacrosBridge {
        Core core;
        FJavaUtils javaUtils;

        public JsMacrosXYZ(Core core) {
            this.core = core;
            try {
                this.javaUtils = new FJavaUtils();
            } catch (Throwable e) {

            }
        }

        @Override
        public <T> T forceUnwrap(Object what, Class<T> type) {

            if (what instanceof BaseHelper<?> base) {
                var raw = base.getRaw();
                return type.cast(raw);
            }
            if (what instanceof Pos3D pos3) {
                return type.cast(new Vec3d(pos3.x, pos3.y, pos3.z));
            }
            if (what instanceof Pos2D pos2D) {
                return type.cast(new Vec2f((float) pos2D.x, (float) pos2D.y));
            }
            if (what instanceof Inventory<?> inventory) {
                return type.cast(inventory.getRawContainer());
            }
            try {
                return type.cast(what);
            } catch (Throwable e) {
                throw new UnsupportedOperationException("This type is not supported to unwrap: "
                        + what.getClass().getName());
            }
            //            try{
            //                Method method = what.getClass().getMethod("getRaw");
            //                method.setAccessible(true);
            //                return (T) method.invoke(what);
            //            }catch (Throwable e){
            //                return (T)what;
            //            }

        }

        @Override
        public Object wrap(Object object) {
            FJavaUtils javaUtils = this.javaUtils;
            if (object instanceof Vec3d vec3d) {
                return new Pos3D(vec3d);
            }
            if (object instanceof Vec2f vec2f) {
                return new Pos2D(vec2f.x, vec2f.y);
            }
            if (object instanceof HandledScreen handledScreen) {
                return Inventory.create(handledScreen);
            }
            Object ret = javaUtils.getHelperFromRaw(object);
            if (ret != null) {
                return ret;
            }
            throw new UnsupportedOperationException(
                    "This type of instance is not supported to wrap" + ", use JavaUtils.getHelperFromRaw instead");
        }

        @Override
        public Object newBlockData(BlockState b, BlockEntity e, BlockPos bp) {
            return new BlockDataHelper(b, e, bp);
        }

        @Override
        public Object createInventory() {
            return Inventory.create();
        }

        @Override
        public ItemStack unwrapItemStack(Object what) {
            return ((ItemStackHelper) what).getRaw();
        }

        @Override
        public Object wrapItemStack(ItemStack what) {
            return new ItemStackHelper(what);
        }

        @Override
        public boolean isItemEmpty(Object what) {
            return ((ItemStackHelper) what).isEmpty();
        }

        @Override
        public Object getRunningCtxBinding(Object what) throws Throwable {
            EventContainer<?> context = (EventContainer<?>) what;
            var ctx0 = context.getCtx().getContext();
            return ReflectHelper.invoke(ctx0, "getBindings", "js");
        }
    }

    public static class JsMacrosCE implements JsMacrosBridge {
        com.jsmacrosce.jsmacros.core.Core core;
        com.jsmacrosce.jsmacros.client.api.library.impl.FJavaUtils javaUtils;

        public JsMacrosCE(com.jsmacrosce.jsmacros.core.Core core) {
            this.core = core;
            try {
                javaUtils = new com.jsmacrosce.jsmacros.client.api.library.impl.FJavaUtils();
            } catch (Throwable e) {

            }
        }

        @Override
        public <T> T forceUnwrap(Object what, Class<T> type) {

            if (what instanceof com.jsmacrosce.jsmacros.core.helpers.BaseHelper<?> base) {
                var raw = base.getRaw();
                return type.cast(raw);
            }
            if (what instanceof com.jsmacrosce.jsmacros.client.api.classes.math.Pos3D pos3) {
                return type.cast(new Vec3d(pos3.x, pos3.y, pos3.z));
            }
            if (what instanceof com.jsmacrosce.jsmacros.client.api.classes.math.Pos2D pos2D) {
                return type.cast(new Vec2f((float) pos2D.x, (float) pos2D.y));
            }
            if (what instanceof com.jsmacrosce.jsmacros.client.api.classes.inventory.Inventory<?> inventory) {
                return type.cast(inventory.getRawContainer());
            }
            try {
                return type.cast(what);
            } catch (Throwable e) {
                throw new UnsupportedOperationException("This type is not supported to unwrap: "
                        + what.getClass().getName());
            }
            //            try{
            //                Method method = what.getClass().getMethod("getRaw");
            //                method.setAccessible(true);
            //                return (T) method.invoke(what);
            //            }catch (Throwable e){
            //                return (T)what;
            //            }

        }

        @Override
        public Object wrap(Object object) {
            com.jsmacrosce.jsmacros.client.api.library.impl.FJavaUtils javaUtils = this.javaUtils;
            if (object instanceof Vec3d vec3d) {
                return new com.jsmacrosce.jsmacros.client.api.classes.math.Pos3D(vec3d);
            }
            if (object instanceof Vec2f vec2f) {
                return new com.jsmacrosce.jsmacros.client.api.classes.math.Pos2D(vec2f.x, vec2f.y);
            }
            if (object instanceof HandledScreen handledScreen) {
                return com.jsmacrosce.jsmacros.client.api.classes.inventory.Inventory.create(handledScreen);
            }
            Object ret = javaUtils.getHelperFromRaw(object);
            if (ret != null) {
                return ret;
            }
            throw new UnsupportedOperationException(
                    "This type of instance is not supported to wrap" + ", use JavaUtils.getHelperFromRaw instead");
        }

        @Override
        public Object newBlockData(BlockState b, BlockEntity e, BlockPos bp) {
            return new com.jsmacrosce.jsmacros.client.api.helper.world.BlockDataHelper(b, e, bp);
        }

        @Override
        public Object createInventory() {
            return com.jsmacrosce.jsmacros.client.api.classes.inventory.Inventory.create();
        }

        @Override
        public ItemStack unwrapItemStack(Object what) {
            return ((com.jsmacrosce.jsmacros.client.api.helper.inventory.ItemStackHelper) what).getRaw();
        }

        @Override
        public Object wrapItemStack(ItemStack what) {
            return new com.jsmacrosce.jsmacros.client.api.helper.inventory.ItemStackHelper(what);
        }

        @Override
        public boolean isItemEmpty(Object what) {
            return ((com.jsmacrosce.jsmacros.client.api.helper.inventory.ItemStackHelper) what).isEmpty();
        }

        @Override
        public Object getRunningCtxBinding(Object what) throws Throwable {
            com.jsmacrosce.jsmacros.core.language.EventContainer<?> context =
                    (com.jsmacrosce.jsmacros.core.language.EventContainer<?>) what;
            var ctx0 = context.getCtx().getContext();
            return ReflectHelper.invoke(ctx0, "getBindings", "js");
        }
    }
}
