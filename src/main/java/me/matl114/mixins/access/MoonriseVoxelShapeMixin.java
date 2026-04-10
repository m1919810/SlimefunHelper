package me.matl114.mixins.access;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import me.matl114.accessors.moonrise.MoonriseVoxelShapeAccess;
import me.matl114.utils.CollisionUtil;
import me.matl114.utils.Debug;
import me.matl114.utils.collections.FlatBitsetUtil;
import me.matl114.utils.world.CachedShapeData;
import me.matl114.utils.world.CachedToAABBs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import org.spongepowered.asm.mixin.*;

@Environment(EnvType.CLIENT)
@Mixin(VoxelShape.class)
public abstract class MoonriseVoxelShapeMixin implements MoonriseVoxelShapeAccess {
    @Final
    @Shadow
    protected VoxelSet voxels;

    @Unique
    private double offsetX;

    @Unique
    private double offsetY;

    @Unique
    private double offsetZ;

    @Unique
    private Box singleAABBRepresentation;

    @Unique
    private double[] rootCoordinatesX;

    @Unique
    private double[] rootCoordinatesY;

    @Unique
    private double[] rootCoordinatesZ;

    private CachedShapeData cachedShapeData;

    @Unique
    private boolean isEmpty;

    @Unique
    private CachedToAABBs cachedToAABBs;

    @Unique
    public CachedToAABBs moonrise$cachedToAABBs() {
        return cachedToAABBs;
    }

    @Unique
    public void moonriss$setCachedToAABBs(CachedToAABBs aabBs) {
        this.cachedToAABBs = aabBs;
    }

    @Unique
    private Box cachedBounds;

    @Unique
    private Boolean isFullBlock;

    @Unique
    private Boolean occludesFullBlock;

    // must be power of two
    @Unique
    private static final int MERGED_CACHE_SIZE = 16;
    //    private ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache[] mergedORCache;

    @Override
    @Unique
    public final double moonrise$offsetX() {
        checkInitialize();
        return this.offsetX;
    }

    @Override
    @Unique
    public final double moonrise$offsetY() {
        checkInitialize();
        return this.offsetY;
    }

    @Override
    @Unique
    public final double moonrise$offsetZ() {
        checkInitialize();
        return this.offsetZ;
    }

    @Override
    @Unique
    public final Box moonrise$getSingleAABBRepresentation() {
        checkInitialize();
        return this.singleAABBRepresentation;
    }

    @Override
    @Unique
    public final double[] moonrise$rootCoordinatesX() {
        checkInitialize();
        return this.rootCoordinatesX;
    }

    @Override
    @Unique
    public final double[] moonrise$rootCoordinatesY() {
        checkInitialize();
        return this.rootCoordinatesY;
    }

    @Override
    @Unique
    public final double[] moonrise$rootCoordinatesZ() {
        checkInitialize();
        return this.rootCoordinatesZ;
    }

    public CachedShapeData moonrise$getCachedVoxelData() {
        checkInitialize();
        return this.cachedShapeData;
    }

    @Shadow
    public abstract DoubleList getPointPositions(Direction.Axis axis);

    private static double[] extractRawArray(final DoubleList list) {
        if (list == null) {
            Debug.stackTrace();
            return new double[0];
        }
        if (list instanceof it.unimi.dsi.fastutil.doubles.DoubleArrayList rawList) {
            final double[] raw = rawList.elements();
            final int expected = rawList.size();
            if (raw.length == expected) {
                return raw;
            } else {
                return java.util.Arrays.copyOf(raw, expected);
            }
        } else {
            return list.toDoubleArray();
        }
    }

