package me.matl114.hacks.modules.render;

import static me.matl114.utils.ColorUtils.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.matl114.accessors.access.ChunkAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.*;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRender;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class ChestESP extends BaseModule {
    public final ModulePath detectBlock = makePath(Configs.RENDER_CONFIG, "detect-block");
    public final ModulePath chestEsp = detectBlock.add("chest-esp");

    public ChestESP() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(chestEsp.add("enable")).build();

    public final NBTRef<RegistryRegex<BlockEntityType<?>>> typeFilter = builder(
                    chestEsp.add("enable-types"), RegistryRegex.<BlockEntityType<?>>parameter())
            .defaultValue(new RegistryRegex<>(new Regex("^(.*chest|barrel|.*box)$"), Registries.BLOCK_ENTITY_TYPE))
            .build();

    public final NBTRef<TracingOption> enableLines = builder(chestEsp.add("esp-trace-options"), TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();

    public final NBTRef<EntryPrimitiveMap<BlockEntityType<?>, TextColor>> colorMap = builder(
                    chestEsp.add("color-map"), EntryPrimitiveMap.<BlockEntityType<?>, TextColor>parameter())
            .defaultValue(new EntryPrimitiveMap<>(
                    Registries.BLOCK_ENTITY_TYPE,
                    NBTTypes.COLOR_TYPE,
                    Map.of(
                            BlockEntityType.CHEST, color(Formatting.GREEN),
                            BlockEntityType.BARREL, color(Formatting.GREEN),
                            BlockEntityType.SHULKER_BOX, color(Color.MAGENTA),
                            BlockEntityType.TRAPPED_CHEST, TextColor.fromRgb(0xFF8000),
                            BlockEntityType.FURNACE, color(Formatting.WHITE),
                            BlockEntityType.ENDER_CHEST, color(Color.CYAN),
                            BlockEntityType.DROPPER, color(Formatting.WHITE),
                            BlockEntityType.DISPENSER, color(Formatting.WHITE),
                            BlockEntityType.HOPPER, color(Formatting.AQUA)),
                    color(Formatting.GREEN)))
            .build();

    public Map<BlockPos, BlockEntity> renderPositions = new HashMap<>();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getBlockEntityRenderListener(), this::onBlockEntityRender);
        registerListener(Listener.getPreGameTick(), this::onSwapRenderContent);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
    }

    public void onBlockEntityRender(Event<BlockEntity> blockEntityEvent) {}

    public int tick4 = 0;

    public void onSwapRenderContent(Event<ClientPlayerEntity> clientPlayerEntityEvent) {
        if (checkNull()) return;
        if (tick4 < 4) {
            tick4 += 1;

        } else {
            renderPositions.clear();
        }
        if (enable.get()) {
            for (var chunk : CommonUtils.chunks(false)) {
                for (var blockEntities : ChunkAccess.of(chunk).blockEntities()) {
                    if (typeFilter.get().test(blockEntities.getValue().getType())) {
                        renderPositions.put(blockEntities.getKey(), blockEntities.getValue());
                    }
                }
            }
        }

        //        tick4 = 0;
        //        if(!collectingRenderPositions.isEmpty() || !renderPositions.isEmpty()){
        //            renderPositions = collectingRenderPositions;
        //            collectingRenderPositions = new HashMap<>();
        //        }

    }

    public void onRender(Event<MatrixStack> render) {
        if (enable.get()) {
            MatrixStack stack = render.context();
            RenderUtils.startDrawVirtual(stack);
            try {
                List<IndexEntry<Box>> boxes = new ArrayList<>();
                List<IndexEntry<Vec3d>> lines = new ArrayList<>();

                for (var entry : renderPositions.entrySet()) {
                    dispatchBlockEntityRender(entry.getValue(), entry.getKey(), stack, boxes, lines);
                }
                Vec3d cameraPosNeg = RenderUtils.getCameraPos().negate();
                VRender.getInstance()
                        .createQuadsLayer(
                                ((operation, vertexConsumer) -> {
                                    if (!boxes.isEmpty()) {
                                        for (IndexEntry<Box> boxEntry : boxes) {
                                            var box = boxEntry.val().offset(cameraPosNeg);
                                            operation.drawSolidBoxQuad(
                                                    stack,
                                                    vertexConsumer,
                                                    box.getMinPos(),
                                                    box.getMaxPos(),
                                                    boxEntry.index());
                                        }
                                    }
                                }),
                                true);
                VRender.getInstance().createLinesLayer(((operation, vertexConsumer) -> {
                    if (!boxes.isEmpty()) {
                        for (var boxEntry : boxes) {
                            var box = boxEntry.val().offset(cameraPosNeg);
                            operation.drawOutlinedBox(
                                    stack,
                                    vertexConsumer,
                                    box.getMinPos(),
                                    box.getMaxPos(),
                                    ColorUtils.withAlphaInt(boxEntry.index(), 0.5F));
                        }
                    }
                    if (!lines.isEmpty()) {
                        Vec3d traceOrigin = RenderUtils.getTracerOrigin(0.0F);
                        for (var line : lines) {
                            operation.drawLine(
                                    stack,
                                    vertexConsumer,
                                    traceOrigin,
                                    line.val().add(cameraPosNeg),
                                    line.index());
                        }
                    }
                }));
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    public void dispatchBlockEntityRender(
            BlockEntity blockEntity,
            BlockPos blockPos,
            MatrixStack matrixStack,
            List<IndexEntry<Box>> boxes,
            List<IndexEntry<Vec3d>> lines) {
        TextColor color = colorMap.get().getOrDefault(blockEntity.getType());
        if (color == null) return;
        TracingOption option = enableLines.get();
        if (option.box()) {
            BlockState state = blockEntity.getCachedState();
            Box outBox = handleDoubleChestBox(state, blockPos);
            if (outBox != null) {
                boxes.add(new IndexEntry<>(ColorUtils.withAlphaInt(color.getRgb(), 0.25F), outBox.offset(blockPos)));
            }
        }
        if (option.line()) {
            lines.add(new IndexEntry<>(ColorUtils.withAlphaInt(color.getRgb(), 1.0F), blockPos.toCenterPos()));
        }
    }

    public Box handleDoubleChestBox(BlockState state, BlockPos pos) {
        VoxelShape shape1 = state.getOutlineShape(mc.world, pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                if (type == ChestType.RIGHT) {
                    return null;
                } else {
                    Direction facing = ChestBlock.getFacing(state);
                    BlockPos otherChest = pos.offset(facing);
                    BlockState state2 = mc.world.getBlockState(otherChest);
                    VoxelShape shape2 = state2.getOutlineShape(mc.world, otherChest);
                    if (!shape2.isEmpty()) {
                        Box otherBox = shape2.getBoundingBox().offset(facing.getDoubleVector());
                        if (!shape1.isEmpty()) {
                            Box box = shape1.getBoundingBox();
                            return box.union(otherBox);
                        } else {
                            return otherBox;
                        }
                    }
                }
            }
        }
        return shape1.isEmpty() ? null : shape1.getBoundingBox();
    }
}
