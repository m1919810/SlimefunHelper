package me.matl114.utils;

import static me.matl114.hacks.RenderTasks.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import me.matl114.accessors.moonrise.MoonriseBlockStateBaseAccess;
import me.matl114.accessors.moonrise.MoonriseChunkBlockCountingAccess;
import me.matl114.accessors.moonrise.MoonriseVoxelShapeAccess;
import me.matl114.hacks.RenderTasks;
import me.matl114.utils.world.CachedShapeData;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;

public final class CollisionUtil {

    public static final double COLLISION_EPSILON = 1.0E-7;
    public static final it.unimi.dsi.fastutil.doubles.DoubleArrayList ZERO_ONE =
            it.unimi.dsi.fastutil.doubles.DoubleArrayList.wrap(new double[] {0.0, 1.0});

    public static boolean isSpecialCollidingBlock(final AbstractBlock.AbstractBlockState block) {
        return block.exceedsCube() || block.getBlock() == net.minecraft.block.Blocks.MOVING_PISTON;
    }

    public static boolean isEmpty(final net.minecraft.util.math.Box aabb) {
        return (aabb.maxX - aabb.minX) < COLLISION_EPSILON
                || (aabb.maxY - aabb.minY) < COLLISION_EPSILON
                || (aabb.maxZ - aabb.minZ) < COLLISION_EPSILON;
    }

    public static boolean isEmpty(
            final double minX,
            final double minY,
            final double minZ,
            final double maxX,
            final double maxY,
            final double maxZ) {
        return (maxX - minX) < COLLISION_EPSILON
                || (maxY - minY) < COLLISION_EPSILON
                || (maxZ - minZ) < COLLISION_EPSILON;
    }

    public static net.minecraft.util.math.Box getBoxForChunk(final int chunkX, final int chunkZ) {
        double x = (double) (chunkX << 4);
        double z = (double) (chunkZ << 4);
        // use a bounding box bigger than the chunk to prevent entities from entering it on move
        return new net.minecraft.util.math.Box(
                x - 3 * COLLISION_EPSILON,
                Double.NEGATIVE_INFINITY,
                z - 3 * COLLISION_EPSILON,
                x + (16.0 + 3 * COLLISION_EPSILON),
                Double.POSITIVE_INFINITY,
                z + (16.0 + 3 * COLLISION_EPSILON));
    }

    /*
     A couple of rules for VoxelShape collisions:
     Two shapes only intersect if they are actually more than EPSILON units into each other. This also applies to movement
     checks.
     If the two shapes strictly collide, then the return value of a collide call will return a value in the opposite
     direction of the source move. However, this value will not be greater in magnitude than EPSILON. Collision code
     will automatically round it to 0.
    */

    public static boolean voxelShapeIntersectHorizontal(
            final net.minecraft.util.math.Box box1, final net.minecraft.util.math.Box box2) {
        // remove y check
        return (box1.minX - box2.maxX) < -COLLISION_EPSILON
                && (box1.maxX - box2.minX) > COLLISION_EPSILON
                &&
                //            (box1.minY - box2.maxY) < -COLLISION_EPSILON && (box1.maxY - box2.minY) >
                // COLLISION_EPSILON &&
                (box1.minZ - box2.maxZ) < -COLLISION_EPSILON
                && (box1.maxZ - box2.minZ) > COLLISION_EPSILON;
    }

    public static boolean voxelShapeIntersect(
            final double minX1,
            final double minY1,
            final double minZ1,
            final double maxX1,
            final double maxY1,
            final double maxZ1,
            final double minX2,
            final double minY2,
            final double minZ2,
            final double maxX2,
            final double maxY2,
            final double maxZ2) {
        return (minX1 - maxX2) < -COLLISION_EPSILON
                && (maxX1 - minX2) > COLLISION_EPSILON
                && (minY1 - maxY2) < -COLLISION_EPSILON
                && (maxY1 - minY2) > COLLISION_EPSILON
                && (minZ1 - maxZ2) < -COLLISION_EPSILON
                && (maxZ1 - minZ2) > COLLISION_EPSILON;
    }

    public static boolean voxelShapeIntersect(
            final net.minecraft.util.math.Box box,
            final double minX,
            final double minY,
            final double minZ,
            final double maxX,
            final double maxY,
            final double maxZ) {
        return (box.minX - maxX) < -COLLISION_EPSILON
                && (box.maxX - minX) > COLLISION_EPSILON
                && (box.minY - maxY) < -COLLISION_EPSILON
                && (box.maxY - minY) > COLLISION_EPSILON
                && (box.minZ - maxZ) < -COLLISION_EPSILON
                && (box.maxZ - minZ) > COLLISION_EPSILON;
    }

    public static boolean voxelShapeIntersect(
            final net.minecraft.util.math.Box box1, final net.minecraft.util.math.Box box2) {
        return (box1.minX - box2.maxX) < -COLLISION_EPSILON
                && (box1.maxX - box2.minX) > COLLISION_EPSILON
                && (box1.minY - box2.maxY) < -COLLISION_EPSILON
                && (box1.maxY - box2.minY) > COLLISION_EPSILON
                && (box1.minZ - box2.maxZ) < -COLLISION_EPSILON
                && (box1.maxZ - box2.minZ) > COLLISION_EPSILON;
    }