    private static final CachedShapeData moonrise$getOrCreateCachedShapeData(VoxelSet shape) {

        final VoxelSet discreteVoxelShape = shape;

        final int sizeX = discreteVoxelShape.getXSize();
        final int sizeY = discreteVoxelShape.getYSize();
        final int sizeZ = discreteVoxelShape.getZSize();

        final int maxIndex = sizeX * sizeY * sizeZ; // exclusive

        final int longsRequired = (maxIndex + (Long.SIZE - 1)) >>> 6;
        long[] voxelSet;

        final boolean isEmpty = discreteVoxelShape.isEmpty();

        if (discreteVoxelShape instanceof BitSetVoxelSet bitsetShape) {
            voxelSet = bitsetShape.storage.toLongArray();
            if (voxelSet.length < longsRequired) {
                // happens when the later long values are 0L, so we need to resize
                voxelSet = Arrays.copyOf(voxelSet, longsRequired);
            }
        } else {
            voxelSet = new long[longsRequired];
            if (!isEmpty) {
                final int mulX = sizeZ * sizeY;
                for (int x = 0; x < sizeX; ++x) {
                    for (int y = 0; y < sizeY; ++y) {
                        for (int z = 0; z < sizeZ; ++z) {
                            if (discreteVoxelShape.contains(x, y, z)) {
                                // index = z + y*size_z + x*(size_z*size_y)
                                final int index = z + y * sizeZ + x * mulX;

                                voxelSet[index >>> 6] |= 1L << index;
                            }
                        }
                    }
                }
            }
        }

        final boolean hasSingleAABB =
                sizeX == 1 && sizeY == 1 && sizeZ == 1 && !isEmpty && discreteVoxelShape.contains(0, 0, 0);

        final int minFullX = discreteVoxelShape.getMin(Direction.Axis.X);
        final int minFullY = discreteVoxelShape.getMin(Direction.Axis.Y);
        final int minFullZ = discreteVoxelShape.getMin(Direction.Axis.Z);

        final int maxFullX = discreteVoxelShape.getMax(Direction.Axis.X);
        final int maxFullY = discreteVoxelShape.getMax(Direction.Axis.Y);
        final int maxFullZ = discreteVoxelShape.getMax(Direction.Axis.Z);

        return new CachedShapeData(
                sizeX,
                sizeY,
                sizeZ,
                voxelSet,
                minFullX,
                minFullY,
                minFullZ,
                maxFullX,
                maxFullY,
                maxFullZ,
                isEmpty,
                hasSingleAABB);
    }

    public boolean initialized = false;

    public void checkInitialize() {
        if (!initialized) {
            moonrise$initCache();
        }
    }

    public final void moonrise$initCache() {
        initialized = true;
        this.cachedShapeData = moonrise$getOrCreateCachedShapeData(this.voxels);
        this.isEmpty = this.cachedShapeData.isEmpty();

        final DoubleList xList = getPointPositions(Direction.Axis.X);
        final DoubleList yList = getPointPositions(Direction.Axis.Y);
        final DoubleList zList = getPointPositions(Direction.Axis.Z);

        if (xList instanceof OffsetDoubleList offsetDoubleList) {
            if (offsetDoubleList.oldList == null) {
                Debug.info("check", offsetDoubleList, offsetDoubleList.getClass(), offsetDoubleList.offset);
            }
            this.offsetX = offsetDoubleList.offset;
            this.rootCoordinatesX = extractRawArray(offsetDoubleList.oldList);
        } else {
            this.rootCoordinatesX = extractRawArray(xList);
        }

        if (yList instanceof OffsetDoubleList offsetDoubleList) {
            if (offsetDoubleList.oldList == null) {
                Debug.info("check", offsetDoubleList, offsetDoubleList.getClass(), offsetDoubleList.offset);
            }
            this.offsetY = offsetDoubleList.offset;
            this.rootCoordinatesY = extractRawArray(offsetDoubleList.oldList);
        } else {
            this.rootCoordinatesY = extractRawArray(yList);
        }

        if (zList instanceof OffsetDoubleList offsetDoubleList) {
            if (offsetDoubleList.oldList == null) {
                Debug.info("check", offsetDoubleList, offsetDoubleList.getClass(), offsetDoubleList.offset);
            }
            this.offsetZ = offsetDoubleList.offset;
            this.rootCoordinatesZ = extractRawArray(offsetDoubleList.oldList);
        } else {
            this.rootCoordinatesZ = extractRawArray(zList);
        }

        if (this.cachedShapeData.hasSingleAABB()) {
            this.singleAABBRepresentation = new Box(
                    this.rootCoordinatesX[0] + this.offsetX,
                    this.rootCoordinatesY[0] + this.offsetY,
                    this.rootCoordinatesZ[0] + this.offsetZ,
                    this.rootCoordinatesX[1] + this.offsetX,
                    this.rootCoordinatesY[1] + this.offsetY,
                    this.rootCoordinatesZ[1] + this.offsetZ);
            this.cachedBounds = this.singleAABBRepresentation;
        }
    }

