package me.matl114.utils;

import java.util.*;
import me.matl114.utils.annotations.NeedTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.Difficulty;
import net.minecraft.world.EmptyBlockView;

@NeedTest
public final class ExplosionUtils {
    private static final double EPSILON = 1.0E-7D;
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final float END_CRYSTAL_POWER = 6.0F;
    public static final float RESPAWN_ANCHOR_POWER = 5.0F;
    public static final float BED_POWER = 5.0F;
    public static final float LARGE_FIREBALL_POWER = 1.0F;

    /**
     * Mirrors the Comet-style explosionDamage entry point.
     *
     * <p>The return value includes armor, resistance and explosion-protection reductions
     * for {@code target}. Use calculateQuickDamage when the raw damage is required.
     */
    public static float explosionDamage(
            LivingEntity target, Vec3d explosionPos, float power, BlockView world, HitRule hitRule) {
        if (target == null) {
            return 0.0F;
        }
        return (float)
                calculateExplosionRawDamage(power, explosionPos, target.getBoundingBox(), fromWorld(world), hitRule);
    }

    public static float explosionDamage(
            Box predictedPos, Vec3d explosionPos, float power, BlockView world, HitRule hitRule) {

        return calculateExplosionRawDamage(power, explosionPos, predictedPos, fromWorld(world), hitRule);
    }

    public static float crystalDamage(Box predicatedPos, Vec3d explosionPos, BlockView world, HitRule hitRule) {
        return calculateExplosionRawDamage(END_CRYSTAL_POWER, explosionPos, predicatedPos, fromWorld(world), hitRule);
    }

    public static float respawnAnchorDamage(Box predictedPos, Vec3d explosionPos, BlockView world, HitRule hitRule) {
        return calculateExplosionRawDamage(
                RESPAWN_ANCHOR_POWER,
                explosionPos,
                predictedPos,
                fromWorldWithOverrides(world, Map.of(BlockPos.ofFloored(explosionPos), Blocks.AIR.getDefaultState())),
                hitRule);
    }

    public static float calculateExplosionMaxDamage(float power, Box targetBox, Vec3d explosionPos) {
        Vec3d targetPos =
                new Vec3d((targetBox.minX + targetBox.maxX) / 2, targetBox.minY, (targetBox.minZ + targetBox.maxZ) / 2);
        double normalizedDistance = getNormalizedDistance(power, explosionPos, targetPos);
        if (normalizedDistance >= 1) {
            return 0.0F;
        }
        double impact;
        impact = getImpact(normalizedDistance, 1);
        return (float) getRawDamage(power, impact);
    }

    public static float calculateExplosionRawDamage(
            float power, Vec3d explosionPos, Box targetBox, BlockStateAccess access, HitRule hitRule) {
        Vec3d targetPos =
                new Vec3d((targetBox.minX + targetBox.maxX) / 2, targetBox.minY, (targetBox.minZ + targetBox.maxZ) / 2);
        double normalizedDistance = getNormalizedDistance(power, explosionPos, targetPos);
        if (normalizedDistance >= 1) {
            return 0.0F;
        }
        double allExposureDamage = getRawDamage(power, 1.0F);
        double impact;
        double exposure;
        if (allExposureDamage > 2) {
            exposure = getExposure(access, explosionPos, targetBox, hitRule);
        } else {
            exposure = getExposureSimplified(access, explosionPos, targetBox, hitRule);
        }
        impact = getImpact(normalizedDistance, exposure);
        return (float) getRawDamage(power, impact);
    }