    // assume !isEmpty(target) && abs(source_move) >= COLLISION_EPSILON
    public static double collideX(
            final net.minecraft.util.math.Box target,
            final net.minecraft.util.math.Box source,
            final double source_move) {
        if ((source.minY - target.maxY) < -COLLISION_EPSILON
                && (source.maxY - target.minY) > COLLISION_EPSILON
                && (source.minZ - target.maxZ) < -COLLISION_EPSILON
                && (source.maxZ - target.minZ) > COLLISION_EPSILON) {
            if (source_move >= 0.0) {
                final double max_move = target.minX - source.maxX; // < 0.0 if no strict collision
                if (max_move < -COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.min(max_move, source_move);
            } else {
                final double max_move = target.maxX - source.minX; // > 0.0 if no strict collision
                if (max_move > COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.max(max_move, source_move);
            }
        }
        return source_move;
    }

    // assume !isEmpty(target) && abs(source_move) >= COLLISION_EPSILON
    public static double collideY(
            final net.minecraft.util.math.Box target,
            final net.minecraft.util.math.Box source,
            final double source_move) {
        if ((source.minX - target.maxX) < -COLLISION_EPSILON
                && (source.maxX - target.minX) > COLLISION_EPSILON
                && (source.minZ - target.maxZ) < -COLLISION_EPSILON
                && (source.maxZ - target.minZ) > COLLISION_EPSILON) {
            if (source_move >= 0.0) {
                final double max_move = target.minY - source.maxY; // < 0.0 if no strict collision
                if (max_move < -COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.min(max_move, source_move);
            } else {
                final double max_move = target.maxY - source.minY; // > 0.0 if no strict collision
                if (max_move > COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.max(max_move, source_move);
            }
        }
        return source_move;
    }

    // assume !isEmpty(target) && abs(source_move) >= COLLISION_EPSILON
    public static double collideZ(
            final net.minecraft.util.math.Box target,
            final net.minecraft.util.math.Box source,
            final double source_move) {
        if ((source.minX - target.maxX) < -COLLISION_EPSILON
                && (source.maxX - target.minX) > COLLISION_EPSILON
                && (source.minY - target.maxY) < -COLLISION_EPSILON
                && (source.maxY - target.minY) > COLLISION_EPSILON) {
            if (source_move >= 0.0) {
                final double max_move = target.minZ - source.maxZ; // < 0.0 if no strict collision
                if (max_move < -COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.min(max_move, source_move);
            } else {
                final double max_move = target.maxZ - source.minZ; // > 0.0 if no strict collision
                if (max_move > COLLISION_EPSILON) {
                    return source_move;
                }
                return Math.max(max_move, source_move);
            }
        }
        return source_move;
    }

    // startIndex and endIndex inclusive
    // assumes indices are in range of array
    private static int findFloor(final double[] values, final double value, int startIndex, int endIndex) {
        do {
            final int middle = (startIndex + endIndex) >>> 1;
            final double middleVal = values[middle];

            if (value < middleVal) {
                endIndex = middle - 1;
            } else {
                startIndex = middle + 1;
            }
        } while (startIndex <= endIndex);

        return startIndex - 1;
    }

    public static boolean voxelShapeIntersectNoEmpty(
            final net.minecraft.util.shape.VoxelShape voxel, final net.minecraft.util.math.Box aabb) {
        if (voxel.isEmpty()) {
            return false;
        }

        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = MoonriseVoxelShapeAccess.of(voxel).moonrise$offsetX();
        final double off_y = MoonriseVoxelShapeAccess.of(voxel).moonrise$offsetY();
        final double off_z = MoonriseVoxelShapeAccess.of(voxel).moonrise$offsetZ();

        final double[] coords_x = MoonriseVoxelShapeAccess.of(voxel).moonrise$rootCoordinatesX();
        final double[] coords_y = MoonriseVoxelShapeAccess.of(voxel).moonrise$rootCoordinatesY();
        final double[] coords_z = MoonriseVoxelShapeAccess.of(voxel).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data =
                MoonriseVoxelShapeAccess.of(voxel).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation

        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_x = Math.max(0, findFloor(coords_x, (aabb.minX - off_x) + COLLISION_EPSILON, 0, size_x));
        if (floor_min_x >= size_x) {
            // cannot intersect
            return false;
        }

        final int ceil_max_x =
                Math.min(size_x, findFloor(coords_x, (aabb.maxX - off_x) - COLLISION_EPSILON, floor_min_x, size_x) + 1);
        if (floor_min_x >= ceil_max_x) {
            // cannot intersect
            return false;
        }

        final int floor_min_y = Math.max(0, findFloor(coords_y, (aabb.minY - off_y) + COLLISION_EPSILON, 0, size_y));
        if (floor_min_y >= size_y) {
            // cannot intersect
            return false;
        }

        final int ceil_max_y =
                Math.min(size_y, findFloor(coords_y, (aabb.maxY - off_y) - COLLISION_EPSILON, floor_min_y, size_y) + 1);
        if (floor_min_y >= ceil_max_y) {
            // cannot intersect
            return false;
        }

        final int floor_min_z = Math.max(0, findFloor(coords_z, (aabb.minZ - off_z) + COLLISION_EPSILON, 0, size_z));
        if (floor_min_z >= size_z) {
            // cannot intersect
            return false;
        }

        final int ceil_max_z =
                Math.min(size_z, findFloor(coords_z, (aabb.maxZ - off_z) - COLLISION_EPSILON, floor_min_z, size_z) + 1);
        if (floor_min_z >= ceil_max_z) {
            // cannot intersect
            return false;
        }

        final long[] bitset = cached_shape_data.voxelSet();

        // check bitset to check if any shapes in range are full

        final int mul_x = size_y * size_z;
        for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
            for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                    final int index = curr_z + curr_y * size_z + curr_x * mul_x;
                    // note: JLS states long shift operators ANDS shift by 63
                    if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // assume !target.isEmpty() && abs(source_move) >= COLLISION_EPSILON
    public static double collideX(
            final net.minecraft.util.shape.VoxelShape target,
            final net.minecraft.util.math.Box source,
            final double source_move) {
        final net.minecraft.util.math.Box single_aabb =
                MoonriseVoxelShapeAccess.of(target).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return collideX(single_aabb, source, source_move);
        }
        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = MoonriseVoxelShapeAccess.of(target).moonrise$offsetX();
        final double off_y = MoonriseVoxelShapeAccess.of(target).moonrise$offsetY();
        final double off_z = MoonriseVoxelShapeAccess.of(target).moonrise$offsetZ();

        final double[] coords_x = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesX();
        final double[] coords_y = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesY();
        final double[] coords_z = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data =
                MoonriseVoxelShapeAccess.of(target).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation

        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_y = Math.max(0, findFloor(coords_y, (source.minY - off_y) + COLLISION_EPSILON, 0, size_y));
        if (floor_min_y >= size_y) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_y = Math.min(
                size_y, findFloor(coords_y, (source.maxY - off_y) - COLLISION_EPSILON, floor_min_y, size_y) + 1);
        if (floor_min_y >= ceil_max_y) {
            // cannot intersect
            return source_move;
        }

        final int floor_min_z = Math.max(0, findFloor(coords_z, (source.minZ - off_z) + COLLISION_EPSILON, 0, size_z));
        if (floor_min_z >= size_z) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_z = Math.min(
                size_z, findFloor(coords_z, (source.maxZ - off_z) - COLLISION_EPSILON, floor_min_z, size_z) + 1);
        if (floor_min_z >= ceil_max_z) {
            // cannot intersect
            return source_move;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final long[] bitset = cached_shape_data.voxelSet();

        if (source_move > 0.0) {
            final double source_max = source.maxX - off_x;
            final int ceil_max_x = findFloor(coords_x, source_max - COLLISION_EPSILON, 0, size_x)
                    + 1; // add one, we are not interested in (coords[i] + COLLISION_EPSILON) < max

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index size on the collision axis for forward movement

            final int mul_x = size_y * size_z;
            for (int curr_x = ceil_max_x; curr_x < size_x; ++curr_x) {
                double max_dist = coords_x[curr_x] - source_max;
                if (max_dist >= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max < source_move, as coords[curr + n] < coords[curr + n + 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist >= -COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.min(max_dist, source_move);
                }
                for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y * size_z + curr_x * mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        } else {
            final double source_min = source.minX - off_x;
            final int floor_min_x = findFloor(coords_x, source_min + COLLISION_EPSILON, 0, size_x);

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index 0 on the collision axis for backwards movement

            // note: we offset the collision axis by - 1 for the voxel bitset index, but use + 1 for the
            //       coordinate index as the voxelset stores whether the shape is solid for [index, index + 1]
            //       thus, we need to use the voxel index i-1 if we want to check that the face at index i is solid
            final int mul_x = size_y * size_z;
            for (int curr_x = floor_min_x - 1; curr_x >= 0; --curr_x) {
                double max_dist = coords_x[curr_x + 1] - source_min;
                if (max_dist <= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max > source_move, as coords[curr + n] > coords[curr + n - 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is possibly bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist <= COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.max(max_dist, source_move);
                }
                for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y * size_z + curr_x * mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        }
    }

    public static double collideY(
            final net.minecraft.util.shape.VoxelShape target,
            final net.minecraft.util.math.Box source,
            final double source_move) {
        final net.minecraft.util.math.Box single_aabb =
                MoonriseVoxelShapeAccess.of(target).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return collideY(single_aabb, source, source_move);
        }
        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = MoonriseVoxelShapeAccess.of(target).moonrise$offsetX();
        final double off_y = MoonriseVoxelShapeAccess.of(target).moonrise$offsetY();
        final double off_z = MoonriseVoxelShapeAccess.of(target).moonrise$offsetZ();

        final double[] coords_x = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesX();
        final double[] coords_y = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesY();
        final double[] coords_z = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data =
                MoonriseVoxelShapeAccess.of(target).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation

        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_x = Math.max(0, findFloor(coords_x, (source.minX - off_x) + COLLISION_EPSILON, 0, size_x));
        if (floor_min_x >= size_x) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_x = Math.min(
                size_x, findFloor(coords_x, (source.maxX - off_x) - COLLISION_EPSILON, floor_min_x, size_x) + 1);
        if (floor_min_x >= ceil_max_x) {
            // cannot intersect
            return source_move;
        }

        final int floor_min_z = Math.max(0, findFloor(coords_z, (source.minZ - off_z) + COLLISION_EPSILON, 0, size_z));
        if (floor_min_z >= size_z) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_z = Math.min(
                size_z, findFloor(coords_z, (source.maxZ - off_z) - COLLISION_EPSILON, floor_min_z, size_z) + 1);
        if (floor_min_z >= ceil_max_z) {
            // cannot intersect
            return source_move;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final long[] bitset = cached_shape_data.voxelSet();

        if (source_move > 0.0) {
            final double source_max = source.maxY - off_y;
            final int ceil_max_y = findFloor(coords_y, source_max - COLLISION_EPSILON, 0, size_y)
                    + 1; // add one, we are not interested in (coords[i] + COLLISION_EPSILON) < max

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index size on the collision axis for forward movement

            final int mul_x = size_y * size_z;
            for (int curr_y = ceil_max_y; curr_y < size_y; ++curr_y) {
                double max_dist = coords_y[curr_y] - source_max;
                if (max_dist >= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max < source_move, as coords[curr + n] < coords[curr + n + 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist >= -COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.min(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y * size_z + curr_x * mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        } else {
            final double source_min = source.minY - off_y;
            final int floor_min_y = findFloor(coords_y, source_min + COLLISION_EPSILON, 0, size_y);

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index 0 on the collision axis for backwards movement

            // note: we offset the collision axis by - 1 for the voxel bitset index, but use + 1 for the
            //       coordinate index as the voxelset stores whether the shape is solid for [index, index + 1]
            //       thus, we need to use the voxel index i-1 if we want to check that the face at index i is solid
            final int mul_x = size_y * size_z;
            for (int curr_y = floor_min_y - 1; curr_y >= 0; --curr_y) {
                double max_dist = coords_y[curr_y + 1] - source_min;
                if (max_dist <= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max > source_move, as coords[curr + n] > coords[curr + n - 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is possibly bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist <= COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.max(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_z = floor_min_z; curr_z < ceil_max_z; ++curr_z) {
                        final int index = curr_z + curr_y * size_z + curr_x * mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        }
    }

    public static double collideZ(
            final net.minecraft.util.shape.VoxelShape target,
            final net.minecraft.util.math.Box source,
            final double source_move) {
        final net.minecraft.util.math.Box single_aabb =
                MoonriseVoxelShapeAccess.of(target).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return collideZ(single_aabb, source, source_move);
        }
        // note: this function assumes that for any i in coords that coord[i + 1] - coord[i] > COLLISION_EPSILON is true

        // offsets that should be applied to coords
        final double off_x = MoonriseVoxelShapeAccess.of(target).moonrise$offsetX();
        final double off_y = MoonriseVoxelShapeAccess.of(target).moonrise$offsetY();
        final double off_z = MoonriseVoxelShapeAccess.of(target).moonrise$offsetZ();

        final double[] coords_x = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesX();
        final double[] coords_y = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesY();
        final double[] coords_z = MoonriseVoxelShapeAccess.of(target).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data =
                MoonriseVoxelShapeAccess.of(target).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: voxel bitset with set index (x, y, z) indicates that
        //       an AABB(coords_x[x], coords_y[y], coords_z[z], coords_x[x + 1], coords_y[y + 1], coords_z[z + 1])
        //       is collidable. this is the fundamental principle of operation for the voxel collision operation

        // note: we should be offsetting coords, but we can also just subtract from source as well - which is
        //       a win in terms of ops / simplicity (see findFloor, allows us to not modify coords for that)
        // note: for intersection, one we find the floor of the min we can use that as the start index
        //       for the next check as source max >= source min
        // note: we can fast check intersection on the two other axis by seeing if the min index is >= size,
        //       as this implies that coords[coords.length - 1] < source min
        //       we can also fast check by seeing if max index is < 0, as this implies that coords[0] > source max

        final int floor_min_x = Math.max(0, findFloor(coords_x, (source.minX - off_x) + COLLISION_EPSILON, 0, size_x));
        if (floor_min_x >= size_x) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_x = Math.min(
                size_x, findFloor(coords_x, (source.maxX - off_x) - COLLISION_EPSILON, floor_min_x, size_x) + 1);
        if (floor_min_x >= ceil_max_x) {
            // cannot intersect
            return source_move;
        }

        final int floor_min_y = Math.max(0, findFloor(coords_y, (source.minY - off_y) + COLLISION_EPSILON, 0, size_y));
        if (floor_min_y >= size_y) {
            // cannot intersect
            return source_move;
        }

        final int ceil_max_y = Math.min(
                size_y, findFloor(coords_y, (source.maxY - off_y) - COLLISION_EPSILON, floor_min_y, size_y) + 1);
        if (floor_min_y >= ceil_max_y) {
            // cannot intersect
            return source_move;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final long[] bitset = cached_shape_data.voxelSet();

        if (source_move > 0.0) {
            final double source_max = source.maxZ - off_z;
            final int ceil_max_z = findFloor(coords_z, source_max - COLLISION_EPSILON, 0, size_z)
                    + 1; // add one, we are not interested in (coords[i] + COLLISION_EPSILON) < max

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index size on the collision axis for forward movement

            final int mul_x = size_y * size_z;
            for (int curr_z = ceil_max_z; curr_z < size_z; ++curr_z) {
                double max_dist = coords_z[curr_z] - source_max;
                if (max_dist >= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max < source_move, as coords[curr + n] < coords[curr + n + 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist >= -COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.min(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                        final int index = curr_z + curr_y * size_z + curr_x * mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        } else {
            final double source_min = source.minZ - off_z;
            final int floor_min_z = findFloor(coords_z, source_min + COLLISION_EPSILON, 0, size_z);

            // note: only the order of the first loop matters

            // note: we cannot collide with the face at index 0 on the collision axis for backwards movement

            // note: we offset the collision axis by - 1 for the voxel bitset index, but use + 1 for the
            //       coordinate index as the voxelset stores whether the shape is solid for [index, index + 1]
            //       thus, we need to use the voxel index i-1 if we want to check that the face at index i is solid
            final int mul_x = size_y * size_z;
            for (int curr_z = floor_min_z - 1; curr_z >= 0; --curr_z) {
                double max_dist = coords_z[curr_z + 1] - source_min;
                if (max_dist <= source_move) {
                    // if we reach here, then we will never have a case where
                    // coords[curr + n] - source_max > source_move, as coords[curr + n] > coords[curr + n - 1]
                    // thus, we can return immediately

                    // this optimization is important since this loop is possibly bounded by size, and _not_ by
                    // a calculated max index based off of source_move - so it would be possible to check
                    // the whole intersected shape for collisions when we didn't need to!
                    return source_move;
                }
                if (max_dist <= COLLISION_EPSILON) { // only push out by up to COLLISION_EPSILON
                    max_dist = Math.max(max_dist, source_move);
                }
                for (int curr_x = floor_min_x; curr_x < ceil_max_x; ++curr_x) {
                    for (int curr_y = floor_min_y; curr_y < ceil_max_y; ++curr_y) {
                        final int index = curr_z + curr_y * size_z + curr_x * mul_x;
                        // note: JLS states long shift operators ANDS shift by 63
                        if ((bitset[index >>> 6] & (1L << index)) != 0L) {
                            return max_dist;
                        }
                    }
                }
            }

            return source_move;
        }
    }

    // does not use epsilon
    public static boolean strictlyContains(
            final net.minecraft.util.shape.VoxelShape voxel, final net.minecraft.util.math.Vec3d point) {
        return strictlyContains(voxel, point.x, point.y, point.z);
    }

    // does not use epsilon
    public static boolean strictlyContains(
            final net.minecraft.util.shape.VoxelShape voxel, double x, double y, double z) {
        final net.minecraft.util.math.Box single_aabb =
                MoonriseVoxelShapeAccess.of(voxel).moonrise$getSingleAABBRepresentation();
        if (single_aabb != null) {
            return single_aabb.contains(x, y, z);
        }

        if (voxel.isEmpty()) {
            // bitset is clear, no point in searching
            return false;
        }

        // offset input
        x -= MoonriseVoxelShapeAccess.of(voxel).moonrise$offsetX();
        y -= MoonriseVoxelShapeAccess.of(voxel).moonrise$offsetY();
        z -= MoonriseVoxelShapeAccess.of(voxel).moonrise$offsetZ();

        final double[] coords_x = MoonriseVoxelShapeAccess.of(voxel).moonrise$rootCoordinatesX();
        final double[] coords_y = MoonriseVoxelShapeAccess.of(voxel).moonrise$rootCoordinatesY();
        final double[] coords_z = MoonriseVoxelShapeAccess.of(voxel).moonrise$rootCoordinatesZ();

        final CachedShapeData cached_shape_data =
                MoonriseVoxelShapeAccess.of(voxel).moonrise$getCachedVoxelData();

        // note: size = coords.length - 1
        final int size_x = cached_shape_data.sizeX();
        final int size_y = cached_shape_data.sizeY();
        final int size_z = cached_shape_data.sizeZ();

        // note: should mirror AABB#contains, which is that for any point X that X >= min and X < max.
        //       specifically, it cannot collide on the max bounds of the shape

        final int index_x = findFloor(coords_x, x, 0, size_x);
        if (index_x < 0 || index_x >= size_x) {
            return false;
        }

        final int index_y = findFloor(coords_y, y, 0, size_y);
        if (index_y < 0 || index_y >= size_y) {
            return false;
        }

        final int index_z = findFloor(coords_z, z, 0, size_z);
        if (index_z < 0 || index_z >= size_z) {
            return false;
        }

        // index = z + y*size_z + x*(size_z*size_y)

        final int index = index_z + index_y * size_z + index_x * (size_z * size_y);

        final long[] bitset = cached_shape_data.voxelSet();

        return (bitset[index >>> 6] & (1L << index)) != 0L;
    }

    private static int makeBitset(final boolean ft, final boolean tf, final boolean tt) {
        // idx ff -> 0
        // idx ft -> 1
        // idx tf -> 2
        // idx tt -> 3
        return ((ft ? 1 : 0) << 1) | ((tf ? 1 : 0) << 2) | ((tt ? 1 : 0) << 3);
    }

    //    private static net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape merge(final
    // ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeDataFirst, final
    // ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeDataSecond,
    //                                                                                  final
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedX, final
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedY,
    //                                                                                  final
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedZ,
    //                                                                                  final int booleanOp) {
    //        final int sizeX = mergedX.voxels;
    //        final int sizeY = mergedY.voxels;
    //        final int sizeZ = mergedZ.voxels;
    //
    //        final long[] s1Voxels = shapeDataFirst.voxelSet();
    //        final long[] s2Voxels = shapeDataSecond.voxelSet();
    //
    //        final int s1Mul1 = shapeDataFirst.sizeZ();
    //        final int s1Mul2 = s1Mul1 * shapeDataFirst.sizeY();
    //
    //        final int s2Mul1 = shapeDataSecond.sizeZ();
    //        final int s2Mul2 = s2Mul1 * shapeDataSecond.sizeY();
    //
    //        // note: indices may contain -1, but nothing > size
    //        final net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape ret = new
    // net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(sizeX, sizeY, sizeZ);
    //
    //        boolean empty = true;
    //
    //        int mergedIdx = 0;
    //        for (int idxX = 0; idxX < sizeX; ++idxX) {
    //            final int s1x = mergedX.firstIndices[idxX];
    //            final int s2x = mergedX.secondIndices[idxX];
    //            boolean setX = false;
    //            for (int idxY = 0; idxY < sizeY; ++idxY) {
    //                final int s1y = mergedY.firstIndices[idxY];
    //                final int s2y = mergedY.secondIndices[idxY];
    //                boolean setY = false;
    //                for (int idxZ = 0; idxZ < sizeZ; ++idxZ) {
    //                    final int s1z = mergedZ.firstIndices[idxZ];
    //                    final int s2z = mergedZ.secondIndices[idxZ];
    //
    //                    int idx;
    //
    //                    final int isS1Full = (s1x | s1y | s1z) < 0 ? 0 : (int)((s1Voxels[(idx = s1z + s1y*s1Mul1 +
    // s1x*s1Mul2) >>> 6] >>> idx) & 1L);
    //                    final int isS2Full = (s2x | s2y | s2z) < 0 ? 0 : (int)((s2Voxels[(idx = s2z + s2y*s2Mul1 +
    // s2x*s2Mul2) >>> 6] >>> idx) & 1L);
    //
    //                    // idx ff -> 0
    //                    // idx ft -> 1
    //                    // idx tf -> 2
    //                    // idx tt -> 3
    //
    //                    final boolean res = (booleanOp & (1 << (isS2Full | (isS1Full << 1)))) != 0;
    //                    setY |= res;
    //                    setX |= res;
    //
    //                    if (res) {
    //                        empty = false;
    //                        // inline and optimize fill operation
    //                        ret.zMin = Math.min(ret.zMin, idxZ);
    //                        ret.zMax = Math.max(ret.zMax, idxZ + 1);
    //                        ret.storage.set(mergedIdx);
    //                    }
    //
    //                    ++mergedIdx;
    //                }
    //                if (setY) {
    //                    ret.yMin = Math.min(ret.yMin, idxY);
    //                    ret.yMax = Math.max(ret.yMax, idxY + 1);
    //                }
    //            }
    //            if (setX) {
    //                ret.xMin = Math.min(ret.xMin, idxX);
    //                ret.xMax = Math.max(ret.xMax, idxX + 1);
    //            }
    //        }
    //
    //        return empty ? null : ret;
    //    }
    //
    //    private static boolean isMergeEmpty(final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData
    // shapeDataFirst, final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeDataSecond,
    //                                        final
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedX, final
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedY,
    //                                        final
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedZ,
    //                                        final int booleanOp) {
    //        final int sizeX = mergedX.voxels;
    //        final int sizeY = mergedY.voxels;
    //        final int sizeZ = mergedZ.voxels;
    //
    //        final long[] s1Voxels = shapeDataFirst.voxelSet();
    //        final long[] s2Voxels = shapeDataSecond.voxelSet();
    //
    //        final int s1Mul1 = shapeDataFirst.sizeZ();
    //        final int s1Mul2 = s1Mul1 * shapeDataFirst.sizeY();
    //
    //        final int s2Mul1 = shapeDataSecond.sizeZ();
    //        final int s2Mul2 = s2Mul1 * shapeDataSecond.sizeY();
    //
    //        // note: indices may contain -1, but nothing > size
    //        for (int idxX = 0; idxX < sizeX; ++idxX) {
    //            final int s1x = mergedX.firstIndices[idxX];
    //            final int s2x = mergedX.secondIndices[idxX];
    //            for (int idxY = 0; idxY < sizeY; ++idxY) {
    //                final int s1y = mergedY.firstIndices[idxY];
    //                final int s2y = mergedY.secondIndices[idxY];
    //                for (int idxZ = 0; idxZ < sizeZ; ++idxZ) {
    //                    final int s1z = mergedZ.firstIndices[idxZ];
    //                    final int s2z = mergedZ.secondIndices[idxZ];
    //
    //                    int idx;
    //
    //                    final int isS1Full = (s1x | s1y | s1z) < 0 ? 0 : (int)((s1Voxels[(idx = s1z + s1y*s1Mul1 +
    // s1x*s1Mul2) >>> 6] >>> idx) & 1L);
    //                    final int isS2Full = (s2x | s2y | s2z) < 0 ? 0 : (int)((s2Voxels[(idx = s2z + s2y*s2Mul1 +
    // s2x*s2Mul2) >>> 6] >>> idx) & 1L);
    //
    //                    // idx ff -> 0
    //                    // idx ft -> 1
    //                    // idx tf -> 2
    //                    // idx tt -> 3
    //
    //                    final boolean res = (booleanOp & (1 << (isS2Full | (isS1Full << 1)))) != 0;
    //
    //                    if (res) {
    //                        return false;
    //                    }
    //                }
    //            }
    //        }
    //
    //        return true;
    //    }

    //    public static net.minecraft.util.shape.VoxelShape joinOptimized(final net.minecraft.util.shape.VoxelShape
    // first, final net.minecraft.util.shape.VoxelShape second, final net.minecraft.world.phys.shapes.BooleanOp
    // operator) {
    //        return joinUnoptimized(first, second, operator).optimize();
    //    }
    //
    //    public static net.minecraft.util.shape.VoxelShape joinUnoptimized(final net.minecraft.util.shape.VoxelShape
    // first, final net.minecraft.util.shape.VoxelShape second, final net.minecraft.world.phys.shapes.BooleanOp
    // operator) {
    //        final boolean ff = operator.apply(false, false);
    //        if (ff) {
    //            // technically, should be an infinite box but that's clearly an error
    //            throw new UnsupportedOperationException("Ambiguous operator: (false, false) -> true");
    //        }
    //
    //        final boolean tt = operator.apply(true, true);
    //
    //        if (first == second) {
    //            return tt ? first : net.minecraft.world.phys.shapes.Shapes.empty();
    //        }
    //
    //        final boolean ft = operator.apply(false, true);
    //        final boolean tf = operator.apply(true, false);
    //
    //        if (first.isEmpty()) {
    //            return ft ? second : net.minecraft.world.phys.shapes.Shapes.empty();
    //        }
    //        if (second.isEmpty()) {
    //            return tf ? first : net.minecraft.world.phys.shapes.Shapes.empty();
    //        }
    //
    //        if (!tt) {
    //            // try to check for no intersection, since tt = false
    //            final net.minecraft.util.math.Box aabbF =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$getSingleAABBRepresentation();
    //            final net.minecraft.util.math.Box aabbS =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$getSingleAABBRepresentation();
    //
    //            final boolean intersect;
    //
    //            final boolean hasAABBF = aabbF != null;
    //            final boolean hasAABBS = aabbS != null;
    //            if (hasAABBF | hasAABBS) {
    //                if (hasAABBF & hasAABBS) {
    //                    intersect = voxelShapeIntersect(aabbF, aabbS);
    //                } else if (hasAABBF) {
    //                    intersect = voxelShapeIntersectNoEmpty(second, aabbF);
    //                } else {
    //                    intersect = voxelShapeIntersectNoEmpty(first, aabbS);
    //                }
    //            } else {
    //                // expect cached bounds
    //                intersect = voxelShapeIntersect(first.bounds(), second.bounds());
    //            }
    //
    //            if (!intersect) {
    //                if (!tf & !ft) {
    //                    return net.minecraft.world.phys.shapes.Shapes.empty();
    //                }
    //                if (!tf | !ft) {
    //                    return tf ? first : second;
    //                }
    //            }
    //        }
    //
    //        final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedX =
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.merge(
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$rootCoordinatesX(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$offsetX(),
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$rootCoordinatesX(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$offsetX(),
    //            ft, tf
    //        );
    //        if (mergedX == ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.EMPTY) {
    //            return net.minecraft.world.phys.shapes.Shapes.empty();
    //        }
    //        final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedY =
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.merge(
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$rootCoordinatesY(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$offsetY(),
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$rootCoordinatesY(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$offsetY(),
    //            ft, tf
    //        );
    //        if (mergedY == ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.EMPTY) {
    //            return net.minecraft.world.phys.shapes.Shapes.empty();
    //        }
    //        final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedZ =
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.merge(
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$rootCoordinatesZ(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$offsetZ(),
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$rootCoordinatesZ(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$offsetZ(),
    //            ft, tf
    //        );
    //        if (mergedZ == ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.EMPTY) {
    //            return net.minecraft.world.phys.shapes.Shapes.empty();
    //        }
    //
    //        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeDataFirst =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$getCachedVoxelData();
    //        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeDataSecond =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$getCachedVoxelData();
    //
    //        final net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape mergedShape = merge(
    //            shapeDataFirst, shapeDataSecond,
    //            mergedX, mergedY, mergedZ,
    //            makeBitset(ft, tf, tt)
    //        );
    //
    //        if (mergedShape == null) {
    //            return net.minecraft.world.phys.shapes.Shapes.empty();
    //        }
    //
    //        return new net.minecraft.world.phys.shapes.ArrayVoxelShape(
    //            mergedShape, mergedX.wrapCoords(), mergedY.wrapCoords(), mergedZ.wrapCoords()
    //        );
    //    }

    //    public static boolean isJoinNonEmpty(final net.minecraft.util.shape.VoxelShape first, final
    // net.minecraft.util.shape.VoxelShape second, final net.minecraft.world.phys.shapes.BooleanOp operator) {
    //        final boolean ff = operator.apply(false, false);
    //        if (ff) {
    //            // technically, should be an infinite box but that's clearly an error
    //            throw new UnsupportedOperationException("Ambiguous operator: (false, false) -> true");
    //        }
    //        final boolean firstEmpty = first.isEmpty();
    //        final boolean secondEmpty = second.isEmpty();
    //        if (firstEmpty | secondEmpty) {
    //            return operator.apply(!firstEmpty, !secondEmpty);
    //        }
    //
    //        final boolean tt = operator.apply(true, true);
    //
    //        if (first == second) {
    //            return tt;
    //        }
    //
    //        final boolean ft = operator.apply(false, true);
    //        final boolean tf = operator.apply(true, false);
    //
    //        // try to check intersection
    //        final net.minecraft.util.math.Box aabbF =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$getSingleAABBRepresentation();
    //        final net.minecraft.util.math.Box aabbS =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$getSingleAABBRepresentation();
    //
    //        final boolean intersect;
    //
    //        final boolean hasAABBF = aabbF != null;
    //        final boolean hasAABBS = aabbS != null;
    //        if (hasAABBF | hasAABBS) {
    //            if (hasAABBF & hasAABBS) {
    //                intersect = voxelShapeIntersect(aabbF, aabbS);
    //            } else if (hasAABBF) {
    //                intersect = voxelShapeIntersectNoEmpty(second, aabbF);
    //            } else {
    //                // hasAABBS -> true
    //                intersect = voxelShapeIntersectNoEmpty(first, aabbS);
    //            }
    //
    //            if (!intersect) {
    //                // is only non-empty if we take from first or second, as there is no overlap AND both shapes are
    // non-empty
    //                return tf | ft;
    //            } else if (tt) {
    //                // intersect = true && tt = true -> non-empty merged shape
    //                return true;
    //            }
    //        } else {
    //            // expect cached bounds
    //            intersect = voxelShapeIntersect(first.bounds(), second.bounds());
    //            if (!intersect) {
    //                // is only non-empty if we take from first or second, as there is no intersection
    //                return tf | ft;
    //            }
    //        }
    //
    //        final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedX =
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.merge(
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$rootCoordinatesX(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$offsetX(),
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$rootCoordinatesX(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$offsetX(),
    //            ft, tf
    //        );
    //        if (mergedX == ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.EMPTY) {
    //            return false;
    //        }
    //        final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedY =
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.merge(
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$rootCoordinatesY(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$offsetY(),
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$rootCoordinatesY(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$offsetY(),
    //            ft, tf
    //        );
    //        if (mergedY == ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.EMPTY) {
    //            return false;
    //        }
    //        final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList mergedZ =
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.merge(
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$rootCoordinatesZ(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$offsetZ(),
    //
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$rootCoordinatesZ(),
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$offsetZ(),
    //            ft, tf
    //        );
    //        if (mergedZ == ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList.EMPTY) {
    //            return false;
    //        }
    //
    //        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeDataFirst =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$getCachedVoxelData();
    //        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeDataSecond =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$getCachedVoxelData();
    //
    //        return !isMergeEmpty(
    //            shapeDataFirst, shapeDataSecond,
    //            mergedX, mergedY, mergedZ,
    //            makeBitset(ft, tf, tt)
    //        );
    //    }

    //    private static final class MergedVoxelCoordinateList {
    //
    //        private static final int[][] SIMPLE_INDICES_CACHE = new int[64][];
    //        static {
    //            for (int i = 0; i < SIMPLE_INDICES_CACHE.length; ++i) {
    //                SIMPLE_INDICES_CACHE[i] = getIndices(i);
    //            }
    //        }
    //
    //        private static final ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList
    // EMPTY = new ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList(
    //            new double[] { 0.0 }, 0.0, new int[0], new int[0], 0
    //        );
    //
    //        private static int[] getIndices(final int length) {
    //            final int[] ret = new int[length];
    //
    //            for (int i = 1; i < length; ++i) {
    //                ret[i] = i;
    //            }
    //
    //            return ret;
    //        }
    //
    //        // indices above voxel size are always set to -1
    //        public final double[] coordinates;
    //        public final double coordinateOffset;
    //        public final int[] firstIndices;
    //        public final int[] secondIndices;
    //        public final int voxels;
    //
    //        private MergedVoxelCoordinateList(final double[] coordinates, final double coordinateOffset,
    //                                          final int[] firstIndices, final int[] secondIndices, final int voxels) {
    //            this.coordinates = coordinates;
    //            this.coordinateOffset = coordinateOffset;
    //            this.firstIndices = firstIndices;
    //            this.secondIndices = secondIndices;
    //            this.voxels = voxels;
    //        }
    //
    //        public it.unimi.dsi.fastutil.doubles.DoubleList wrapCoords() {
    //            if (this.coordinateOffset == 0.0) {
    //                return it.unimi.dsi.fastutil.doubles.DoubleArrayList.wrap(this.coordinates, this.voxels + 1);
    //            }
    //            return new
    // net.minecraft.world.phys.shapes.OffsetDoubleList(it.unimi.dsi.fastutil.doubles.DoubleArrayList.wrap(this.coordinates, this.voxels + 1), this.coordinateOffset);
    //        }
    //
    //        // assume coordinates.length > 1
    //        public static ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList
    // getForSingle(final double[] coordinates, final double offset) {
    //            final int voxels = coordinates.length - 1;
    //            final int[] indices = voxels < SIMPLE_INDICES_CACHE.length ? SIMPLE_INDICES_CACHE[voxels] :
    // getIndices(voxels);
    //
    //            return new
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList(coordinates, offset, indices,
    // indices, voxels);
    //        }
    //
    //        // assume coordinates.length > 1
    //        public static ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList
    // merge(final double[] firstCoordinates, final double firstOffset,
    //
    // final double[] secondCoordinates, final double secondOffset,
    //
    // final boolean ft, final boolean tf) {
    //            if (firstCoordinates == secondCoordinates && firstOffset == secondOffset) {
    //                return getForSingle(firstCoordinates, firstOffset);
    //            }
    //
    //            final int firstCount = firstCoordinates.length;
    //            final int secondCount = secondCoordinates.length;
    //
    //            final int voxelsFirst = firstCount - 1;
    //            final int voxelsSecond = secondCount - 1;
    //
    //            final int maxCount = firstCount + secondCount;
    //
    //            final double[] coordinates = new double[maxCount];
    //            final int[] firstIndices = new int[maxCount];
    //            final int[] secondIndices = new int[maxCount];
    //
    //            final boolean notTF = !tf;
    //            final boolean notFT = !ft;
    //
    //            int firstIndex = 0;
    //            int secondIndex = 0;
    //            int resultSize = 0;
    //
    //            // note: operations on NaN are false
    //            double last = Double.NaN;
    //
    //            for (;) {
    //                final boolean noneLeftFirst = firstIndex >= firstCount;
    //                final boolean noneLeftSecond = secondIndex >= secondCount;
    //
    //                if ((noneLeftFirst & noneLeftSecond) | (noneLeftSecond & notTF) | (noneLeftFirst & notFT)) {
    //                    break;
    //                }
    //
    //                final boolean firstZero = firstIndex == 0;
    //                final boolean secondZero = secondIndex == 0;
    //
    //                final double select;
    //
    //                if (noneLeftFirst) {
    //                    // noneLeftSecond -> false
    //                    // notFT -> false
    //                    select = secondCoordinates[secondIndex] + secondOffset;
    //                    ++secondIndex;
    //                } else if (noneLeftSecond) {
    //                    // noneLeftFirst -> false
    //                    // notTF -> false
    //                    select = firstCoordinates[firstIndex] + firstOffset;
    //                    ++firstIndex;
    //                } else {
    //                    // noneLeftFirst | noneLeftSecond -> false
    //                    // notTF -> ??
    //                    // notFT -> ??
    //                    final boolean breakFirst = notTF & secondZero;
    //                    final boolean breakSecond = notFT & firstZero;
    //
    //                    final double first = firstCoordinates[firstIndex] + firstOffset;
    //                    final double second = secondCoordinates[secondIndex] + secondOffset;
    //                    final boolean useFirst = first < (second + COLLISION_EPSILON);
    //                    final boolean cont = (useFirst & breakFirst) | (!useFirst & breakSecond);
    //
    //                    select = useFirst ? first : second;
    //                    firstIndex += useFirst ? 1 : 0;
    //                    secondIndex += 1 ^ (useFirst ? 1 : 0);
    //
    //                    if (cont) {
    //                        continue;
    //                    }
    //                }
    //
    //                int prevFirst = firstIndex - 1;
    //                prevFirst = prevFirst >= voxelsFirst ? -1 : prevFirst;
    //                int prevSecond = secondIndex - 1;
    //                prevSecond = prevSecond >= voxelsSecond ? -1 : prevSecond;
    //
    //                if (last >= (select - COLLISION_EPSILON)) {
    //                    // note: any operations on NaN is false
    //                    firstIndices[resultSize - 1] = prevFirst;
    //                    secondIndices[resultSize - 1] = prevSecond;
    //                } else {
    //                    firstIndices[resultSize] = prevFirst;
    //                    secondIndices[resultSize] = prevSecond;
    //                    coordinates[resultSize] = select;
    //
    //                    ++resultSize;
    //                    last = select;
    //                }
    //            }
    //
    //            return resultSize <= 1 ? EMPTY : new
    // ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.MergedVoxelCoordinateList(coordinates, 0.0,
    // firstIndices, secondIndices, resultSize - 1);
    //        }
    //    }

    //    public static boolean equals(final net.minecraft.world.phys.shapes.DiscreteVoxelShape shape1, final
    // net.minecraft.world.phys.shapes.DiscreteVoxelShape shape2) {
    //        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData cachedShapeData1 =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape)shape1).moonrise$getOrCreateCachedShapeData();
    //        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData cachedShapeData2 =
    // ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape)shape2).moonrise$getOrCreateCachedShapeData();
    //
    //        final boolean isEmpty1 = cachedShapeData1.isEmpty();
    //        final boolean isEmpty2 = cachedShapeData2.isEmpty();
    //
    //        if (isEmpty1 & isEmpty2) {
    //            return true;
    //        } else if (isEmpty1 ^ isEmpty2) {
    //            return false;
    //        }
    //
    //        if (cachedShapeData1.hasSingleAABB() != cachedShapeData2.hasSingleAABB()) {
    //            return false;
    //        }
    //
    //        if (cachedShapeData1.sizeX() != cachedShapeData2.sizeX()) {
    //            return false;
    //        }
    //        if (cachedShapeData1.sizeY() != cachedShapeData2.sizeY()) {
    //            return false;
    //        }
    //        if (cachedShapeData1.sizeZ() != cachedShapeData2.sizeZ()) {
    //            return false;
    //        }
    //
    //        return java.util.Arrays.equals(cachedShapeData1.voxelSet(), cachedShapeData2.voxelSet());
    //    }

    // useful only for testing
    //    public static boolean equals(final net.minecraft.util.shape.VoxelShape shape1, final
    // net.minecraft.util.shape.VoxelShape shape2) {
    //        if (!equals(shape1.shape, shape2.shape)) {
    //            return false;
    //        }
    //
    //        return
    // shape1.getCoords(net.minecraft.core.Direction.Axis.X).equals(shape2.getCoords(net.minecraft.core.Direction.Axis.X)) &&
    //
    // shape1.getCoords(net.minecraft.core.Direction.Axis.Y).equals(shape2.getCoords(net.minecraft.core.Direction.Axis.Y)) &&
    //
    // shape1.getCoords(net.minecraft.core.Direction.Axis.Z).equals(shape2.getCoords(net.minecraft.core.Direction.Axis.Z));
    //    }

    public static net.minecraft.util.math.Box offsetX(final net.minecraft.util.math.Box box, final double dx) {
        return new net.minecraft.util.math.Box(box.minX + dx, box.minY, box.minZ, box.maxX + dx, box.maxY, box.maxZ);
    }

    public static net.minecraft.util.math.Box offsetY(final net.minecraft.util.math.Box box, final double dy) {
        return new net.minecraft.util.math.Box(box.minX, box.minY + dy, box.minZ, box.maxX, box.maxY + dy, box.maxZ);
    }

    public static net.minecraft.util.math.Box offsetZ(final net.minecraft.util.math.Box box, final double dz) {
        return new net.minecraft.util.math.Box(box.minX, box.minY, box.minZ + dz, box.maxX, box.maxY, box.maxZ + dz);
    }

    public static net.minecraft.util.math.Box expandRight(
            final net.minecraft.util.math.Box box, final double dx) { // dx > 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY, box.minZ, box.maxX + dx, box.maxY, box.maxZ);
    }

    public static net.minecraft.util.math.Box expandLeft(
            final net.minecraft.util.math.Box box, final double dx) { // dx < 0.0
        return new net.minecraft.util.math.Box(box.minX - dx, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static net.minecraft.util.math.Box expandUpwards(
            final net.minecraft.util.math.Box box, final double dy) { // dy > 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY + dy, box.maxZ);
    }

    public static net.minecraft.util.math.Box expandDownwards(
            final net.minecraft.util.math.Box box, final double dy) { // dy < 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY - dy, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static net.minecraft.util.math.Box expandForwards(
            final net.minecraft.util.math.Box box, final double dz) { // dz > 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ + dz);
    }

    public static net.minecraft.util.math.Box expandBackwards(
            final net.minecraft.util.math.Box box, final double dz) { // dz < 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY, box.minZ - dz, box.maxX, box.maxY, box.maxZ);
    }

    public static net.minecraft.util.math.Box cutRight(
            final net.minecraft.util.math.Box box, final double dx) { // dx > 0.0
        return new net.minecraft.util.math.Box(box.maxX, box.minY, box.minZ, box.maxX + dx, box.maxY, box.maxZ);
    }

    public static net.minecraft.util.math.Box cutLeft(
            final net.minecraft.util.math.Box box, final double dx) { // dx < 0.0
        return new net.minecraft.util.math.Box(box.minX + dx, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
    }

    public static net.minecraft.util.math.Box cutUpwards(
            final net.minecraft.util.math.Box box, final double dy) { // dy > 0.0
        return new net.minecraft.util.math.Box(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + dy, box.maxZ);
    }

    public static net.minecraft.util.math.Box cutDownwards(
            final net.minecraft.util.math.Box box, final double dy) { // dy < 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY + dy, box.minZ, box.maxX, box.minY, box.maxZ);
    }

    public static net.minecraft.util.math.Box cutForwards(
            final net.minecraft.util.math.Box box, final double dz) { // dz > 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + dz);
    }

    public static net.minecraft.util.math.Box cutBackwards(
            final net.minecraft.util.math.Box box, final double dz) { // dz < 0.0
        return new net.minecraft.util.math.Box(box.minX, box.minY, box.minZ + dz, box.maxX, box.maxY, box.minZ);
    }

    public static net.minecraft.util.math.Box resetY(
            final net.minecraft.util.math.Box box, final double y1, final double y2) { // dy > 0.0
        return new net.minecraft.util.math.Box(box.minX, y1, box.minZ, box.maxX, y2, box.maxZ);
    }

    public static double performAABBCollisionsX(
            final net.minecraft.util.math.Box currentBoundingBox,
            double value,
            final java.util.List<net.minecraft.util.math.Box> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final net.minecraft.util.math.Box target = potentialCollisions.get(i);
            value = collideX(target, currentBoundingBox, value);
        }

        return value;
    }

    public static double performAABBCollisionsY(
            final net.minecraft.util.math.Box currentBoundingBox,
            double value,
            final java.util.List<net.minecraft.util.math.Box> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final net.minecraft.util.math.Box target = potentialCollisions.get(i);
            value = collideY(target, currentBoundingBox, value);
        }

        return value;
    }

    public static double performAABBCollisionsZ(
            final net.minecraft.util.math.Box currentBoundingBox,
            double value,
            final java.util.List<net.minecraft.util.math.Box> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final net.minecraft.util.math.Box target = potentialCollisions.get(i);
            value = collideZ(target, currentBoundingBox, value);
        }

        return value;
    }

    public static double performVoxelCollisionsX(
            final net.minecraft.util.math.Box currentBoundingBox,
            double value,
            final java.util.List<net.minecraft.util.shape.VoxelShape> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final net.minecraft.util.shape.VoxelShape target = potentialCollisions.get(i);
            value = collideX(target, currentBoundingBox, value);
        }

        return value;
    }

    public static double performVoxelCollisionsY(
            final net.minecraft.util.math.Box currentBoundingBox,
            double value,
            final java.util.List<net.minecraft.util.shape.VoxelShape> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final net.minecraft.util.shape.VoxelShape target = potentialCollisions.get(i);
            value = collideY(target, currentBoundingBox, value);
        }

        return value;
    }

    public static double performVoxelCollisionsZ(
            final net.minecraft.util.math.Box currentBoundingBox,
            double value,
            final java.util.List<net.minecraft.util.shape.VoxelShape> potentialCollisions) {
        for (int i = 0, len = potentialCollisions.size(); i < len; ++i) {
            if (Math.abs(value) < COLLISION_EPSILON) {
                return 0.0;
            }
            final net.minecraft.util.shape.VoxelShape target = potentialCollisions.get(i);
            value = collideZ(target, currentBoundingBox, value);
        }

        return value;
    }

    public static net.minecraft.util.math.Vec3d performVoxelCollisions(
            final net.minecraft.util.math.Vec3d moveVector,
            net.minecraft.util.math.Box axisalignedbb,
            final java.util.List<net.minecraft.util.shape.VoxelShape> potentialCollisions) {
        double x = moveVector.x;
        double y = moveVector.y;
        double z = moveVector.z;

        if (y != 0.0) {
            y = performVoxelCollisionsY(axisalignedbb, y, potentialCollisions);
            if (y != 0.0) {
                axisalignedbb = offsetY(axisalignedbb, y);
            }
        }

        final boolean xSmaller = Math.abs(x) < Math.abs(z);

        if (xSmaller && z != 0.0) {
            z = performVoxelCollisionsZ(axisalignedbb, z, potentialCollisions);
            if (z != 0.0) {
                axisalignedbb = offsetZ(axisalignedbb, z);
            }
        }

        if (x != 0.0) {
            x = performVoxelCollisionsX(axisalignedbb, x, potentialCollisions);
            if (!xSmaller && x != 0.0) {
                axisalignedbb = offsetX(axisalignedbb, x);
            }
        }

        if (!xSmaller && z != 0.0) {
            z = performVoxelCollisionsZ(axisalignedbb, z, potentialCollisions);
        }

        return new net.minecraft.util.math.Vec3d(x, y, z);
    }

    public static net.minecraft.util.math.Vec3d performAABBCollisions(
            final net.minecraft.util.math.Vec3d moveVector,
            net.minecraft.util.math.Box axisalignedbb,
            final java.util.List<net.minecraft.util.math.Box> potentialCollisions) {
        double x = moveVector.x;
        double y = moveVector.y;
        double z = moveVector.z;

        if (y != 0.0) {
            y = performAABBCollisionsY(axisalignedbb, y, potentialCollisions);
            debugBoxMov(axisalignedbb, new Vec3d(0, y, 0));
            if (y != 0.0) {
                axisalignedbb = offsetY(axisalignedbb, y);
            }
        }

        final boolean xSmaller = Math.abs(x) < Math.abs(z);

        if (xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, potentialCollisions);
            debugBoxMov(axisalignedbb, new Vec3d(0, 0, z));
            if (z != 0.0) {
                axisalignedbb = offsetZ(axisalignedbb, z);
            }
        }

        if (x != 0.0) {
            x = performAABBCollisionsX(axisalignedbb, x, potentialCollisions);
            debugBoxMov(axisalignedbb, new Vec3d(x, 0, 0));
            if (!xSmaller && x != 0.0) {
                axisalignedbb = offsetX(axisalignedbb, x);
            }
        }

        if (!xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, potentialCollisions);
            debugBoxMov(axisalignedbb, new Vec3d(0, 0, z));
        }

        return new net.minecraft.util.math.Vec3d(x, y, z);
    }

    public static net.minecraft.util.math.Vec3d performCollisions(
            final net.minecraft.util.math.Vec3d moveVector,
            net.minecraft.util.math.Box axisalignedbb,
            final java.util.List<net.minecraft.util.shape.VoxelShape> voxels,
            final java.util.List<net.minecraft.util.math.Box> aabbs) {
        if (voxels.isEmpty()) {
            // fast track only AABBs
            return performAABBCollisions(moveVector, axisalignedbb, aabbs);
        }

        double x = moveVector.x;
        double y = moveVector.y;
        double z = moveVector.z;

        if (y != 0.0) {
            y = performAABBCollisionsY(axisalignedbb, y, aabbs);
            y = performVoxelCollisionsY(axisalignedbb, y, voxels);
            debugBoxMov(axisalignedbb, new Vec3d(0, y, 0));
            if (y != 0.0) {
                axisalignedbb = offsetY(axisalignedbb, y);
            }
        }

        final boolean xSmaller = Math.abs(x) < Math.abs(z);

        if (xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, aabbs);
            z = performVoxelCollisionsZ(axisalignedbb, z, voxels);
            debugBoxMov(axisalignedbb, new Vec3d(0, 0, z));
            if (z != 0.0) {
                axisalignedbb = offsetZ(axisalignedbb, z);
            }
        }

        if (x != 0.0) {
            x = performAABBCollisionsX(axisalignedbb, x, aabbs);
            x = performVoxelCollisionsX(axisalignedbb, x, voxels);
            debugBoxMov(axisalignedbb, new Vec3d(x, 0, 0));
            if (!xSmaller && x != 0.0) {
                axisalignedbb = offsetX(axisalignedbb, x);
            }
        }

        if (!xSmaller && z != 0.0) {
            z = performAABBCollisionsZ(axisalignedbb, z, aabbs);
            z = performVoxelCollisionsZ(axisalignedbb, z, voxels);
            debugBoxMov(axisalignedbb, new Vec3d(0, 0, z));
        }

        return new net.minecraft.util.math.Vec3d(x, y, z);
    }

    //    public static boolean isCollidingWithBorder(final net.minecraft.world.level.border.WorldBorder worldborder,
    // final net.minecraft.util.math.Box boundingBox) {
    //        return isCollidingWithBorder(worldborder, boundingBox.minX, boundingBox.maxX, boundingBox.minZ,
    // boundingBox.maxZ);
    //    }

    //    public static boolean isCollidingWithBorder(final net.minecraft.world.level.border.WorldBorder worldborder,
    //                                                final double boxMinX, final double boxMaxX,
    //                                                final double boxMinZ, final double boxMaxZ) {
    //        final double borderMinX = Math.floor(worldborder.getMinX()); // -X
    //        final double borderMaxX = Math.ceil(worldborder.getMaxX()); // +X
    //
    //        final double borderMinZ = Math.floor(worldborder.getMinZ()); // -Z
    //        final double borderMaxZ = Math.ceil(worldborder.getMaxZ()); // +Z
    //
    //        // inverted check for world border enclosing the specified box expanded by -EPSILON
    //        return (borderMinX - boxMinX) > ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON
    // || (borderMaxX - boxMaxX) < -ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON ||
    //            (borderMinZ - boxMinZ) > ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON ||
    // (borderMaxZ - boxMaxZ) < -ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON;
    //    }

    /* Math.max/min specify that any NaN argument results in a NaN return, unlike these functions */
    private static double min(final double x, final double y) {
        return x < y ? x : y;
    }

    private static double max(final double x, final double y) {
        return x > y ? x : y;
    }

    public static final int COLLISION_FLAG_LOAD_CHUNKS = 1 << 0;
    public static final int COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS = 1 << 1;
    public static final int COLLISION_FLAG_CHECK_BORDER = 1 << 2;
    public static final int COLLISION_FLAG_CHECK_ONLY = 1 << 3;

    public static boolean getCollisionsForBlocksOrWorldBorder(
            final net.minecraft.world.World world,
            final net.minecraft.entity.Entity entity,
            final net.minecraft.util.math.Box aabb,
            final java.util.List<net.minecraft.util.shape.VoxelShape> intoVoxel,
            final java.util.List<net.minecraft.util.math.Box> intoAABB,
            final int collisionFlags,
            final java.util.function.BiPredicate<net.minecraft.block.BlockState, net.minecraft.util.math.BlockPos>
                    predicate,
            BiFunction<net.minecraft.block.BlockState, net.minecraft.util.math.BlockPos, Box> environmentFilter) {
        final boolean checkOnly = (collisionFlags & COLLISION_FLAG_CHECK_ONLY) != 0;
        boolean ret = false;

        //        if ((collisionFlags & COLLISION_FLAG_CHECK_BORDER) != 0) {
        //            final net.minecraft.world.level.border.WorldBorder worldBorder = world.getWorldBorder();
        //            if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isCollidingWithBorder(worldBorder,
        // aabb) && entity != null && worldBorder.isInsideCloseToBorder(entity, aabb)) {
        //                if (checkOnly) {
        //                    return true;
        //                } else {
        //                    final net.minecraft.util.shape.VoxelShape borderShape = worldBorder.getCollisionShape();
        //                    intoVoxel.add(borderShape);
        //                    ret = true;
        //                }
        //            }
        //        }

        final int minSection = world.getBottomSectionCoord();

        final int minBlockX = net.minecraft.util.math.MathHelper.floor(aabb.minX - COLLISION_EPSILON) - 1;
        final int maxBlockX = net.minecraft.util.math.MathHelper.floor(aabb.maxX + COLLISION_EPSILON) + 1;

        final int minBlockY = Math.max(
                (minSection << 4) - 1, net.minecraft.util.math.MathHelper.floor(aabb.minY - COLLISION_EPSILON) - 1);
        final int maxBlockY = Math.min(
                (((world.getTopSectionCoord() - 1)) << 4) + 16,
                net.minecraft.util.math.MathHelper.floor(aabb.maxY + COLLISION_EPSILON) + 1);

        final int minBlockZ = net.minecraft.util.math.MathHelper.floor(aabb.minZ - COLLISION_EPSILON) - 1;
        final int maxBlockZ = net.minecraft.util.math.MathHelper.floor(aabb.maxZ + COLLISION_EPSILON) + 1;

        final net.minecraft.util.math.BlockPos.Mutable mutablePos = new net.minecraft.util.math.BlockPos.Mutable();
        final net.minecraft.block.ShapeContext collisionShape = new LazyEntityCollisionContext(entity);

        // special cases:
        if (minBlockY > maxBlockY) {
            // no point in checking
            return ret;
        }

        final int minChunkX = minBlockX >> 4;
        final int maxChunkX = maxBlockX >> 4;

        final int minChunkY = minBlockY >> 4;
        final int maxChunkY = maxBlockY >> 4;

        final int minChunkZ = minBlockZ >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final boolean loadChunks = (collisionFlags & COLLISION_FLAG_LOAD_CHUNKS) != 0;
        final net.minecraft.world.chunk.ChunkManager chunkSource = world.getChunkManager();

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                final net.minecraft.world.chunk.Chunk chunk = chunkSource.getChunk(
                        currChunkX, currChunkZ, net.minecraft.world.chunk.ChunkStatus.FULL, loadChunks);

                if (chunk == null) {
                    if ((collisionFlags & COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS) != 0) {
                        if (checkOnly) {
                            return true;
                        } else {
                            intoAABB.add(getBoxForChunk(currChunkX, currChunkZ));
                            ret = true;
                        }
                    }
                    continue;
                }

                final net.minecraft.world.chunk.ChunkSection[] sections = chunk.getSectionArray();

                // bound y
                for (int currChunkY = minChunkY; currChunkY <= maxChunkY; ++currChunkY) {
                    final int sectionIdx = currChunkY - minSection;
                    if (sectionIdx < 0 || sectionIdx >= sections.length) {
                        continue;
                    }
                    final net.minecraft.world.chunk.ChunkSection section = sections[sectionIdx];
                    if (section == null || section.isEmpty()) {
                        // empty
                        continue;
                    }

                    final boolean hasSpecial = // false;
                            MoonriseChunkBlockCountingAccess.of(section).getSpecialCollidingBlockCount() != 0;
                    // ((ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection)section).moonrise$getSpecialCollidingBlocks() != 0;
                    final int sectionAdjust = !hasSpecial ? 1 : 0;

                    final net.minecraft.world.chunk.PalettedContainer<net.minecraft.block.BlockState> blocks =
                            section.getBlockStateContainer(); //  .states;

                    final int minXIterate = currChunkX == minChunkX ? (minBlockX & 15) + sectionAdjust : 0;
                    final int maxXIterate = currChunkX == maxChunkX ? (maxBlockX & 15) - sectionAdjust : 15;
                    final int minZIterate = currChunkZ == minChunkZ ? (minBlockZ & 15) + sectionAdjust : 0;
                    final int maxZIterate = currChunkZ == maxChunkZ ? (maxBlockZ & 15) - sectionAdjust : 15;
                    final int minYIterate = currChunkY == minChunkY ? (minBlockY & 15) + sectionAdjust : 0;
                    final int maxYIterate = currChunkY == maxChunkY ? (maxBlockY & 15) - sectionAdjust : 15;

                    for (int currY = minYIterate; currY <= maxYIterate; ++currY) {
                        final int blockY = currY | (currChunkY << 4);
                        for (int currZ = minZIterate; currZ <= maxZIterate; ++currZ) {
                            final int blockZ = currZ | (currChunkZ << 4);
                            for (int currX = minXIterate; currX <= maxXIterate; ++currX) {
                                final int localBlockIndex = (currX) | (currZ << 4) | ((currY) << 8);
                                final int blockX = currX | (currChunkX << 4);

                                final int edgeCount = hasSpecial
                                        ? ((blockX == minBlockX || blockX == maxBlockX) ? 1 : 0)
                                                + ((blockY == minBlockY || blockY == maxBlockY) ? 1 : 0)
                                                + ((blockZ == minBlockZ || blockZ == maxBlockZ) ? 1 : 0)
                                        : 0;
                                if (edgeCount == 3) {
                                    continue;
                                }

                                final net.minecraft.block.BlockState blockData = blocks.get(localBlockIndex);
                                mutablePos.set(blockX, blockY, blockZ);

                                if (environmentFilter != null) {
                                    net.minecraft.util.math.Box extraEnvironmentBox =
                                            environmentFilter.apply(blockData, mutablePos);
                                    if (extraEnvironmentBox != null) {
                                        intoAABB.add(extraEnvironmentBox);
                                    }
                                }
                                //                                if(blockData.getBlock() == Blocks.LAVA){
                                //                                    Debug.info("check lava");
                                //                                }
                                if (blockData
                                        .getBlock()
                                        .getCollisionShape(
                                                blockData,
                                                EmptyBlockView.INSTANCE,
                                                BlockPos.ORIGIN,
                                                ShapeContext.absent())
                                        .isEmpty()) {
                                    continue;
                                }
                                ;

                                net.minecraft.util.shape.VoxelShape blockCollision = MoonriseBlockStateBaseAccess.of(
                                                blockData)
                                        .moonrise$getConstantCollisionShape();

                                if (edgeCount == 0
                                        || ((edgeCount != 1 || blockData.exceedsCube())
                                                && (edgeCount != 2
                                                        || blockData.getBlock()
                                                                == net.minecraft.block.Blocks.MOVING_PISTON))) {
                                    if (blockCollision == null) {
                                        mutablePos.set(blockX, blockY, blockZ);
                                        blockCollision = blockData.getCollisionShape(world, mutablePos, collisionShape);
                                    }

                                    net.minecraft.util.math.Box singleAABB = MoonriseVoxelShapeAccess.of(blockCollision)
                                            .moonrise$getSingleAABBRepresentation();
                                    if (singleAABB != null) {
                                        singleAABB =
                                                singleAABB.offset((double) blockX, (double) blockY, (double) blockZ);
                                        if (!voxelShapeIntersect(aabb, singleAABB)) {
                                            continue;
                                        }

                                        if (predicate != null) {
                                            mutablePos.set(blockX, blockY, blockZ);
                                            if (!predicate.test(blockData, mutablePos)) {
                                                continue;
                                            }
                                        }

                                        if (checkOnly) {
                                            return true;
                                        } else {
                                            ret = true;
                                            intoAABB.add(singleAABB);
                                            continue;
                                        }
                                    }

                                    if (blockCollision.isEmpty()) {
                                        continue;
                                    }

                                    final net.minecraft.util.shape.VoxelShape blockCollisionOffset =
                                            blockCollision.offset((double) blockX, (double) blockY, (double) blockZ);

                                    if (!voxelShapeIntersectNoEmpty(blockCollisionOffset, aabb)) {
                                        continue;
                                    }

                                    if (predicate != null) {
                                        mutablePos.set(blockX, blockY, blockZ);
                                        if (!predicate.test(blockData, mutablePos)) {
                                            continue;
                                        }
                                    }

                                    if (checkOnly) {
                                        return true;
                                    } else {
                                        ret = true;
                                        intoVoxel.add(blockCollisionOffset);
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    /**
     * 获取与指定 AxisAlignedBB 存在碰撞的所有方块位置。
     *
     * @param world     世界
     * @param box       目标碰撞箱（世界坐标）
     * @param loadChunks 是否加载未加载的区块（若为 false，未加载区块内的方块将被忽略）
     * @return 与之相交的方块位置列表（按遍历顺序，无去重）
     */
    public static List<BlockPos> getIntersectingBlockPositions(World world, Box box, boolean loadChunks) {
        List<BlockPos> result = new ArrayList<>();

        // 扩展一个极小容差，确保边界方块被包含（与原版碰撞检测一致）
        final double eps = COLLISION_EPSILON;

        int minBlockX = MathHelper.floor(box.minX - eps) - 1;
        int maxBlockX = MathHelper.floor(box.maxX + eps) + 1;
        int minBlockY = MathHelper.floor(box.minY - eps) - 1;
        int maxBlockY = MathHelper.floor(box.maxY + eps) + 1;
        int minBlockZ = MathHelper.floor(box.minZ - eps) - 1;
        int maxBlockZ = MathHelper.floor(box.maxZ + eps) + 1;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;
        int minChunkY = minBlockY >> 4;
        int maxChunkY = maxBlockY >> 4;
        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        ChunkManager chunkManager = world.getChunkManager();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                Chunk chunk = chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, loadChunks);
                if (chunk == null) continue;

                ChunkSection[] sections = chunk.getSectionArray();
                int bottomSection = world.getBottomSectionCoord();

                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    int sectionIdx = chunkY - bottomSection;
                    if (sectionIdx < 0 || sectionIdx >= sections.length) continue;
                    ChunkSection section = sections[sectionIdx];
                    if (section == null || section.isEmpty()) continue;

                    // 获取该段内的方块状态容器
                    PalettedContainer<BlockState> states = section.getBlockStateContainer();

                    // 本段内需要迭代的局部坐标范围
                    int minX = (chunkX == minChunkX) ? (minBlockX & 15) : 0;
                    int maxX = (chunkX == maxChunkX) ? (maxBlockX & 15) : 15;
                    int minY = (chunkY == minChunkY) ? (minBlockY & 15) : 0;
                    int maxY = (chunkY == maxChunkY) ? (maxBlockY & 15) : 15;
                    int minZ = (chunkZ == minChunkZ) ? (minBlockZ & 15) : 0;
                    int maxZ = (chunkZ == maxChunkZ) ? (maxBlockZ & 15) : 15;

                    for (int y = minY; y <= maxY; y++) {
                        int blockY = (chunkY << 4) + y;
                        for (int z = minZ; z <= maxZ; z++) {
                            int blockZ = (chunkZ << 4) + z;
                            for (int x = minX; x <= maxX; x++) {
                                int blockX = (chunkX << 4) + x;
                                int localIndex = x + (z << 4) + (y << 8);
                                BlockState state = states.get(localIndex);

                                // 快速跳过空气（可选，但可提升性能）
                                if (state.isAir()) continue;

                                // 获取碰撞形状（优先使用 Moonrise 常量形状）
                                VoxelShape shape =
                                        MoonriseBlockStateBaseAccess.of(state).moonrise$getConstantCollisionShape();
                                if (shape == null) {
                                    mutablePos.set(blockX, blockY, blockZ);
                                    shape = state.getCollisionShape(world, mutablePos, ShapeContext.absent());
                                }
                                if (shape.isEmpty()) continue;

                                // 将形状便宜到世界坐标
                                shape = shape.offset(blockX, blockY, blockZ);

                                // 使用已有的快速相交检测方法
                                if (voxelShapeIntersectNoEmpty(shape, box)) {
                                    result.add(new BlockPos(blockX, blockY, blockZ));
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    public static boolean isHardColliding(Entity entity) {
        return entity instanceof BoatEntity
                || entity instanceof AbstractMinecartEntity
                || entity instanceof ShulkerEntity
                || entity.isCollidable();
    }

    public static boolean getEntityHardCollisions(
            final net.minecraft.world.World world,
            final net.minecraft.entity.Entity entity,
            net.minecraft.util.math.Box aabb,
            final java.util.List<net.minecraft.util.math.Box> into,
            final int collisionFlags,
            final java.util.function.Predicate<net.minecraft.entity.Entity> predicate) {
        final boolean checkOnly = (collisionFlags & COLLISION_FLAG_CHECK_ONLY) != 0;

        boolean ret = false;

        // to comply with vanilla intersection rules, expand by -epsilon so that we only get stuff we definitely collide
        // with.
        // Vanilla for hard collisions has this backwards, and they expand by +epsilon but this causes terrible problems
        // specifically with boat collisions.

        // we have to add back the checks because of shitting shulker shits

        aabb = aabb.expand(-COLLISION_EPSILON, -COLLISION_EPSILON, -COLLISION_EPSILON);
        final java.util.List<net.minecraft.entity.Entity> entities;
        if (entity != null && isHardColliding(entity)) {
            entities = world.getOtherEntities(entity, aabb, e -> predicate == null || predicate.test(e));
        } else {
            entities = world.getOtherEntities(
                    entity, aabb, (e) -> isHardColliding(e) && (predicate == null || predicate.test(e)));
            // entities =
            // ((ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter)world).moonrise$getHardCollidingEntities(entity, aabb, predicate);
        }

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final net.minecraft.entity.Entity otherEntity = entities.get(i);

            if (otherEntity.isSpectator()) {
                continue;
            }

            if ((entity == null && otherEntity.isCollidable())
                    || (entity != null && entity.collidesWith(otherEntity))) {
                if (checkOnly) {
                    return true;
                } else {
                    into.add(otherEntity.getBoundingBox());
                    ret = true;
                }
            }
        }

        return ret;
    }

    public static boolean getCollisions(
            final net.minecraft.world.World world,
            final net.minecraft.entity.Entity entity,
            final net.minecraft.util.math.Box aabb,
            final java.util.List<net.minecraft.util.shape.VoxelShape> intoVoxel,
            final java.util.List<net.minecraft.util.math.Box> intoAABB,
            final int collisionFlags,
            final java.util.function.BiPredicate<net.minecraft.block.BlockState, net.minecraft.util.math.BlockPos>
                    blockPredicate,
            final java.util.function.Predicate<net.minecraft.entity.Entity> entityPredicate,
            BiFunction<net.minecraft.block.BlockState, net.minecraft.util.math.BlockPos, Box> environmentFilter) {
        if ((collisionFlags & COLLISION_FLAG_CHECK_ONLY) != 0) {
            return getCollisionsForBlocksOrWorldBorder(
                            world, entity, aabb, intoVoxel, intoAABB, collisionFlags, blockPredicate, environmentFilter)
                    || getEntityHardCollisions(world, entity, aabb, intoAABB, collisionFlags, entityPredicate);
        } else {
            return getCollisionsForBlocksOrWorldBorder(
                            world, entity, aabb, intoVoxel, intoAABB, collisionFlags, blockPredicate, environmentFilter)
                    | getEntityHardCollisions(world, entity, aabb, intoAABB, collisionFlags, entityPredicate);
        }
    }

    public static final class LazyEntityCollisionContext extends net.minecraft.block.EntityShapeContext {

        private net.minecraft.block.ShapeContext delegate;
        private boolean delegated;

        public LazyEntityCollisionContext(final net.minecraft.entity.Entity entity) {
            super(false, 0.0, null, null, entity);
        }

        public boolean isDelegated() {
            final boolean delegated = this.delegated;
            this.delegated = false;
            return delegated;
        }

        public net.minecraft.block.ShapeContext getDelegate() {
            this.delegated = true;
            final net.minecraft.entity.Entity entity = this.getEntity();
            return this.delegate == null
                    ? this.delegate = (entity == null
                            ? net.minecraft.block.ShapeContext.absent()
                            : net.minecraft.block.ShapeContext.of(entity))
                    : this.delegate;
        }

        @Override
        public boolean isDescending() {
            return this.getDelegate().isDescending();
        }

        @Override
        public boolean isAbove(
                final net.minecraft.util.shape.VoxelShape shape,
                final net.minecraft.util.math.BlockPos pos,
                final boolean defaultValue) {
            return this.getDelegate().isAbove(shape, pos, defaultValue);
        }

        @Override
        public boolean isHolding(final net.minecraft.item.Item item) {
            return this.getDelegate().isHolding(item);
        }

        @Override
        public boolean canWalkOnFluid(
                final net.minecraft.fluid.FluidState state, final net.minecraft.fluid.FluidState fluidState) {
            return this.getDelegate().canWalkOnFluid(state, fluidState);
        }
    }

    private CollisionUtil() {
        throw new RuntimeException();
    }

    public static double calculateAxisCollide(
            VoxelShape voxelShape, final Direction.Axis axis, final Box source, final double source_move) {
        switch (axis) {
            case X: {
                return CollisionUtil.collideX(voxelShape, source, source_move);
            }
            case Y: {
                return CollisionUtil.collideY(voxelShape, source, source_move);
            }
            case Z: {
                return CollisionUtil.collideZ(voxelShape, source, source_move);
            }
            default: {
                throw new RuntimeException("Unknown axis: " + axis);
            }
        }
    }

    public static double calculateAxisMin(MoonriseVoxelShapeAccess access, Direction.Axis axis) {
        final CachedShapeData shapeData = access.moonrise$getCachedVoxelData();
        switch (axis) {
            case X: {
                final int idx = shapeData.minFullX();
                return idx >= shapeData.sizeX()
                        ? Double.POSITIVE_INFINITY
                        : (access.moonrise$rootCoordinatesX()[idx] + access.moonrise$offsetX());
            }
            case Y: {
                final int idx = shapeData.minFullY();
                return idx >= shapeData.sizeY()
                        ? Double.POSITIVE_INFINITY
                        : (access.moonrise$rootCoordinatesY()[idx] + access.moonrise$offsetY());
            }
            case Z: {
                final int idx = shapeData.minFullZ();
                return idx >= shapeData.sizeZ()
                        ? Double.POSITIVE_INFINITY
                        : (access.moonrise$rootCoordinatesZ()[idx] + access.moonrise$offsetZ());
            }
            default: {
                // should never get here
                return Double.POSITIVE_INFINITY;
            }
        }
    }

    public static double calculateAxisMax(MoonriseVoxelShapeAccess access, Direction.Axis axis) {
        final CachedShapeData shapeData = access.moonrise$getCachedVoxelData();
        switch (axis) {
            case X: {
                final int idx = shapeData.maxFullX();
                return idx <= 0
                        ? Double.NEGATIVE_INFINITY
                        : (access.moonrise$rootCoordinatesX()[idx] + access.moonrise$offsetX());
            }
            case Y: {
                final int idx = shapeData.maxFullY();
                return idx <= 0
                        ? Double.NEGATIVE_INFINITY
                        : (access.moonrise$rootCoordinatesY()[idx] + access.moonrise$offsetY());
            }
            case Z: {
                final int idx = shapeData.maxFullZ();
                return idx <= 0
                        ? Double.NEGATIVE_INFINITY
                        : (access.moonrise$rootCoordinatesZ()[idx] + access.moonrise$offsetZ());
            }
            default: {
                // should never get here
                return Double.NEGATIVE_INFINITY;
            }
        }
    }

    public static List<Vec3d> getBoxVertices(Box box) {
        List<Vec3d> vertices = new ArrayList<>(8);

        // 遍历所有可能的组合（2^3 = 8 种）
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX; // 最低位决定 X
            double y = (i & 2) == 0 ? box.minY : box.maxY; // 次低位决定 Y
            double z = (i & 4) == 0 ? box.minZ : box.maxZ; // 最高位决定 Z
            vertices.add(new Vec3d(x, y, z));
        }

        return vertices;
    }

    public static boolean collisionDebugRender() {
        return RenderTasks.DEBUG_RENDER_COLLISION && RenderTasks.DEBUG_RENDER_COLLISION_RENDERING;
    }

    public static List<Box> getIntersectBox(Box box, List<Box> boxList) {
        List<Box> arrayList = new ArrayList<>();
        for (var b : boxList) {
            if (voxelShapeIntersect(box, b)) {
                arrayList.add(b);
            }
        }
        return arrayList;
    }

    // todo line and given box collide method

    public static boolean isEntitySupported(Entity entity) {
        return isEntitySupported(entity, 0.5D);
    }

    public static boolean isEntitySupported(Entity entity, double yDepth) {
        World world = entity.getEntityWorld();
        Box originalBox = entity.getBoundingBox();
        // 向下平移 0.5 格，检测区域从脚底下方 0.5 格处开始
        Box checkBox = originalBox.stretch(0, -yDepth, 0);

        List<VoxelShape> voxelShapes = new ArrayList<>();
        List<Box> aabbShapes = new ArrayList<>();
        int flags = 0; // 不需要加载未加载区块，不需要边界检测

        boolean hasCollision = CollisionUtil.getCollisionsForBlocksOrWorldBorder(
                world,
                entity, // 可为 null，但传入实体可能用于特殊上下文（如忽略自身）
                checkBox,
                voxelShapes,
                aabbShapes,
                flags,
                null, // 方块过滤器（可选）
                null // 环境过滤器（可选）
                );

        return hasCollision;
    }
}