    private boolean computeFullBlock() {
        Boolean ret;
        if (this.isEmpty) {
            ret = Boolean.FALSE;
        } else if ((VoxelShape) (Object) this == VoxelShapes.fullCube()) {
            ret = Boolean.TRUE;
        } else {
            final Box singleAABB = this.singleAABBRepresentation;
            if (singleAABB == null) {
                final CachedShapeData shapeData = this.cachedShapeData;
                final int sMinX = shapeData.minFullX();
                final int sMinY = shapeData.minFullY();
                final int sMinZ = shapeData.minFullZ();

                final int sMaxX = shapeData.maxFullX();
                final int sMaxY = shapeData.maxFullY();
                final int sMaxZ = shapeData.maxFullZ();

                if (Math.abs(this.rootCoordinatesX[sMinX] + this.offsetX) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(this.rootCoordinatesY[sMinY] + this.offsetY) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(this.rootCoordinatesZ[sMinZ] + this.offsetZ) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(1.0 - (this.rootCoordinatesX[sMaxX] + this.offsetX))
                                <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(1.0 - (this.rootCoordinatesY[sMaxY] + this.offsetY))
                                <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(1.0 - (this.rootCoordinatesZ[sMaxZ] + this.offsetZ))
                                <= CollisionUtil.COLLISION_EPSILON) {

                    // index = z + y*sizeZ + x*(sizeZ*sizeY)

                    final int sizeY = shapeData.sizeY();
                    final int sizeZ = shapeData.sizeZ();

                    final long[] bitset = shapeData.voxelSet();

                    ret = Boolean.TRUE;

                    check_full:
                    for (int x = sMinX; x < sMaxX; ++x) {
                        for (int y = sMinY; y < sMaxY; ++y) {
                            final int baseIndex = y * sizeZ + x * (sizeZ * sizeY);
                            if (!FlatBitsetUtil.isRangeSet(bitset, baseIndex + sMinZ, baseIndex + sMaxZ)) {
                                ret = Boolean.FALSE;
                                break check_full;
                            }
                        }
                    }
                } else {
                    ret = Boolean.FALSE;
                }
            } else {
                ret = Boolean.valueOf(Math.abs(singleAABB.minX) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(singleAABB.minY) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(singleAABB.minZ) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(1.0 - singleAABB.maxX) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(1.0 - singleAABB.maxY) <= CollisionUtil.COLLISION_EPSILON
                        && Math.abs(1.0 - singleAABB.maxZ) <= CollisionUtil.COLLISION_EPSILON);
            }
        }

        this.isFullBlock = ret;

        return ret.booleanValue();
    }

    @Override
    public final boolean moonrise$isFullBlock() {
        checkInitialize();
        final Boolean ret = this.isFullBlock;

        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeFullBlock();
    }

    private List<Box> toAabbsUncached() {
        final List<Box> ret = new java.util.ArrayList<>();
        if (this.singleAABBRepresentation != null) {
            ret.add(this.singleAABBRepresentation);
        } else {
            final double[] coordsX = this.rootCoordinatesX;
            final double[] coordsY = this.rootCoordinatesY;
            final double[] coordsZ = this.rootCoordinatesZ;

            final double offX = this.offsetX;
            final double offY = this.offsetY;
            final double offZ = this.offsetZ;

            this.voxels.forEachBox(
                    (final int minX,
                            final int minY,
                            final int minZ,
                            final int maxX,
                            final int maxY,
                            final int maxZ) -> {
                        ret.add(new Box(
                                coordsX[minX] + offX,
                                coordsY[minY] + offY,
                                coordsZ[minZ] + offZ,
                                coordsX[maxX] + offX,
                                coordsY[maxY] + offY,
                                coordsZ[maxZ] + offZ));
                    },
                    true);
        }

        // cache result
        this.cachedToAABBs = new CachedToAABBs(ret, false, 0.0, 0.0, 0.0);

        return ret;
    }

