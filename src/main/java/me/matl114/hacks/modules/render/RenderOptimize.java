package me.matl114.hacks.modules.render;

import java.util.Iterator;
import java.util.concurrent.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.accessors.access.ChunkAccess;
import me.matl114.accessors.events.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.containers.MetaData;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.apache.commons.lang3.function.BooleanConsumer;

public class RenderOptimize extends BaseModule {
    public static final String[] OPTIMIZE_ITEM_TICK = {"render-optimize", "optimize-item-tick"};
    public static final String[] OPTIMIZE_ITEM_CULLING_DISTANCE = {"render-optimize", "item-culling-distance"};

    public static final String[] OPTIMIZE_PARTICLE_TICK = {"render-optimize", "optimize-particle-tick"};

    public static final String[] OPTIMIZE_ARMOR_STAND_TICK = {"render-optimize", "optimize-armor-stand-tick"};

    public static final String[] OPTIMIZE_LABEL_RENDER = {"render-optimize", "optimize-entity-label-render"};

    public static final String[] OPTIMIZE_ENTITY_LABEL_CULLING = {
        "render-optimize", "entity-label-render-culling-distance"
    };

    public static final String[] OPTIMIZE_BLOCK_LABEL_RENDER = {"render-optimize", "optimize-block-label-render"};

    public static final String[] OPTIMIZE_BLOCK_LABEL_CULLING_DISTANCE = {
        "render-optimize", "block-label-render-culling-distance"
    };

    public static final String[] CULLING_ENABLE = {"render-optimize", "optimize-culling-enable"};

    public static final String[] CULLING_ENABLE_HOTKEY = {"render-optimize", "optimize-culling-enable-hotkey"};

    public static final String[] CULLING_ENTITY_TYPES = {"render-optimize", "optimize-culling-entity-types"};

    public static final String[] CULLING_BLOCK_ENTITY_TYPES = {"render-optimize", "optimize-culling-block-entity-types"
    };

    public static final String[] CULLING_PARTICLE_TYPES = {"render-optimize", "optimize-culling-block-entity-types"};

    public static final String[] OPTIMIZE_USE_RAYCAST = {"render-optimize", "optimize-culling-use-raycast"};

    public static final String[] OPTIMIZE_CULL_RADIUS = {"render-optimize", "optimize-culling-radius"};

    public RenderOptimize() {}

    public final FlagRef enableItemTickOpt =
            flagBuilder(Configs.RENDER_CONFIG, OPTIMIZE_ITEM_TICK).build();

    public final DoubleRef cullingDistanceItem = builder(
                    Configs.RENDER_CONFIG, OPTIMIZE_ITEM_CULLING_DISTANCE, DoubleRef.TYPE)
            .defaultValue(40.0D)
            .build();

    public final FlagRef enableParticleTickOpt =
            flagBuilder(Configs.RENDER_CONFIG, OPTIMIZE_PARTICLE_TICK).build();

    public final FlagRef enableArmorStandTickOpt =
            flagBuilder(Configs.RENDER_CONFIG, OPTIMIZE_ARMOR_STAND_TICK).build();

    public final FlagRef enableLabelRenderOpt =
            flagBuilder(Configs.RENDER_CONFIG, OPTIMIZE_LABEL_RENDER).build();

    public final DoubleRef cullingDistanceEntityLabel = builder(
                    Configs.RENDER_CONFIG, OPTIMIZE_ENTITY_LABEL_CULLING, DoubleRef.TYPE)
            .defaultValue(64.0D)
            .build();

    public final FlagRef enableBlockLabelRenderOpt =
            flagBuilder(Configs.RENDER_CONFIG, OPTIMIZE_BLOCK_LABEL_RENDER).build();

    public final DoubleRef cullingDistanceBlockLabel = builder(
                    Configs.RENDER_CONFIG, OPTIMIZE_BLOCK_LABEL_CULLING_DISTANCE, DoubleRef.TYPE)
            .defaultValue(20.0D)
            .build();

    public final FlagRef cullingEnable =
            flagBuilder(Configs.RENDER_CONFIG, CULLING_ENABLE).build();

    public final KeyBindRef keyBindRef = toggleHotkey(
                    Configs.RENDER_CONFIG, CULLING_ENABLE_HOTKEY, new MultiKeyBind(), CULLING_ENABLE)
            .build();

