package me.matl114.utils.world;

import java.util.Objects;
import me.matl114.utils.MathUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record ContainerPosition(RegistryKey<World> world, int doubleX, int y, int doubleZ) {

    public Vec3d getCenterPosition() {
        return new Vec3d((doubleX + 1) / 2.0F, y + 0.5, (doubleZ + 1) / 2.0F);
    }

    public Box getBoundingBox() {
        BlockLocation first = getFirst();
        BlockLocation second = getSecond();
        return new Box(
                Math.min(first.x(), second.x()), // minX
                y, // minY
                Math.min(first.z(), second.z()), // minZ
                Math.max(first.x(), second.x()) + 1.0, // maxX (第二个方块的右边界)
                y + 1.0, // maxY (方块顶部)
                Math.max(first.z(), second.z()) + 1.0 // maxZ (第二个方块的前边界)
                );
    }

    public boolean isInRenderRange(BlockLocation location, double distance) {
        if (Objects.equals(location.world(), world)) {
            return MathUtils.s2(doubleX - 2 * location.x()) + MathUtils.s2(doubleZ - 2 * location.z())
                    <= MathUtils.s2(distance) * 4;
        } else {
            return false;
        }
    }

    public static ContainerPosition ofPosition(BlockLocation location) {
        return new ContainerPosition(location.world(), 2 * location.x(), location.y(), 2 * location.z());
    }

    public static ContainerPosition ofSingle(World world, BlockPos pos) {
        return new ContainerPosition(world.getRegistryKey(), 2 * pos.getX(), pos.getY(), 2 * pos.getZ());
    }

    public static ContainerPosition resolveDoubleChest(World world, BlockPos pos, BlockState state) {
        Direction direction = ChestBlock.getFacing(state);
        return new ContainerPosition(
                world.getRegistryKey(),
                pos.getX() * 2 + direction.getOffsetX(),
                pos.getY(),
                pos.getZ() * 2 + direction.getOffsetZ());
    }

    public boolean isDouble() {
        return (doubleX & 1) != 0 || (doubleZ & 1) != 0;
    }

    public BlockLocation getFirst() {
        return new BlockLocation(world, doubleX >> 1, y, doubleZ >> 1);
    }

    public BlockLocation getSecond() {
        return new BlockLocation(world, doubleX - (doubleX >> 1), y, doubleZ - (doubleZ >> 1));
    }
}
