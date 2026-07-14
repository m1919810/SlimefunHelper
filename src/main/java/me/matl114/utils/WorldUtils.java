package me.matl114.utils;

import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.versioned.api.VRecord;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.waypoint.TrackedWaypoint;

public class WorldUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

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

        return getWaypointInternal()
                .map(TrackedWaypoint::getSource)
                .flatMap(s -> s.map(
                        uid -> {
                            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(uid);
                            if (entry != null) {
                                return Stream.of(uid.toString(), VRecord.getName(entry.getProfile()));
                            } else {
                                return Stream.of(uid.toString());
                            }
                        },
                        name -> {
                            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(name);
                            if (entry != null) {
                                return Stream.of(name, VRecord.getName(entry.getProfile()));
                            } else {
                                return Stream.of(name);
                            }
                        }));
    }

    private static Stream<TrackedWaypoint> getWaypointInternal() {
        List<TrackedWaypoint> waypoints = new ArrayList<>();
        mc.getNetworkHandler().getWaypointHandler().forEachWaypoint(mc.player, waypoints::add);
        return waypoints.stream();
    }

    public static Stream<Waypoint> getWaypoints() {
        return getWaypointInternal().map(WorldUtils::translate);
    }

    private static Waypoint translate(TrackedWaypoint s) {
        ByteBuf buf = NetworkUtils.createBytebuf();
        s.writeBuf(buf);
        PacketByteBuf byteBuf = new PacketByteBuf(buf);
        Either<UUID, String> either = byteBuf.readEither(Uuids.PACKET_CODEC, PacketByteBuf::readString);
        net.minecraft.world.waypoint.Waypoint.Config config = (net.minecraft.world.waypoint.Waypoint.Config)
                net.minecraft.world.waypoint.Waypoint.Config.PACKET_CODEC.decode(byteBuf);
        var configNbt = (NbtCompound) net.minecraft.world.waypoint.Waypoint.Config.CODEC
                .encodeStart(NbtOps.INSTANCE, config)
                .getOrThrow();
        int varInt = byteBuf.readVarInt();

        WaypointData data =
                switch (varInt) {
                    case 1 -> new WaypointData.Pos(
                            new Vec3d(byteBuf.readVarInt(), byteBuf.readVarInt(), byteBuf.readVarInt()));
                    case 2 -> new WaypointData.Chunk(new ChunkPos(byteBuf.readVarInt(), byteBuf.readVarInt()));
                    case 3 -> new WaypointData.Direction(byteBuf.readFloat());
                    default -> WaypointData.EMPTY;
                };
        buf.release();
        return new Waypoint(either, configNbt, data);
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

    public static float getPlayerBlockBreakingSpeedAt(BlockState state) {
        return getPlayerBlockBreakingSpeedWithCanMineMultiply(mc.player, state, mc.player.getMainHandStack());
    }

    public static float getPlayerBlockBreakingSpeedWithCanMineMultiply(
            PlayerEntity player, BlockState state, ItemStack stack) {
        float f = stack.getMiningSpeedMultiplier(state);
        if (f > 1.0F) {
            AttributeContainer attributeContainer =
                    AttributeUtils.getAttributeWith(player, Map.of(EquipmentSlot.MAINHAND, stack));
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

        f *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            f *= (float) player.getAttributeInstance(EntityAttributes.SUBMERGED_MINING_SPEED)
                    .getValue();
        }

        if (!player.isOnGround()) {
            f /= 5.0F;
        }
        int i = canToolHarvest(state, stack) ? 30 : 100;
        return f / i;
    }

    public static float calcBlockBreakingDelta(BlockState state, BlockView world, BlockPos pos) {
        var playerBreakSpeed = getPlayerBlockBreakingSpeedAt(state);
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
