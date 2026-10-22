package me.matl114.hacks.modules.render;

import java.util.concurrent.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.accessors.access.ChunkAccess;
import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Regex;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.apache.commons.lang3.function.BooleanConsumer;

public class RenderOptimize extends BaseModule {
    public final ModulePath renderOptimize = makePath(Configs.RENDER_CONFIG, "render-optimize");

    public RenderOptimize() {
        super("Optimize");
    }

    public final FlagRef enableItemTickOpt =
            flagBuilder(renderOptimize.add("optimize-item-tick")).build();

    public final DoubleRef cullingDistanceItem = builder(renderOptimize.add("item-culling-distance"), DoubleRef.TYPE)
            .defaultValue(40.0D)
            .validator(Configs.doubleRange(0.0D, 360.0D))
            .build();

    public final FlagRef enableParticleTickOpt =
            flagBuilder(renderOptimize.add("optimize-particle-tick")).build();

    public final FlagRef enableArmorStandTickOpt =
            flagBuilder(renderOptimize.add("optimize-armor-stand-tick")).build();

    public final FlagRef enableLabelRenderOpt =
            flagBuilder(renderOptimize.add("optimize-entity-label-render")).build();

    public final DoubleRef cullingDistanceEntityLabel = builder(
                    renderOptimize.add("entity-label-render-culling-distance"), DoubleRef.TYPE)
            .defaultValue(64.0D)
            .validator(Configs.doubleRange(0.0D, 360.0D))
            .build();

    public final FlagRef enableBlockLabelRenderOpt =
            flagBuilder(renderOptimize.add("optimize-block-label-render")).build();

    public final DoubleRef cullingDistanceBlockLabel = builder(
                    renderOptimize.add("block-label-render-culling-distance"), DoubleRef.TYPE)
            .defaultValue(20.0D)
            .validator(Configs.doubleRange(0.0D, 360.0D))
            .build();

    public final FlagRef cullingEnable =
            flagBuilder(renderOptimize.add("optimize-culling-enable")).build();

    public final KeyBindRef keyBindRef = toggleHotkey(
                    renderOptimize.add("optimize-culling-enable-hotkey"),
                    new MultiKeyBind(),
                    renderOptimize.add("optimize-culling-enable"))
            .build();

    public final NBTRef<EntrySet<EntityType<?>>> cullingTypes = builder(
                    renderOptimize.add("optimize-culling-entity-types"), EntrySet.<EntityType<?>>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(item.*)$"), Registries.ENTITY_TYPE))
            .build();

    public final NBTRef<EntrySet<BlockEntityType<?>>> cullingTypes2 = builder(
                    renderOptimize.add("optimize-culling-block-entity-types"), EntrySet.<BlockEntityType<?>>parameter())
            .defaultValue(new EntrySet<>(
                    new Regex("^((.*sign)|barrel|skull|(.*chest)|enchanting_table)$"), Registries.BLOCK_ENTITY_TYPE))
            .build();

    public final NBTRef<EntrySet<ParticleType<?>>> cullingTypes3 = builder(
                    renderOptimize.add("optimize-culling-block-entity-types"), EntrySet.<ParticleType<?>>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(.*)$"), Registries.PARTICLE_TYPE))
            .build();

    public final DoubleRef cullingRadius = builder(renderOptimize.add("optimize-culling-radius"), DoubleRef.TYPE)
            .defaultValue(64.0D)
            .validator(Configs.doubleRange(0.0D, 360.0D))
            .build();

    public final FlagRef cullingUseRaycast =
            flagBuilder(renderOptimize.add("optimize-culling-use-raycast")).build();

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
                item.setPosition(item.getTrackedPosition().getPos());
                return;
            }
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null
                    && player.getPos().squaredDistanceTo(item.getPos()) > MathUtils.s2(cullingDistanceItem.get())) {
                event.cancel();
                item.setPosition(item.getTrackedPosition().getPos());
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

    public void onBlockEntityCullingTick() {

        if (mc.world != null && cullingEnable.get()) {
            double maxDistance = cullingRadius.get();
            int maxChunkDistance = (int) ((cullingRadius.get() + 1) / 16 + 1);
            Vec3d pos = RenderUtils.getCameraPos();
            BlockPos cameraBlock = BlockPos.ofFloored(pos);
            int chunkX = cameraBlock.getX() >> 4;
            int chunkZ = cameraBlock.getZ() >> 4;
            for (var chunk : CommonUtils.chunks(false)) {
                ChunkPos cpos = chunk.getPos();
                if (Math.abs(cpos.x - chunkX) <= maxChunkDistance && Math.abs(cpos.z - chunkZ) <= maxChunkDistance) {
                    for (var entry : ChunkAccess.of(chunk).blockEntityEntries()) {
                        BlockPos blockPos = entry.getKey();
                        Box box = Box.from(Vec3d.of(blockPos));
                        if (box.squaredMagnitude(pos) <= MathUtils.s2(maxDistance)) {
                            BlockEntity blockEntity = entry.getValue();
                            if (blockEntity instanceof MetadataHolder holder) {
                                MetaData metaData;
                                RenderController controller;
                                BlockEntityType<?> types = blockEntity.getType();
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

    public void onCacheClean() {
        if (cullingUseRaycast.get()) {
            tickCounter += 1;
            if (tickCounter > 1) {
                tickCounter = 0;
                cache = new ConcurrentHashMap<>();
            }
        }
    }

    public boolean raycastFullBlockAsync(Box box, Vec3d cameraPos, RenderController controller) {
        ClientWorld world = mc.world;
        if (world == null) return false;

        Vec3d center = box.getCenter();
        double xSize = box.maxX - box.minX;
        double ySize = box.maxY - box.minY;
        double zSize = box.maxZ - box.minZ;
        Vec3d[] samples;
        if (xSize <= ySize && xSize <= zSize) {
            double x = center.x;
            samples = new Vec3d[] {
                new Vec3d(x, box.minY, box.minZ),
                new Vec3d(x, box.minY, box.maxZ),
                new Vec3d(x, box.maxY, box.minZ),
                new Vec3d(x, box.maxY, box.maxZ),
                center
            };
        } else if (ySize <= zSize) {
            double y = center.y;
            samples = new Vec3d[] {
                new Vec3d(box.minX, y, box.minZ),
                new Vec3d(box.minX, y, box.maxZ),
                new Vec3d(box.maxX, y, box.minZ),
                new Vec3d(box.maxX, y, box.maxZ),
                center
            };
        } else {
            double z = center.z;
            samples = new Vec3d[] {
                new Vec3d(box.minX, box.minY, z),
                new Vec3d(box.minX, box.maxY, z),
                new Vec3d(box.maxX, box.minY, z),
                new Vec3d(box.maxX, box.maxY, z),
                center
            };
        }

        for (Vec3d sample : samples) {
            var hit = world.raycast(new RaycastContext(
                    cameraPos,
                    sample,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
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
