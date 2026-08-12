package me.matl114.utils.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import me.matl114.utils.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record BlockLocation(RegistryKey<World> world, int x, int y, int z) {
    public static BlockLocation of(Entity entity) {
        return new BlockLocation(
                entity.getEntityWorld().getRegistryKey(), entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
    }

    public static BlockLocation of(World world, BlockPos pos) {
        return new BlockLocation(world.getRegistryKey(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockLocation of(RegistryKey<World> world, BlockPos pos) {
        return new BlockLocation(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos getPos() {
        return new BlockPos(x, y, z);
    }

    public boolean isInRange(BlockLocation location, double distance) {
        if (Objects.equals(location.world, world)) {
            return MathUtils.s2(x - location.x) + MathUtils.s2(z - location.z) + MathUtils.s2(y - location.y)
                    <= MathUtils.s2(distance);
        } else {
            return false;
        }
    }

    public boolean isInRangeHorizontal(BlockLocation location, double distance) {
        if (Objects.equals(location.world, world)) {
            return MathUtils.s2(x - location.x) + MathUtils.s2(z - location.z) <= MathUtils.s2(distance);
        } else {
            return false;
        }
    }

    public boolean isLocationLoaded(World world) {
        if (Objects.equals(world.getRegistryKey(), world())) {
            return world.getChunkManager().isChunkLoaded(x >> 4, z >> 4);
        } else {
            return false;
        }
    }

    public static MapCodec<BlockLocation> DELEGATE_MAP_CODEC = RecordCodecBuilder.mapCodec(o -> o.group(
                    RegistryKey.createCodec(RegistryKeys.WORLD).fieldOf("world").forGetter(BlockLocation::world),
                    BlockPos.CODEC.fieldOf("pos").forGetter(BlockLocation::getPos))
            .apply(o, BlockLocation::of));

    public static MapCodec<BlockLocation> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    RegistryKey.createCodec(RegistryKeys.WORLD).fieldOf("world").forGetter(BlockLocation::world),
                    Codec.INT.fieldOf("x").forGetter(BlockLocation::x),
                    Codec.INT.fieldOf("y").forGetter(BlockLocation::y),
                    Codec.INT.fieldOf("z").forGetter(BlockLocation::z))
            .apply(instance, BlockLocation::new));

    public static Codec<BlockLocation> CODEC = Codec.withAlternative(MAP_CODEC.codec(), DELEGATE_MAP_CODEC.codec());
}
