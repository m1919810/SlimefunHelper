package me.matl114.utils.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import me.matl114.utils.MathUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public record ContainerPosition(RegistryKey<World> world, int doubleX, int y, int doubleZ) {
    public static final Codec<ContainerPosition> CODEC = RecordCodecBuilder.create(obj -> obj.group(
                    RegistryKey.createCodec(RegistryKeys.WORLD).fieldOf("world").forGetter(ContainerPosition::world),
                    Codec.INT.fieldOf("double-x").forGetter(ContainerPosition::doubleX),
                    Codec.INT.fieldOf("y").forGetter(ContainerPosition::y),
                    Codec.INT.fieldOf("double-z").forGetter(ContainerPosition::doubleZ))
            .apply(obj, ContainerPosition::new));

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

    public static ContainerPosition ofDouble(World world, BlockPos pos1, BlockPos pos2) {
        return new ContainerPosition(
                world.getRegistryKey(), pos1.getX() + pos2.getX(), pos1.getY(), pos1.getZ() + pos2.getZ());
    }

    public static ContainerPosition resolve(World world, BlockPos pos) {
        if (world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                return resolveDoubleChest(world, pos, state);
            } else {
                return ContainerPosition.ofSingle(world, pos);
            }
        } else {
            return ContainerPosition.ofSingle(world, pos);
        }
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

    public boolean contains(BlockPos pos) {
        // 两个箱子的 y 坐标相同，不匹配直接返回 false
        if (pos.getY() != y) {
            return false;
        }

        int x1 = doubleX >> 1;
        int z1 = doubleZ >> 1;
        // 利用数学关系：x1 + x2 = doubleX，z1 + z2 = doubleZ
        int x2 = doubleX - x1;
        int z2 = doubleZ - z1;

        int px = pos.getX();
        int pz = pos.getZ();

        return (px == x1 && pz == z1) || (px == x2 && pz == z2);
    }

    public ChunkPos getChunk() {
        return new ChunkPos(doubleX >> 5, doubleZ >> 5);
    }
}