    @NeedTest
    private static Set<BlockPos> collectPotentiallyDestroyedBlocks(
            float power, Vec3d explosionPos, BlockStateAccess access) {
        if (power <= 0.0F) {
            return Set.of();
        }

        Set<BlockPos> destroyedBlocks = new HashSet<>();
        for (int x = 0; x < 16; ++x) {
            for (int y = 0; y < 16; ++y) {
                for (int z = 0; z < 16; ++z) {
                    if (x != 0 && x != 15 && y != 0 && y != 15 && z != 0 && z != 15) {
                        continue;
                    }

                    double directionX = (double) x / 15.0D * 2.0D - 1.0D;
                    double directionY = (double) y / 15.0D * 2.0D - 1.0D;
                    double directionZ = (double) z / 15.0D * 2.0D - 1.0D;
                    double length =
                            Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
                    directionX /= length;
                    directionY /= length;
                    directionZ /= length;

                    // Use the maximum vanilla random multiplier so any block that can be removed
                    // by this blast is treated as non-persistent cover.
                    double energy = power * 1.3D;
                    double currentX = explosionPos.x;
                    double currentY = explosionPos.y;
                    double currentZ = explosionPos.z;

                    while (energy > 0.0D) {
                        BlockPos pos = BlockPos.ofFloored(currentX, currentY, currentZ);
                        BlockState state = access.getBlockState(pos);
                        if (access.hasBlastResistance(pos, state)) {
                            energy -= (access.getBlastResistance(pos, state) + 0.3D) * 0.3D;
                        }
                        if (energy > 0.0D && state != null && !state.isAir()) {
                            destroyedBlocks.add(pos.toImmutable());
                        }

                        currentX += directionX * 0.3D;
                        currentY += directionY * 0.3D;
                        currentZ += directionZ * 0.3D;
                        energy -= 0.22500001D;
                    }
                }
            }
        }
        return destroyedBlocks;
    }

    @NeedTest
    private static List<Vec3d> collectExposureSamplePoints(Box box) {
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1.0D / (xDiff * 2.0D + 1.0D);
        double yStep = 1.0D / (yDiff * 2.0D + 1.0D);
        double zStep = 1.0D / (zDiff * 2.0D + 1.0D);

        if (xStep <= 0.0D || yStep <= 0.0D || zStep <= 0.0D) {
            return List.of();
        }

        double xOffset = (1.0D - Math.floor(1.0D / xStep) * xStep) * 0.5D;
        double zOffset = (1.0D - Math.floor(1.0D / zStep) * zStep) * 0.5D;

        xStep *= xDiff;
        yStep *= yDiff;
        zStep *= zDiff;

        double startX = box.minX + xOffset;
        double startY = box.minY;
        double startZ = box.minZ + zOffset;
        double endX = box.maxX + xOffset;
        double endY = box.maxY;
        double endZ = box.maxZ + zOffset;

        List<Vec3d> samples = new ArrayList<>();
        for (double x = startX; x <= endX + EPSILON; x += xStep) {
            for (double y = startY; y <= endY + EPSILON; y += yStep) {
                for (double z = startZ; z <= endZ + EPSILON; z += zStep) {
                    samples.add(new Vec3d(x, y, z));
                }
            }
        }
        return samples;
    }

    @NeedTest
    public static double getNormalizedDistance(float power, Vec3d explosionPos, Vec3d targetPos) {
        if (power <= 0.0F) {
            return Double.POSITIVE_INFINITY;
        }
        return explosionPos.distanceTo(targetPos) / (power * 2.0D);
    }

    @NeedTest
    public static double getImpact(double normalizedDistance, double exposure) {
        if (normalizedDistance >= 1.0D || exposure <= 0.0D) {
            return 0.0D;
        }
        return (1.0D - normalizedDistance) * MathHelper.clamp(exposure, 0.0D, 1.0D);
    }

    public static double getRawDamage(float power, double impact) {
        double damageScale = power * 2;
        if (impact <= 0.0D) {
            return 0.0D;
        }
        return ((impact * impact + impact) / 2.0D) * 7.0D * damageScale + 1.0D;
    }

    private static float getExposure(BlockStateAccess access, Vec3d source, Box box, HitRule hitRule) {
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1 / (xDiff * 2 + 1);
        double yStep = 1 / (yDiff * 2 + 1);
        double zStep = 1 / (zDiff * 2 + 1);

        if (xStep > 0 && yStep > 0 && zStep > 0) {
            int misses = 0;
            int hits = 0;

            double xOffset = (1 - Math.floor(1 / xStep) * xStep) * 0.5;
            double zOffset = (1 - Math.floor(1 / zStep) * zStep) * 0.5;

            xStep = xStep * xDiff;
            yStep = yStep * yDiff;
            zStep = zStep * zDiff;

            double startX = box.minX + xOffset;
            double startY = box.minY;
            double startZ = box.minZ + zOffset;
            double endX = box.maxX + xOffset;
            double endY = box.maxY;
            double endZ = box.maxZ + zOffset;

            for (double x = startX; x <= endX; x += xStep) {
                for (double y = startY; y <= endY; y += yStep) {
                    for (double z = startZ; z <= endZ; z += zStep) {
                        Vec3d position = new Vec3d(x, y, z);

                        if (!rayCastAccept(access, source, position, hitRule)) {
                            misses++;
                        }
                        hits++;
                    }
                }
            }

            return (float) misses / hits;
        }

        return 0f;
    }

