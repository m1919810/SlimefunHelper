package me.matl114.hacks.modules.render;

import java.lang.reflect.Method;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class Xray extends BaseModule {
    public static Xray INSTANCE;

    // meteor 1.21.1: Sodium and Iris shader packs cannot draw translucent terrain, so opacity is forced to 0
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static final Object IRIS_API;
    private static final Method IRIS_SHADER_PACK_IN_USE;

    static {
        Object api = null;
        Method shaderPackInUse = null;
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                api = null;
                shaderPackInUse = null;
            }
        }
        IRIS_API = api;
        IRIS_SHADER_PACK_IN_USE = shaderPackInUse;
    }

    public static final List<Block> ORES = List.of(
            Blocks.COAL_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.COPPER_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.NETHER_GOLD_ORE,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.ANCIENT_DEBRIS);

    private final ModulePath xrayPath = makePath(Configs.RENDER_CONFIG, "xray");

    public Xray() {
        super("Xray");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final FlagRef enable = flagBuilder(xrayPath.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(xrayPath.addHotkey(), new MultiKeyBind(), xrayPath.addEnable())
            .build();

    public final NBTRef<EntrySet<Block>> whitelist = builder(xrayPath, EntrySet.<Block>parameter())
            .path(xrayPath.add("whitelist").toPath())
            .defaultValue(new EntrySet<>(Registries.BLOCK, ORES))
            .updateListener(ignored -> refreshIfActive())
            .build();

    public final IntRef opacity = builder(xrayPath.add("opacity"), IntRef.TYPE)
            .defaultValue(25)
            .validator(Configs.intRange(0, 255))
            .updateListener(ignored -> refreshIfActive())
            .build();

    public final EnumRef<FluidOpacity> fluidOpacity = builder(xrayPath.add("fluid-opacity"), FluidOpacity.class)
            .defaultValue(FluidOpacity.Both)
            .updateListener(ignored -> refreshIfActive())
            .build();

    public final FlagRef exposedOnly = builder(xrayPath.add("exposed-only"), Boolean.class)
            .defaultValue(false)
            .updateListener(ignored -> refreshIfActive())
            .build();

    public final FlagRef affectBlockEntities = builder(xrayPath.add("affect-block-entities"), Boolean.class)
            .defaultValue(true)
            .updateListener(ignored -> refreshIfActive())
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getBlockEntityRenderListener(), this::onBlockEntityRender);
    }

    private void onBlockEntityRender(Event<BlockEntity> event) {
        if (!affectBlockEntities.get() || event.isCancelled()) return;

        BlockEntity blockEntity = event.context();
        Block block = blockEntity.getCachedState().getBlock();
        if (isActive() && opacity.get() == 0 && isBlocked(block, blockEntity.getPos())) {
            event.cancel();
        }
    }

    @Override
    public void onEnableModule() {
        refreshWorldRenderer();
    }

    @Override
    public void onDisableModule() {
        refreshWorldRenderer();
    }

    private void refreshIfActive() {
        if (isActive()) refreshWorldRenderer();
    }

    private void refreshWorldRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.worldRenderer == null) return;

        client.worldRenderer.reload();
    }

    public boolean isBlocked(Block block, BlockPos pos) {
        return !(whitelist.get().test(block) && (!exposedOnly.get() || pos == null || isExposed(pos)));
    }

    private boolean isExposed(BlockPos pos) {
        if (mc.world == null) return false;

        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            BlockState adjacentState = mc.world.getBlockState(adjacentPos);
            if (!adjacentState.isOpaque()) return true;
        }

        return false;
    }

    public boolean modifyDrawSide(BlockState state, BlockView world, BlockPos pos, Direction facing, boolean original) {
        if (!original && !isBlocked(state.getBlock(), pos)) {
            BlockPos adjacentPos = pos.offset(facing);
            BlockState adjacentState = world.getBlockState(adjacentPos);
            return adjacentState.getCullingFace(world, adjacentPos, facing.getOpposite()) != VoxelShapes.fullCube()
                    || adjacentState.getBlock() != state.getBlock()
                    || !adjacentState.isSolidBlock(world, adjacentPos)
                    || isBlocked(adjacentState.getBlock(), adjacentPos);
        }

        return original;
    }

    public static boolean isXrayActive() {
        return INSTANCE != null && INSTANCE.isActive();
    }

    public static int getAlpha(BlockState state, BlockPos pos) {
        Xray xray = INSTANCE;
        if (xray != null && xray.isActive() && xray.isBlocked(state.getBlock(), pos)) {
            return isTranslucencyUnsupported() ? 0 : xray.opacity.get();
        }

        return -1;
    }

    public static int getFluidAlpha(FluidState state, BlockPos pos) {
        Xray xray = INSTANCE;
        if (xray != null
                && xray.isActive()
                && xray.shouldApplyFluidOpacity(state)
                && xray.isBlocked(state.getBlockState().getBlock(), pos)) {
            return isTranslucencyUnsupported() ? 0 : xray.opacity.get();
        }

        return -1;
    }

    private static boolean isTranslucencyUnsupported() {
        if (SODIUM_LOADED) return true;
        if (IRIS_API == null) return false;
        try {
            return (boolean) IRIS_SHADER_PACK_IN_USE.invoke(IRIS_API);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }

    private boolean shouldApplyFluidOpacity(FluidState state) {
        return switch (fluidOpacity.get()) {
            case None -> false;
            case Water -> state.isIn(FluidTags.WATER);
            case Lava -> state.isIn(FluidTags.LAVA);
            case Both -> state.isIn(FluidTags.WATER) || state.isIn(FluidTags.LAVA);
        };
    }

    public enum FluidOpacity implements ConfigEnum {
        None,
        Water,
        Lava,
        Both;

        @Override
        public String getConfigEnumType() {
            return "xray_fluid_opacity";
        }
    }
}