    public final NBTRef<RegistryRegex<EntityType<?>>> cullingTypes = builder(
                    Configs.RENDER_CONFIG,
                    CULLING_ENTITY_TYPES,
                    NBTType.<RegistryRegex<EntityType<?>>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex("^(item.*)$"), Registries.ENTITY_TYPE))
            .build();

    public final NBTRef<RegistryRegex<BlockEntityType<?>>> cullingTypes2 = builder(
                    Configs.RENDER_CONFIG,
                    CULLING_BLOCK_ENTITY_TYPES,
                    NBTType.<RegistryRegex<BlockEntityType<?>>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(
                    new Regex("^((.*sign)|barrel|skull|(.*chest)|enchanting_table)$"), Registries.BLOCK_ENTITY_TYPE))
            .build();

    public final NBTRef<RegistryRegex<ParticleType<?>>> cullingTypes3 = builder(
                    Configs.RENDER_CONFIG,
                    CULLING_PARTICLE_TYPES,
                    NBTType.<RegistryRegex<ParticleType<?>>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex("^(.*)$"), Registries.PARTICLE_TYPE))
            .build();

    public final DoubleRef cullingRadius = builder(Configs.RENDER_CONFIG, OPTIMIZE_CULL_RADIUS, DoubleRef.TYPE)
            .defaultValue(64.0D)
            .build();

    public final FlagRef cullingUseRaycast =
            flagBuilder(Configs.RENDER_CONFIG, OPTIMIZE_USE_RAYCAST).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getEntityPreTickListener(), this::onEntityTick);
        registerListener(Listener.getEntityPreTickListener(), this::onEntityCullingTick);
        registerListener(Listener.getEntityPreTickListener(), this::onEntityLabelShowTick);
        registerListener(Listener.getBlockEntityTickListener(), this::onBlockEntityTick);
        registerListener(Listener.getPostGameTick(), this::onCacheClean);
        registerListener(Listener.getPreGameTick(), this::onBlockEntityCullingTick);

        registerListener(RenderListener.getEntityRenderListener(), this::onEntityRender);
        registerListener(RenderListener.getBlockEntityRenderListener(), this::onBlockEntityRender);
    }

    ExecutorService parallelRaycastExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        parallelRaycastExecutor = Executors.newFixedThreadPool(4);
    }

    @Override
    public void onRemove() {
        super.onRemove();
        if (parallelRaycastExecutor != null && !parallelRaycastExecutor.isShutdown()) {
            parallelRaycastExecutor.shutdown();
        }
    }

    public void onEntityTick(Event<Entity> event) {
        Entity entity = event.context();
        if (entity instanceof ItemEntity item && enableItemTickOpt.get()) {
            boolean itemInFluid = item.isInFluid();
            boolean itemFalling = !item.isOnGround() && item.getFinalGravity() > 0;
            if (!itemInFluid && !itemFalling) {
                event.cancel();
                return;
            }
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null
                    && player.getPos().squaredDistanceTo(item.getPos()) > MathUtils.s2(cullingDistanceItem.get())) {
                event.cancel();
                return;
            }
        } else if (entity instanceof ArmorStandEntity armorStand && enableArmorStandTickOpt.get()) {
            event.cancel();
            return;
        }
    }

    public void onEntityCullingTick(Event<Entity> event) {
        Entity entity = event.context();
        if (entity instanceof MetadataHolder holder) {
            MetaData metaData;
            RenderController controller;
            Vec3d pos = RenderUtils.getCameraPos();
            EntityType<?> types = entity.getType();
            if (cullingEnable.get()) {
                if (this.cullingTypes.get().test(types)) {
                    metaData = holder.getMetadata();
                    controller = metaData.getOrPut(this, KEY_RENDER_CONTROL, RenderController::new);

                    // do not hide nearby entity
                    Box box = entity.getBoundingBox();
                    if (box.squaredMagnitude(pos) < 16) {
                        controller.hideAll = false;
                    } else if (box.squaredMagnitude(pos) > MathUtils.s2(cullingRadius.get())) {
                        controller.hideAll = true;
                    } else {
                        Vec3d playerTo = pos.subtract(entity.getPos());
                        Vec3d playerLook = RenderUtils.getCameraLookVec(0.0F);
                        if (playerLook.dotProduct(playerTo) > 0) {
                            controller.hideAll = true;
                        } else {
                            if (cullingUseRaycast.get()) {
                                delayScheduleRaycast(box, controller, (val) -> {
                                    controller.hideAll = val;
                                });
                            } else {
                                controller.hideAll = false;
                            }
                        }
                    }

                } else {
                    if (!holder.isMetaEmpty()) {
                        metaData = holder.getMetadata();
                        controller = metaData.get(this, KEY_RENDER_CONTROL);
                        if (controller != null) {
                            controller.hideAll = false;
                        }
                    }
                }
            }
        }
    }

    public void onEntityLabelShowTick(Event<Entity> event) {
        if (enableLabelRenderOpt.get()) {
            Entity entity = event.context();
            // do not hide player nametags
            if (entity instanceof PlayerEntity) {
                return;
            }
            if (entity.hasCustomName() && entity instanceof MetadataHolder holder) {
                MetaData metaData = holder.getMetadata();
                RenderController controller = metaData.getOrPut(this, KEY_RENDER_CONTROL, RenderController::new);
                Vec3d pos = RenderUtils.getCameraPos();
                if (entity.getPos().squaredDistanceTo(pos) > MathUtils.s2(cullingDistanceEntityLabel.get())) {
                    controller.hideLabelFront = true;
                } else {
                    Vec3d toPlayer = pos.subtract(entity.getPos());
                    if (toPlayer.dotProduct(RenderUtils.getCameraLookVec(0.0F)) > 0) {
                        controller.hideLabelFront = true;
                    } else {
                        controller.hideLabelFront = false;
                    }
                    // do not cull label
                }
            }
        }
    }

    public void onEntityRender(Event<Entity> event) {
        if (event.isCancelled() || !cullingEnable.get()) return;
        Entity entity = event.context();
        if (entity instanceof MetadataHolder holder
                && !holder.isMetaEmpty()
                && holder.getMetadata().get(this, KEY_RENDER_CONTROL) instanceof RenderController controller
                && controller.hideAll) {
            event.cancel();
        }
    }

    public static final String KEY_RENDER_CONTROL = "slimefunhelper:render_optimize/render_controller";

    public void onBlockEntityTick(Event<BlockEntityTickInvoker> event) {
        BlockEntityTickInvoker entity = event.context();
        BlockPos blockPos = entity.getPos();
        BlockEntity blockEntity = mc.world.getBlockEntity(blockPos);
        if (blockEntity instanceof MetadataHolder holder) {
            // more choice
            if (blockEntity instanceof SignBlockEntity) {
                MetaData metaData = holder.getMetadata();
                RenderController controller = metaData.getOrPut(this, KEY_RENDER_CONTROL, RenderController::new);
                BlockState blockState = mc.world.getBlockState(entity.getPos());
                if (enableBlockLabelRenderOpt.get()) {
                    if (blockState.getBlock() instanceof AbstractSignBlock signBlock) {
                        // sign logic
                        Vec3d cameraPos = RenderUtils.getCameraPos();
                        if (blockPos.getSquaredDistance(cameraPos) > MathUtils.s2(cullingDistanceBlockLabel.get())) {
                            controller.hideLabelBack = controller.hideLabelFront = true;
                        } else {
                            float degree = signBlock.getRotationDegrees(blockState);
                            float yawRad = degree * MathHelper.RADIANS_PER_DEGREE;
                            double frontX = -MathHelper.sin(yawRad);
                            double frontZ = MathHelper.cos(yawRad);
                            Vec3d frontNormal = new Vec3d(frontX, 0, frontZ).normalize();
                            Vec3d signCenter = Vec3d.of(blockPos).add(signBlock.getCenter(blockState));
                            Vec3d toPlayer = cameraPos.subtract(signCenter);
                            Vec3d playerLook = RenderUtils.getCameraLookVec(0.0f);
                            boolean showFront = true;
                            boolean showBack = true;
                            // culling back entities
                            if (playerLook.dotProduct(toPlayer) > 0) {
                                showFront = false;
                                showBack = false;
                            } else {
                                if (toPlayer.dotProduct(frontNormal) > 0) {
                                    showBack = false;
                                } else {
                                    showFront = false;
                                }
                            }
                            if ((showBack || showFront) && cullingUseRaycast.get()) {
                                // delay update
                                final boolean showBack0 = showBack;
                                final boolean showFront0 = showFront;
                                Box box = Box.from(Vec3d.of(blockPos));
                                delayScheduleRaycast(box, controller, (val) -> {
                                    if (!val) {
                                        controller.hideLabelBack = !showBack0;
                                        controller.hideLabelFront = !showFront0;
                                    } else {
                                        controller.hideLabelFront = true;
                                        controller.hideLabelBack = true;
                                    }
                                });

                            } else {
                                controller.hideLabelBack = !showBack;
                                controller.hideLabelFront = !showFront;
                            }
                        }
                    } else {
                        controller.hideLabelBack = false;
                        controller.hideLabelFront = false;
                    }
                } else {
                    controller.hideLabelFront = false;
                    controller.hideLabelBack = false;
                }
            }
        }
    }

    public void canChunkBeSeen(int chunkX, int chunkZ, Vec3d cameraPos, Vec3d cameraLook) {}

    public void onBlockEntityCullingTick(Event<ClientPlayerEntity> event) {

        if (mc.world != null) {
            double maxDistance = cullingRadius.get();
            int maxChunkDistance = (int) ((cullingRadius.get() + 1) / 16 + 1);
            Vec3d pos = RenderUtils.getCameraPos();
            BlockPos cameraBlock = BlockPos.ofFloored(pos);
            int chunkX = cameraBlock.getX() >> 4;
            int chunkZ = cameraBlock.getZ() >> 4;
            for (var chunk : CommonUtils.chunks(false)) {
                ChunkPos cpos = chunk.getPos();
                if (Math.abs(cpos.x - chunkX) <= maxChunkDistance && Math.abs(cpos.z - chunkZ) <= maxChunkDistance) {
                    for (var entry : ChunkAccess.of(chunk).blockEntities()) {
                        BlockPos blockPos = entry.getKey();
                        Box box = Box.from(Vec3d.of(blockPos));
                        if (box.squaredMagnitude(pos) <= MathUtils.s2(maxDistance)) {
                            BlockEntity blockEntity = entry.getValue();
                            if (blockEntity instanceof MetadataHolder holder) {
                                MetaData metaData;
                                RenderController controller;
                                BlockEntityType<?> types = blockEntity.getType();
                                if (cullingEnable.get()) {
                                    if (cullingTypes2.get().test(types)) {
                                        metaData = holder.getMetadata();
                                        controller = metaData.getOrPut(this, KEY_RENDER_CONTROL, RenderController::new);

                                        // do not hide nearby entity
                                        if (box.squaredMagnitude(pos) < 16) {
                                            controller.hideAll = false;
                                        } else {
                                            Vec3d playerTo = pos.subtract(blockPos.toCenterPos());
                                            Vec3d playerLook = RenderUtils.getCameraLookVec(0.0F);
                                            if (playerLook.dotProduct(playerTo) > 0) {
                                                controller.hideAll = true;
                                            } else {
                                                if (cullingUseRaycast.get()) {
                                                    delayScheduleRaycast(box, controller, (val) -> {
                                                        controller.hideAll = val;
                                                    });
                                                } else {
                                                    controller.hideAll = false;
                                                }
                                            }
                                        }
                                    } else {
                                        if (!holder.isMetaEmpty()) {
                                            metaData = holder.getMetadata();
                                            controller = metaData.get(this, KEY_RENDER_CONTROL);
                                            if (controller != null) {
                                                controller.hideAll = false;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void onBlockEntityRender(Event<BlockEntity> event) {
        if (event.isCancelled() || !cullingEnable.get()) return;
        BlockEntity entity = event.context();
        BlockEntityType<?> type = entity.getType();
        if (cullingTypes2.get().test(type)) {
            Box box = Box.from(Vec3d.of(entity.getPos()));
            double sq = box.squaredMagnitude(RenderUtils.getCameraPos());
            // use distance first
            if (sq < 16) {
                return;
            } else if (sq > MathUtils.s2(cullingRadius.get())) {
                event.cancel();
                return;
                // then calculate
                // may contains old data, but will refresh next tick
            } else if (entity instanceof MetadataHolder holder
                    && !holder.isMetaEmpty()
                    && holder.getMetadata().get(this, KEY_RENDER_CONTROL) instanceof RenderController controller
                    && controller.hideAll) {
                event.cancel();
            }
        }
    }

    public boolean shouldCancelShowDisplayName(Entity entity) {
        if (enableLabelRenderOpt.get()
                && entity instanceof MetadataHolder holder
                && !holder.isMetaEmpty()
                && holder.getMetadata().get(this, KEY_RENDER_CONTROL) instanceof RenderController controller) {
            return controller.hideLabelFront;
        }
        return false;
    }

    public void delayScheduleRaycast(Box box, RenderController controller, BooleanConsumer consumer) {
        if ((parallelRaycastExecutor == null || parallelRaycastExecutor.isShutdown())) {
            consumer.accept(false);
        } else if (mc.getCameraEntity() != null && mc.getCameraEntity().isSpectator()) {
            // support spectator mode
            consumer.accept(false);
        } else if (controller.lastUpdateRaycastTick + 2 > Tasks.getTick()) {
            //
            consumer.accept(controller.raycastResult);
            return;
        } else {
            Vec3d camera = RenderUtils.getCameraPos();
            controller.lastUpdateRaycastTick = Tasks.getTick();
            CompletableFuture.runAsync(
                    () -> {
                        controller.raycastResult = raycastFullBlockAsync(box, camera, controller);
                        controller.lastUpdateRaycastTick = Tasks.getTick();
                        consumer.accept(controller.raycastResult);
                        // again update
                    },
                    parallelRaycastExecutor);
        }
    }

    public volatile ConcurrentHashMap<Long, Boolean> cache = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public void onCacheClean(Event<ClientPlayerEntity> event) {
        if (cullingUseRaycast.get()) {
            tickCounter += 1;
            if (tickCounter > 1) {
                tickCounter = 0;
                cache = new ConcurrentHashMap<>();
            }
        }
    }

    public boolean raycastFullBlockAsync(Box box, Vec3d cameraPos, RenderController controller) {
        boolean smallBox = box.getMaxPos().subtract(box.getMinPos()).lengthSquared() < 1e-2;
        Vec3d[] corners = smallBox
                ? new Vec3d[] {box.getCenter()}
                : new Vec3d[] {
                    new Vec3d(box.minX, box.minY, box.minZ), // 000
                    new Vec3d(box.maxX, box.minY, box.minZ), // 100
                    new Vec3d(box.minX, box.maxY, box.minZ), // 010
                    new Vec3d(box.maxX, box.maxY, box.minZ), // 110
                    new Vec3d(box.minX, box.minY, box.maxZ), // 001
                    new Vec3d(box.maxX, box.minY, box.maxZ), // 101
                    new Vec3d(box.minX, box.maxY, box.maxZ), // 011
                    new Vec3d(box.maxX, box.maxY, box.maxZ) // 111
                };

        ConcurrentHashMap<Long, Boolean> cacheResults = cache;

        ClientWorld mcwolrd = mc.world;
        if (mcwolrd == null) return false;
        for (var start : corners) {
            Iterator<BlockPos> blockPosIterator =
                    RaycastUtils.createRaycastBlockPosIterator(mc.world, start, cameraPos);
            int blockCount = 0;
            long startPos = BlockPos.ofFloored(start).asLong();
            while (blockPosIterator.hasNext()) {
                BlockPos blockPos = blockPosIterator.next();
                long posId = blockPos.asLong();
                if (startPos == posId) continue;
                boolean checkIsBlock;
                Boolean cacheR = cacheResults.get(posId);
                if (cacheR == null) {
                    BlockState state = mcwolrd.getBlockState(blockPos);
                    checkIsBlock = !state.isAir() && state.isOpaque() && state.isFullCube(mcwolrd, blockPos);
                    cacheResults.put(posId, checkIsBlock ? Boolean.TRUE : Boolean.FALSE);
                } else {
                    checkIsBlock = cacheR;
                }
                if (checkIsBlock) {
                    blockCount += 1;
                    if (blockCount >= 1) {
                        break;
                    }
                }
            }
            // can be seen
            if (blockCount < 1) {
                return false;
            }
        }
        return true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class RenderController {
        boolean hideLabelFront = false;
        boolean hideLabelBack = false;
        boolean hideAll = false;
        boolean raycastResult = false;
        int lastUpdateRaycastTick = 0;
    }
}
