package me.matl114.utils.world;

import java.util.Objects;
import me.matl114.utils.MathUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record BlockLocation(RegistryKey<World> world, int x, int y, int z) {
    public static BlockLocation of(Entity entity) {
        return new BlockLocation(
                entity.getEntityWorld().getRegistryKey(), entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
    }

    public static BlockLocation of(ClientWorld world, BlockPos pos) {
        return new BlockLocation(world.getRegistryKey(), pos.getX(), pos.getY(), pos.getZ());
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
}