    @Nullable
    private static Direction getDirection( // Paper - optimise collisions - public
            Box box,
            Vec3d intersectingVector,
            double[] traceDistanceResult,
            @Nullable Direction approachDirection,
            double deltaX,
            double deltaY,
            double deltaZ) {
        if (deltaX > 1.0E-7) {
            approachDirection = clipPoint(
                    traceDistanceResult,
                    approachDirection,
                    deltaX,
                    deltaY,
                    deltaZ,
                    box.minX,
                    box.minY,
                    box.maxY,
                    box.minZ,
                    box.maxZ,
                    Direction.WEST,
                    intersectingVector.x,
                    intersectingVector.y,
                    intersectingVector.z);
        } else if (deltaX < -1.0E-7) {
            approachDirection = clipPoint(
                    traceDistanceResult,
                    approachDirection,
                    deltaX,
                    deltaY,
                    deltaZ,
                    box.maxX,
                    box.minY,
                    box.maxY,
                    box.minZ,
                    box.maxZ,
                    Direction.EAST,
                    intersectingVector.x,
                    intersectingVector.y,
                    intersectingVector.z);
        }

        if (deltaY > 1.0E-7) {
            approachDirection = clipPoint(
                    traceDistanceResult,
                    approachDirection,
                    deltaY,
                    deltaZ,
                    deltaX,
                    box.minY,
                    box.minZ,
                    box.maxZ,
                    box.minX,
                    box.maxX,
                    Direction.DOWN,
                    intersectingVector.y,
                    intersectingVector.z,
                    intersectingVector.x);
        } else if (deltaY < -1.0E-7) {
            approachDirection = clipPoint(
                    traceDistanceResult,
                    approachDirection,
                    deltaY,
                    deltaZ,
                    deltaX,
                    box.maxY,
                    box.minZ,
                    box.maxZ,
                    box.minX,
                    box.maxX,
                    Direction.UP,
                    intersectingVector.y,
                    intersectingVector.z,
                    intersectingVector.x);
        }

        if (deltaZ > 1.0E-7) {
            approachDirection = clipPoint(
                    traceDistanceResult,
                    approachDirection,
                    deltaZ,
                    deltaX,
                    deltaY,
                    box.minZ,
                    box.minX,
                    box.maxX,
                    box.minY,
                    box.maxY,
                    Direction.NORTH,
                    intersectingVector.z,
                    intersectingVector.x,
                    intersectingVector.y);
        } else if (deltaZ < -1.0E-7) {
            approachDirection = clipPoint(
                    traceDistanceResult,
                    approachDirection,
                    deltaZ,
                    deltaX,
                    deltaY,
                    box.maxZ,
                    box.minX,
                    box.maxX,
                    box.minY,
                    box.maxY,
                    Direction.SOUTH,
                    intersectingVector.z,
                    intersectingVector.x,
                    intersectingVector.y);
        }

        return approachDirection;
    }

    @Nullable
    private static Direction clipPoint(
            double[] traceDistanceResult,
            @Nullable Direction approachDirection,
            double deltaX,
            double deltaY,
            double deltaZ,
            double begin,
            double minX,
            double maxX,
            double minZ,
            double maxZ,
            Direction resultDirection,
            double startX,
            double startY,
            double startZ) {
        double d = (begin - startX) / deltaX;
        double e = startY + d * deltaY;
        double f = startZ + d * deltaZ;
        if (0.0 < d
                && d < traceDistanceResult[0]
                && minX - 1.0E-7 < e
                && e < maxX + 1.0E-7
                && minZ - 1.0E-7 < f
                && f < maxZ + 1.0E-7) {
            traceDistanceResult[0] = d;
            return resultDirection;
        } else {
            return approachDirection;
        }
    }

