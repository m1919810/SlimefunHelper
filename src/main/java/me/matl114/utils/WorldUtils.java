package me.matl114.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.attribute.EntityAttributeInstance;
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
                .map(GameProfile::getName);
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
            optionalUid = entry.getProfile().getId().toString();
        } else {
            optionalUid = null;
        }
        return getWaypoints()
                .filter(s -> lookup.equalsIgnoreCase(s.getDisplayName())
                        || (optionalUid != null && optionalUid.equalsIgnoreCase(s.getDisplayName())))
                .findFirst()
                .orElse(null);
    }

    public static float getPlayerBlockBreakingSpeedWithCanMineMultiply(
            PlayerEntity player, BlockState state, ItemStack stack) {
        float f = stack.getMiningSpeedMultiplier(state);
        if (f > 1.0F) {
            AttributeContainer attributeContainer = new AttributeContainer(
                    DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) player.getType()));
            attributeContainer.setFrom(player.getAttributes());
            stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (holder, attr) -> {
                EntityAttributeInstance instance = attributeContainer.getCustomInstance(holder);
                if (instance != null) {
                    instance.removeModifier(attr.id());
                    instance.addTemporaryModifier(attr);
                }
            });
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
