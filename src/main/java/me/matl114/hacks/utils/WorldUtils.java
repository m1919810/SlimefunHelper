package me.matl114.hacks.utils;

import com.mojang.datafixers.util.Either;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.accessors.access.PendingUpdateManagerAccess;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.task.ServerFeature;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRecord;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class WorldUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final int UPDATE_BLOCK_NO_PHYSICS = 2 | 16 | 512;

    // The action must not destroy or replace existing block entities.
    public static Map<BlockPos, BlockState> predictSequencedAction(Runnable action) {
        ClientWorld world = mc.world;
        var manager = world.getPendingUpdateManager();
        PendingUpdateManagerAccess access = PendingUpdateManagerAccess.of(manager);
        int previousSequence = manager.getSequence();
        boolean previousPendingSequence = manager.hasPendingSequence();
        Map<BlockPos, IndexEntry<BlockState>> originalPending = access.getPendingBlockStates(Integer.MIN_VALUE);
        int sequence = manager.incrementSequence().getSequence();
        Map<BlockPos, BlockState> predictedStates = new HashMap<>();
        try {
            action.run();
        } finally {
            try {
                Map<BlockPos, IndexEntry<BlockState>> pendingStates = access.getPendingBlockStates(sequence);
                for (var entry : pendingStates.entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockState previousState = entry.getValue().val();
                    BlockState predictedState = world.getBlockState(pos);
                    if (predictedState != previousState) {
                        predictedStates.put(pos, predictedState);
                        BlockEntity blockEntity = world.getBlockEntity(pos);
                        restorePredictedBlock(world, pos, previousState, blockEntity);
                    }
                }
            } finally {
                try {
                    access.restorePendingUpdate(sequence, originalPending);
                } finally {
                    access.restorePredictionSequence(previousSequence, previousPendingSequence);
                }
            }
        }
        return predictedStates;
    }

    public static Map<BlockPos, BlockState> predictActionAtSequence(int sequence, Runnable action) {
        ClientWorld world = mc.world;
        var manager = world.getPendingUpdateManager();
        PendingUpdateManagerAccess access = PendingUpdateManagerAccess.of(manager);
        int currentSequence = manager.getSequence();
        boolean currentPendingSequence = manager.hasPendingSequence();
        Map<BlockPos, IndexEntry<BlockState>> beforePrediction = access.getPendingBlockStates(sequence);
        Map<BlockPos, BlockEntity> currentBlockEntities = captureBlockEntities(world, beforePrediction.keySet());
        Map<BlockPos, BlockState> currentStates = new HashMap<>();
        try {
            for (var entry : beforePrediction.entrySet()) {
                BlockPos pos = entry.getKey();
                currentStates.put(pos, world.getBlockState(pos));
                world.setBlockState(pos, entry.getValue().val(), UPDATE_BLOCK_NO_PHYSICS);
            }
            access.restorePendingUpdate(sequence, beforePrediction);
            return predictSequencedAction(action);
        } finally {
            try {
                for (var entry : currentStates.entrySet()) {
                    restorePredictedBlock(
                            world, entry.getKey(), entry.getValue(), currentBlockEntities.get(entry.getKey()));
                }
            } finally {
                try {
                    access.restorePendingUpdate(sequence, beforePrediction);
                } finally {
                    access.restorePredictionSequence(currentSequence, currentPendingSequence);
                }
            }
        }
    }

    private static Map<BlockPos, BlockEntity> captureBlockEntities(ClientWorld world, Collection<BlockPos> positions) {
        Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntities.put(pos.toImmutable(), blockEntity);
            }
        }
        return blockEntities;
    }

    private static void restorePredictedBlock(
            ClientWorld world, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        world.setBlockState(pos, state, UPDATE_BLOCK_NO_PHYSICS);
        if (blockEntity != null
                && blockEntity.supports(state)
                && world.getBlockState(pos) == state
                && (blockEntity.isRemoved() || world.getBlockEntity(pos) != blockEntity)) {
            world.removeBlockEntity(pos);
            world.addBlockEntity(blockEntity);
        }
    }

    public static boolean areWorldEquals(ClientWorld world1, ClientWorld world2) {
        return world1 == world2
                || (world1 != null
                        && world2 != null
                        && Objects.equals(
                                world1.getRegistryKey().getValue(),
                                world2.getRegistryKey().getValue()));
    }

    public static Stream<String> getPlayerListNames() {
        return mc.getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(VRecord::getName);
    }

    public static Stream<String> getWaypointNames() {

        return Stream.empty();
    }

    public static Stream<Waypoint> getWaypoints() {
        return Stream.empty();
    }

    public static Map<BlockPos, BlockState> scannChunk(Chunk chunk, BiPredicate<BlockPos, BlockState> predicate) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getStartX();
        int minY = chunk.getBottomY();
        int minZ = chunkPos.getStartZ();
        int maxX = chunkPos.getEndX();
        int section = chunk.getHighestNonEmptySection();
        int maxY = section == -1
                ? chunk.getBottomY()
                : ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(section + 1));
        int maxZ = chunkPos.getEndZ();
        Map<BlockPos, BlockState> stateMap = new LinkedHashMap<>();

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!predicate.test(pos, state)) continue;
                    stateMap.put(pos, state);
                }

        return stateMap;
    }

    public static Waypoint getWaypoint(String lookup) {
        String optionalUid;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(lookup);
        if (entry != null) {
            optionalUid = VRecord.getId(entry.getProfile()).toString();
        } else {
            optionalUid = null;
        }
        return getWaypoints()
                .filter(s -> lookup.equalsIgnoreCase(s.getDisplayName())
                        || (optionalUid != null && optionalUid.equalsIgnoreCase(s.getDisplayName())))
                .findFirst()
                .orElse(null);
    }

    /** Uses the attribute snapshot when attributeSwap is true, otherwise the supplied tool's attributes. */
    public static float getPlayerBlockBreakingSpeedWithCanMineMultiply(
            PlayerEntity player, BlockState state, ItemStack stack, boolean attributeSwap) {
        float f = stack.getMiningSpeedMultiplier(state);
        AttributeContainer attributeContainer = attributeSwap
                ? PlayerStateManager.INSTANCE.getOrCreatePlayerStatus(player).getAttribute()
                : AttributeUtils.getAttributeWith(player, Map.of(EquipmentSlot.MAINHAND, stack));
        if (f > 1.0F) {
            f += attributeContainer.getValue(EntityAttributes.MINING_EFFICIENCY);
        }

        if (StatusEffectUtil.hasHaste(player)) {
            f *= 1.0F + (float) (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2F;
        }

        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float var10000;
            switch (player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> var10000 = 0.3F;
                case 1 -> var10000 = 0.09F;
                case 2 -> var10000 = 0.0027F;
                default -> var10000 = 8.1E-4F;
            }

            float g = var10000;
            f *= g;
        }

        f *= (float) attributeContainer.getValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            f *= (float) attributeContainer.getValue(EntityAttributes.SUBMERGED_MINING_SPEED);
        }

        if (!player.isOnGround()) {
            f /= 5.0F;
        }
        int i = canToolHarvest(state, stack) ? 30 : 100;
        return f / i;
    }

    public static float calcBlockBreakingDelta(BlockState state, BlockView world, BlockPos pos) {
        return calcBlockBreakingDelta(state, world, pos, mc.player, mc.player.getMainHandStack());
    }

    public static float calcBlockBreakingDelta(
            BlockState state, BlockView world, BlockPos pos, PlayerEntity player, ItemStack tool) {
        return calcBlockBreakingDelta(state, world, pos, player, tool, ServerFeature.INSTANCE.attributeSwap.get());
    }

    public static float calcBlockBreakingDelta(
            BlockState state,
            BlockView world,
            BlockPos pos,
            PlayerEntity player,
            ItemStack tool,
            boolean attributeSwap) {
        var playerBreakSpeed = getPlayerBlockBreakingSpeedWithCanMineMultiply(player, state, tool, attributeSwap);
        return calcBlockBreakingDelta(state, world, pos, playerBreakSpeed);
    }

    public static float calcBlockBreakingDelta(
            BlockState state, BlockView world, BlockPos pos, float playerBreakSpeed) {
        float f = state.getHardness(world, pos);
        if (f == -1.0F) {
            return 0.0F;
        } else {
            return playerBreakSpeed / f;
        }
    }

    private static boolean canToolHarvest(BlockState state, ItemStack stack) {
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }

    public static boolean isServerChunkLoaded(BlockPos pos) {
        return isServerChunkLoaded(
                ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }

    public static boolean isServerPosLoaded(int blockPosX, int blockPosZ) {

        return isServerChunkLoaded(
                ChunkSectionPos.getSectionCoord(blockPosX), ChunkSectionPos.getSectionCoord(blockPosZ));
    }

    public static boolean isServerChunkLoaded(int chunkX, int chunkZ) {
        return isChunkLoaded(chunkX, chunkZ);
    }

    public static boolean isChunkLoaded(BlockPos pos) {
        return isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }

    public static boolean isChunkLoaded(int chunkX, int chunkZ) {
        return mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
    }

    public static boolean isInfiniteWater(World world, BlockPos pos) {
        int stillSourceCount = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            FluidState neighborFluid = world.getFluidState(pos.offset(direction));
            if (neighborFluid.isOf(Fluids.WATER) && neighborFluid.isStill()) {
                stillSourceCount++;
            }
        }
        if (stillSourceCount < 2) {
            return false;
        }
        BlockPos downPos = pos.down();
        BlockState downState = world.getBlockState(downPos);
        FluidState downFluid = downState.getFluidState();
        return downState.isSolid() || (downFluid.isOf(Fluids.WATER) && downFluid.isStill());
    }

    public static boolean canEntitySpawnAt(World world, BlockPos pos, EntityType<?> type) {
        BlockState state = world.getBlockState(pos);
        BlockState upState = world.getBlockState(pos.up());
        BlockState downState = world.getBlockState(pos.down());
        Vec3d spawnerCenter = pos.toBottomCenterPos();
        return downState.allowsSpawning(world, pos.down(), type)
                && world.isSpaceEmpty(type.getSpawnBox(spawnerCenter.x, spawnerCenter.y, spawnerCenter.z))
                && SpawnHelper.isClearForSpawn(world, pos, state, state.getFluidState(), type)
                && SpawnHelper.isClearForSpawn(world, pos.up(), upState, upState.getFluidState(), type);
    }

    @Getter
    @AllArgsConstructor
    public static class Waypoint {
        Either<UUID, String> source;
        NbtCompound config;
        WaypointData data;

        public String getDisplayName() {
            return getSource().map(UUID::toString, Function.identity());
        }
    }

    public static sealed interface WaypointData
            permits WaypointData.Pos, WaypointData.Chunk, WaypointData.Direction, WaypointData.Empty {
        public String getTypeName();

        public record Pos(Vec3d pos) implements WaypointData {

            @Override
            public String getTypeName() {
                return "Pos";
            }
        }

        public record Chunk(ChunkPos pos) implements WaypointData {
            @Override
            public String getTypeName() {
                return "Chunk";
            }
        }

        public record Direction(float azimuth) implements WaypointData {

            @Override
            public String getTypeName() {
                return "Direction";
            }
        }

        public record Empty() implements WaypointData {
            @Override
            public String getTypeName() {
                return "Empty";
            }
        }

        public static WaypointData EMPTY = new Empty();
    }
}