    private static BlockHitResult raycast(final Box aabb, final Vec3d from, final Vec3d to, final BlockPos offset) {
        final double[] minDistanceArr = new double[] {1.0};
        final double diffX = to.x - from.x;
        final double diffY = to.y - from.y;
        final double diffZ = to.z - from.z;

        final Direction direction = getDirection(aabb.offset(offset), from, minDistanceArr, null, diffX, diffY, diffZ);

        if (direction == null) {
            return null;
        }

        final double minDistance = minDistanceArr[0];
        return new BlockHitResult(
                from.add(minDistance * diffX, minDistance * diffY, minDistance * diffZ), direction, offset, false);
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public boolean isEmpty() {
        checkInitialize();
        return this.isEmpty; // Paper - optimise collisions
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public double getMin(Direction.Axis axis) {
        checkInitialize();
        // Paper start - optimise collisions
        return CollisionUtil.calculateAxisMin(this, axis);
        // Paper end - optimise collisions
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public double getMax(Direction.Axis axis) {
        checkInitialize();
        // Paper start - optimise collisions
        return CollisionUtil.calculateAxisMax(this, axis);
        // Paper end - optimise collisions
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public Box getBoundingBox() {
        checkInitialize();
        // Paper start - optimise collisions
        if (this.isEmpty) {
            throw new UnsupportedOperationException("No bounds for empty shape.");
        }
        Box cached = this.cachedBounds;
        if (cached != null) {
            return cached;
        }

        final CachedShapeData shapeData = this.cachedShapeData;

        final double[] coordsX = this.rootCoordinatesX;
        final double[] coordsY = this.rootCoordinatesY;
        final double[] coordsZ = this.rootCoordinatesZ;

        final double offX = this.offsetX;
        final double offY = this.offsetY;
        final double offZ = this.offsetZ;

        // note: if not empty, then there is one full AABB so no bounds checks are needed on the minFull/maxFull indices
        cached = new Box(
                coordsX[shapeData.minFullX()] + offX,
                coordsY[shapeData.minFullY()] + offY,
                coordsZ[shapeData.minFullZ()] + offZ,
                coordsX[shapeData.maxFullX()] + offX,
                coordsY[shapeData.maxFullY()] + offY,
                coordsZ[shapeData.maxFullZ()] + offZ);

        this.cachedBounds = cached;
        return cached;
        // Paper end - optimise collisions
    }

    private static DoubleList offsetList(final DoubleList src, final double by) {
        if (src instanceof OffsetDoubleList offsetDoubleList) {
            return new OffsetDoubleList(offsetDoubleList.oldList, by + offsetDoubleList.offset);
        }
        return new OffsetDoubleList(src, by);
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public VoxelShape offset(double x, double y, double z) {
        checkInitialize();
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return VoxelShapes.empty();
        }

        final ArrayVoxelShape ret = new ArrayVoxelShape(
                this.voxels,
                offsetList(this.getPointPositions(Direction.Axis.X), x),
                offsetList(this.getPointPositions(Direction.Axis.Y), y),
                offsetList(this.getPointPositions(Direction.Axis.Z), z));

        final CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            MoonriseVoxelShapeAccess.of(ret).moonriss$setCachedToAABBs(CachedToAABBs.offset(cachedToAABBs, x, y, z));
        }

        return ret;
        // Paper end - optimise collisions
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public VoxelShape simplify() {
        checkInitialize();
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return VoxelShapes.empty();
        }

        // note: the isFullBlock() is fuzzy, and Shapes.create() is also fuzzy which would return block()
        if (this.singleAABBRepresentation != null)
            return this.moonrise$isFullBlock() ? VoxelShapes.fullCube() : (VoxelShape) (Object) this;
        final List<Box> aabbs = this.getBoundingBoxes();

        if (aabbs.size() == 1) {
            final Box singleAABB = aabbs.get(0);
            final VoxelShape ret = VoxelShapes.cuboid(singleAABB);

            // forward AABB cache
            if (MoonriseVoxelShapeAccess.of(ret).moonrise$cachedToAABBs() == null) {
                MoonriseVoxelShapeAccess.of(ret).moonriss$setCachedToAABBs(this.cachedToAABBs);
            }

            return ret;
        } else {
            // reduce complexity of joins by splitting the merges (old complexity: n^2, new: nlogn)

            // set up flat array so that this merge is done in-place
            final VoxelShape[] tmp = new VoxelShape[aabbs.size()];

            // initialise as unmerged
            for (int i = 0, len = aabbs.size(); i < len; ++i) {
                tmp[i] = VoxelShapes.cuboid(aabbs.get(i));
            }

            int size = aabbs.size();
            while (size > 1) {
                int newSize = 0;
                for (int i = 0; i < size; i += 2) {
                    final int next = i + 1;
                    if (next >= size) {
                        // nothing to merge with, so leave it for next iteration
                        tmp[newSize++] = tmp[i];
                        break;
                    } else {
                        // merge with adjacent
                        final VoxelShape first = tmp[i];
                        final VoxelShape second = tmp[next];

                        tmp[newSize++] = VoxelShapes.combine(first, second, BooleanBiFunction.OR);
                    }
                }
                size = newSize;
            }

            final VoxelShape ret = tmp[0];

            // forward AABB cache
            if (MoonriseVoxelShapeAccess.of(ret).moonrise$cachedToAABBs() == null) {
                MoonriseVoxelShapeAccess.of(ret).moonriss$setCachedToAABBs(this.cachedToAABBs);
            }

            return ret;
        }
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public List<Box> getBoundingBoxes() {
        checkInitialize();
        // Paper start - optimise collisions
        CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            if (!cachedToAABBs.isOffset()) {
                return cachedToAABBs.aabbs();
            }

            // all we need to do is offset the cache
            cachedToAABBs = cachedToAABBs.removeOffset();
            // update cache
            this.cachedToAABBs = cachedToAABBs;

            return cachedToAABBs.aabbs();
        }

        // make new cache
        return this.toAabbsUncached();
        // Paper end - optimise collisions
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public BlockHitResult raycast(final Vec3d from, final Vec3d to, final BlockPos offset) {
        checkInitialize();
        if (this.isEmpty) {
            return null;
        }

        final Vec3d directionOpposite = to.subtract(from);
        if (directionOpposite.lengthSquared() < CollisionUtil.COLLISION_EPSILON) {
            return null;
        }

        final Vec3d fromBehind = from.add(directionOpposite.multiply(0.001));
        final double fromBehindOffsetX = fromBehind.x - (double) offset.getX();
        final double fromBehindOffsetY = fromBehind.y - (double) offset.getY();
        final double fromBehindOffsetZ = fromBehind.z - (double) offset.getZ();

        final Box singleAABB = this.singleAABBRepresentation;

        if (singleAABB != null) {
            if (singleAABB.contains(fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
                return new BlockHitResult(
                        fromBehind,
                        Direction.getFacing(directionOpposite.x, directionOpposite.y, directionOpposite.z)
                                .getOpposite(),
                        offset,
                        true);
            }
            return raycast(singleAABB, from, to, offset);
        }

        if (CollisionUtil.strictlyContains(
                (VoxelShape) (Object) this, fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
            return new BlockHitResult(
                    fromBehind,
                    Direction.getFacing(directionOpposite.x, directionOpposite.y, directionOpposite.z)
                            .getOpposite(),
                    offset,
                    true);
        }

        return Box.raycast(getBoundingBoxes(), from, to, offset);
        // Paper end - optimise collisions

    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    public Optional<Vec3d> getClosestPointTo(Vec3d point) {
        checkInitialize();
        if (this.isEmpty) {
            return Optional.empty();
        }

        Vec3d ret = null;
        double retDistance = Double.MAX_VALUE;

        final List<Box> aabbs = this.getBoundingBoxes();
        for (int i = 0, len = aabbs.size(); i < len; ++i) {
            final Box aabb = aabbs.get(i);
            final double x = MathHelper.clamp(point.x, aabb.minX, aabb.maxX);
            final double y = MathHelper.clamp(point.y, aabb.minY, aabb.maxY);
            final double z = MathHelper.clamp(point.z, aabb.minZ, aabb.maxZ);

            double dist = point.squaredDistanceTo(x, y, z);
            if (dist < retDistance) {
                ret = new Vec3d(x, y, z);
                retDistance = dist;
            }
        }

        return Optional.ofNullable(ret);
        // Paper end - optimise collisions
    }
    // Paper start - optimise collisions
    /**
     * @author
     * @reason
     */
    @Overwrite
    public double calculateMaxDistance(final Direction.Axis axis, final Box source, final double source_move) {
        checkInitialize();
        if (this.isEmpty) {
            return source_move;
        }
        if (Math.abs(source_move) < CollisionUtil.COLLISION_EPSILON) {
            return 0.0;
        }
        VoxelShape voxelShape = (VoxelShape) (Object) this;
        return CollisionUtil.calculateAxisCollide(voxelShape, axis, source, source_move);
        // Paper end - optimise collisions
    }
}