    private static float getExposureSimplified(BlockStateAccess access, Vec3d source, Box box, HitRule hitRule) {
        Vec3d center = box.getCenter();
        List<Vec3d> samplePoints = List.of(
                new Vec3d(box.minX, center.y, center.z),
                new Vec3d(box.maxX, center.y, center.z),
                new Vec3d(center.x, box.minY, center.z),
                new Vec3d(center.x, box.maxY, center.z),
                new Vec3d(center.x, center.y, box.minZ),
                new Vec3d(center.x, center.y, box.maxZ));

        int visibleCount = 0;
        for (Vec3d samplePoint : samplePoints) {
            if (!rayCastAccept(access, source, samplePoint, hitRule)) {
                visibleCount++;
            }
        }
        return (float) visibleCount / (float) samplePoints.size();
    }

    private static boolean rayCastAccept(BlockStateAccess stateAccess, Vec3d source, Vec3d pos, HitRule hitRule) {
        BlockPos startFuckPos = BlockPos.ofFloored(MathHelper.lerp(-1E-7, source, pos));
        BlockPos endFuckPos = BlockPos.ofFloored(MathHelper.lerp(-1E-7, pos, source));
        for (var re : RaycastUtils.createRaycastBlockPoses(pos, source, true)) {
            boolean strict = Objects.equals(startFuckPos, re) || Objects.equals(endFuckPos, re);
            BlockState state = stateAccess.getBlockState(re);
            if (hitRule.mayHit(pos, source, re, state, strict)) {
                return true;
            }
        }
        return false;
    }

    public static float getMultipliedDamageByDifficulty(ClientWorld world, float amount) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            amount = 0.0F;
        }

        if (world.getDifficulty() == Difficulty.EASY) {
            amount = Math.min(amount / 2.0F + 1.0F, amount);
        }

        if (world.getDifficulty() == Difficulty.HARD) {
            amount = amount * 3.0F / 2.0F;
        }
        return amount;
    }

    @NeedTest
    public static BlockStateAccess fromWorld(BlockView world) {
        return new BlockStateAccess() {
            @Override
            @NeedTest
            public BlockState getBlockState(BlockPos pos) {
                return world.getBlockState(pos);
            }

            @Override
            @NeedTest
            public VoxelShape getCollisionShape(BlockPos pos) {
                return world.getBlockState(pos).getCollisionShape(world, pos);
            }

            @Override
            @NeedTest
            public double getFluidResistance(BlockPos pos) {
                return world.getFluidState(pos).getBlastResistance();
            }
        };
    }

    @NeedTest
    public static BlockStateAccess fromWorldWithOverrides(BlockView world, Map<BlockPos, BlockState> overrides) {
        Map<BlockPos, BlockState> safeOverrides = overrides == null ? Map.of() : overrides;
        return new BlockStateAccess() {
            @Override
            @NeedTest
            public BlockState getBlockState(BlockPos pos) {
                return safeOverrides.containsKey(pos) ? safeOverrides.get(pos) : world.getBlockState(pos);
            }

            @Override
            @NeedTest
            public VoxelShape getCollisionShape(BlockPos pos) {
                BlockState overrideState = safeOverrides.get(pos);
                if (overrideState != null || safeOverrides.containsKey(pos)) {
                    return overrideState == null ? VoxelShapes.empty() : overrideState.getCollisionShape(world, pos);
                }
                return world.getBlockState(pos).getCollisionShape(world, pos);
            }

            @Override
            @NeedTest
            public double getFluidResistance(BlockPos pos) {
                if (safeOverrides.containsKey(pos)) {
                    BlockState overrideState = safeOverrides.get(pos);
                    return overrideState == null
                            ? 0.0D
                            : overrideState.getFluidState().getBlastResistance();
                }
                return world.getFluidState(pos).getBlastResistance();
            }
        };
    }

    @NeedTest
    public static BlockStateAccess fromMapWithFallback(Map<BlockPos, BlockState> overrides, BlockStateAccess fallback) {
        Map<BlockPos, BlockState> safeOverrides = overrides == null ? Map.of() : overrides;
        BlockStateAccess safeFallback = fallback == null ? emptyAccess() : fallback;
        return new BlockStateAccess() {
            @Override
            @NeedTest
            public BlockState getBlockState(BlockPos pos) {
                return safeOverrides.containsKey(pos) ? safeOverrides.get(pos) : safeFallback.getBlockState(pos);
            }

            @Override
            @NeedTest
            public VoxelShape getCollisionShape(BlockPos pos) {
                if (safeOverrides.containsKey(pos)) {
                    BlockState overrideState = safeOverrides.get(pos);
                    return overrideState == null
                            ? VoxelShapes.empty()
                            : overrideState.getCollisionShape(EmptyBlockView.INSTANCE, pos);
                }
                return safeFallback.getCollisionShape(pos);
            }

            @Override
            @NeedTest
            public double getBlockResistance(BlockPos pos, BlockState state) {
                if (safeOverrides.containsKey(pos)) {
                    return state == null ? 0.0D : state.getBlock().getBlastResistance();
                }
                return safeFallback.getBlockResistance(pos, state);
            }

            @Override
            @NeedTest
            public double getFluidResistance(BlockPos pos) {
                if (safeOverrides.containsKey(pos)) {
                    BlockState overrideState = safeOverrides.get(pos);
                    return overrideState == null
                            ? 0.0D
                            : overrideState.getFluidState().getBlastResistance();
                }
                return safeFallback.getFluidResistance(pos);
            }
        };
    }

    @NeedTest
    public static BlockStateAccess emptyAccess() {
        return new BlockStateAccess() {
            @Override
            @NeedTest
            public BlockState getBlockState(BlockPos pos) {
                return null;
            }
        };
    }

    @NeedTest
    public interface BlockStateAccess {
        @NeedTest
        BlockState getBlockState(BlockPos pos);

        @NeedTest
        default VoxelShape getCollisionShape(BlockPos pos) {
            BlockState state = getBlockState(pos);
            if (state == null) {
                return VoxelShapes.empty();
            }
            return state.getCollisionShape(EmptyBlockView.INSTANCE, pos);
        }

        @NeedTest
        default double getBlockResistance(BlockPos pos, BlockState state) {
            return state == null ? 0.0D : state.getBlock().getBlastResistance();
        }

        @NeedTest
        default double getFluidResistance(BlockPos pos) {
            return 0.0D;
        }

        @NeedTest
        default double getBlastResistance(BlockPos pos, BlockState state) {
            return Math.max(getBlockResistance(pos, state), getFluidResistance(pos));
        }

        @NeedTest
        default boolean hasBlastResistance(BlockPos pos, BlockState state) {
            return (state != null && !state.isAir()) || getFluidResistance(pos) > 0.0D;
        }
    }

    public static final HitRule ALL_TERRAIN = (pos, state) -> {
        return true;
    };

    public static final HitRule EXPLOSION_RESISTENCE = (pos, state) -> {
        return state.getBlock().getBlastResistance() > 600;
    };

    @NeedTest
    public interface HitRule {
        default boolean mayHit(Vec3d start, Vec3d end, BlockPos pos, BlockState state, boolean strictCheck) {
            if (!state.isAir() && !state.isLiquid() && mayHitIgnoreShape(pos, state)) {
                if (!strictCheck && state.isFullCube(mc.world, pos)) {
                    return true;
                }
                var shape = state.getCollisionShape(mc.world, pos);
                if (shape.isEmpty()) {
                    return false;
                }
                BlockHitResult result = shape.raycast(start, end, pos);
                return result != null && result.getType() == HitResult.Type.BLOCK;
            }
            return false;
        }

        public boolean mayHitIgnoreShape(BlockPos pos, BlockState state);
    }
}
