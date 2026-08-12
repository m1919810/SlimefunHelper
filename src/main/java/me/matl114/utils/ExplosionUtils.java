package me.matl114.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.matl114.utils.annotations.NeedTest;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;

@NeedTest
public final class ExplosionUtils {
    private static final double EPSILON = 1.0E-7D;

    @NeedTest
    private ExplosionUtils() {}

    @NeedTest
    public static ExplosionProfile getDefaultProfile(ExplosionType type) {
        return switch (type) {
            case LARGE_FIREBALL -> new ExplosionProfile(type, 1.0F, 2.0D, true, "大火球默认爆炸");
            case END_CRYSTAL -> new ExplosionProfile(type, 6.0F, 12.0D, true, "水晶爆炸");
            case RESPAWN_ANCHOR -> new ExplosionProfile(type, 5.0F, 10.0D, true, "重生锚爆炸");
            case WIND_CHARGE -> new ExplosionProfile(type, 1.2F, 2.4D, false, "风弹爆炸默认不走爆炸扣血；玩家风弹 1.2，Breeze 可传 3.0");
        };
    }

    @NeedTest
    public static ExplosionProfile createProfile(ExplosionType type, float power, double damageScale) {
        return new ExplosionProfile(type, power, damageScale, true, type.name().toLowerCase());
    }

    @NeedTest
    public static ExplosionProfile createProfile(
            ExplosionType type, float power, double damageScale, boolean damagesEntities) {
        return new ExplosionProfile(
                type, power, damageScale, damagesEntities, type.name().toLowerCase());
    }

    @NeedTest
    public static QuickDamageResult largeFireballDamage(
            Vec3d explosionPos, Vec3d targetPos, Box targetBox, BlockStateAccess access) {
        return toQuickDamageResult(calculateDamage(
                getDefaultProfile(ExplosionType.LARGE_FIREBALL),
                explosionPos,
                targetPos,
                targetBox,
                access,
                EstimatedDamageContext.none()));
    }

    @NeedTest
    public static QuickDamageResult crystalDamage(
            Vec3d explosionPos, Vec3d targetPos, Box targetBox, BlockStateAccess access) {
        return toQuickDamageResult(calculateDamage(
                getDefaultProfile(ExplosionType.END_CRYSTAL),
                explosionPos,
                targetPos,
                targetBox,
                access,
                EstimatedDamageContext.none()));
    }

    @NeedTest
    public static QuickDamageResult anchorDamage(
            Vec3d explosionPos, Vec3d targetPos, Box targetBox, BlockStateAccess access) {
        return toQuickDamageResult(calculateDamage(
                getDefaultProfile(ExplosionType.RESPAWN_ANCHOR),
                explosionPos,
                targetPos,
                targetBox,
                access,
                EstimatedDamageContext.none()));
    }

    @NeedTest
    public static QuickDamageResult windChargeDamage(
            Vec3d explosionPos, Vec3d targetPos, Box targetBox, BlockStateAccess access, float power) {
        return toQuickDamageResult(calculateDamage(
                createProfile(ExplosionType.WIND_CHARGE, power, power * 2.0D, false),
                explosionPos,
                targetPos,
                targetBox,
                access,
                EstimatedDamageContext.none()));
    }

    @NeedTest
    public static QuickDamageResult calculateQuickDamage(
            ExplosionProfile profile, Vec3d explosionPos, Vec3d targetPos, Box targetBox, BlockView world) {
        return toQuickDamageResult(
                calculateDamage(profile, explosionPos, targetPos, targetBox, world, EstimatedDamageContext.none()));
    }

    @NeedTest
    public static QuickDamageResult calculateQuickDamage(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            BlockView world,
            EstimatedDamageContext context) {
        return toQuickDamageResult(calculateDamage(profile, explosionPos, targetPos, targetBox, world, context));
    }

    @NeedTest
    public static QuickDamageResult calculateQuickDamage(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            Map<BlockPos, BlockState> overrides,
            BlockView world) {
        return calculateQuickDamage(
                profile, explosionPos, targetPos, targetBox, overrides, world, EstimatedDamageContext.none());
    }

    @NeedTest
    public static QuickDamageResult calculateQuickDamage(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            Map<BlockPos, BlockState> overrides,
            BlockView world,
            EstimatedDamageContext context) {
        return toQuickDamageResult(calculateDamage(
                profile, explosionPos, targetPos, targetBox, fromWorldWithOverrides(world, overrides), context));
    }

    @NeedTest
    public static QuickDamageResult calculateQuickDamage(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            Map<BlockPos, BlockState> overrides,
            BlockView world,
            LivingEntity entity) {
        return calculateQuickDamage(
                profile, explosionPos, targetPos, targetBox, overrides, world, createEstimatedDamageContext(entity));
    }

    @NeedTest
    public static QuickDamageResult calculateQuickDamage(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            Map<BlockPos, BlockState> overrides,
            BlockView world,
            LivingEntity entity,
            int hiddenResistanceLevel) {
        return calculateQuickDamage(
                profile,
                explosionPos,
                targetPos,
                targetBox,
                overrides,
                world,
                createEstimatedDamageContext(entity, hiddenResistanceLevel));
    }

    @NeedTest
    private static ExplosionDamageTrace calculateDamage(
            ExplosionProfile profile, Vec3d explosionPos, Vec3d targetPos, Box targetBox, BlockView world) {
        return calculateDamage(
                profile, explosionPos, targetPos, targetBox, fromWorld(world), EstimatedDamageContext.none());
    }

    @NeedTest
    private static ExplosionDamageTrace calculateDamage(
            ExplosionProfile profile, Vec3d explosionPos, Vec3d targetPos, Box targetBox, BlockStateAccess access) {
        return calculateDamage(profile, explosionPos, targetPos, targetBox, access, EstimatedDamageContext.none());
    }

    @NeedTest
    private static ExplosionDamageTrace calculateDamage(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            BlockView world,
            EstimatedDamageContext context) {
        return calculateDamage(profile, explosionPos, targetPos, targetBox, fromWorld(world), context);
    }

    @NeedTest
    private static ExplosionDamageTrace calculateDamage(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            BlockStateAccess access,
            EstimatedDamageContext context) {
        ExposureTrace exposureTrace = traceExposure(explosionPos, targetBox, access);
        double normalizedDistance = getNormalizedDistance(profile.power(), explosionPos, targetPos);
        double impact = getImpact(normalizedDistance, exposureTrace.exposure());
        double rawDamage = profile.damagesEntities() ? getRawDamage(profile.damageScale(), impact) : 0.0D;
        EstimatedDamageBreakdown estimatedBreakdown = estimateDamage(rawDamage, context);
        return new ExplosionDamageTrace(
                profile,
                explosionPos,
                targetPos,
                targetBox,
                normalizedDistance,
                exposureTrace.exposure(),
                impact,
                rawDamage,
                estimatedBreakdown.estimatedDamage(),
                estimatedBreakdown,
                exposureTrace);
    }

    @NeedTest
    private static QuickDamageResult toQuickDamageResult(ExplosionDamageTrace trace) {
        return trace == null
                ? new QuickDamageResult(0.0D, 0.0D)
                : new QuickDamageResult(trace.rawDamage(), trace.estimatedDamage());
    }

    @NeedTest
    private static ExposureTrace traceExposure(Vec3d explosionPos, Box targetBox, BlockView world) {
        return traceExposure(explosionPos, targetBox, fromWorld(world));
    }

    @NeedTest
    private static ExposureTrace traceExposure(Vec3d explosionPos, Box targetBox, BlockStateAccess access) {
        List<Vec3d> samplePoints = collectExposureSamplePoints(targetBox);
        List<RayTraceSample> rays = new ArrayList<>(samplePoints.size());
        Set<BlockPos> visitedBlocks = new LinkedHashSet<>();
        Set<BlockPos> affectingBlocks = new LinkedHashSet<>();
        int visibleCount = 0;

        for (Vec3d samplePoint : samplePoints) {
            RayTraceSample ray = traceSample(samplePoint, explosionPos, access);
            rays.add(ray);
            for (RayTraceStep step : ray.steps()) {
                visitedBlocks.add(step.pos().toImmutable());
                if (step.blocksRay()) {
                    affectingBlocks.add(step.pos().toImmutable());
                }
            }
            if (ray.visible()) {
                visibleCount++;
            }
        }

        double exposure = samplePoints.isEmpty() ? 0.0D : (double) visibleCount / (double) samplePoints.size();
        return new ExposureTrace(
                samplePoints.size(),
                visibleCount,
                exposure,
                Collections.unmodifiableList(rays),
                Collections.unmodifiableList(new ArrayList<>(visitedBlocks)),
                Collections.unmodifiableList(new ArrayList<>(affectingBlocks)));
    }

    @NeedTest
    private static RayTraceSample traceSample(Vec3d samplePoint, Vec3d explosionPos, BlockStateAccess access) {
        List<RayTraceStep> steps = traceRaySteps(samplePoint, explosionPos, access);
        BlockPos firstBlockingBlock = null;
        for (RayTraceStep step : steps) {
            if (step.blocksRay()) {
                firstBlockingBlock = step.pos().toImmutable();
                break;
            }
        }
        return new RayTraceSample(
                samplePoint,
                explosionPos,
                firstBlockingBlock == null,
                firstBlockingBlock,
                Collections.unmodifiableList(steps));
    }

    @NeedTest
    private static List<RayTraceStep> traceRaySteps(Vec3d start, Vec3d end, BlockStateAccess access) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;

        int x = MathHelper.floor(start.x);
        int y = MathHelper.floor(start.y);
        int z = MathHelper.floor(start.z);
        int endX = MathHelper.floor(end.x);
        int endY = MathHelper.floor(end.y);
        int endZ = MathHelper.floor(end.z);

        int stepX = Integer.compare(endX, x);
        int stepY = Integer.compare(endY, y);
        int stepZ = Integer.compare(endZ, z);

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : intBound(start.x, dx);
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : intBound(start.y, dy);
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : intBound(start.z, dz);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : (double) stepX / dx;
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : (double) stepY / dy;
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (double) stepZ / dz;

        List<RayTraceStep> steps = new ArrayList<>();
        int guard = 0;
        int maxSteps = Math.max(64, (Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 4) * 4);

        while (guard++ < maxSteps) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = access.getBlockState(pos);
            VoxelShape shape = access.getCollisionShape(pos);
            double resistance = access.getBlockResistance(pos, state);
            boolean hasShape = shape != null && !shape.isEmpty();
            boolean intersects = hasShape && doesLineIntersectShape(start, end, pos, shape);
            boolean blocksRay = intersects;

            steps.add(new RayTraceStep(pos.toImmutable(), state, resistance, hasShape, intersects, blocksRay));

            if (blocksRay || (x == endX && y == endY && z == endZ)) {
                break;
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxX && tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        return steps;
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
        return explosionPos.distanceTo(targetPos) / power;
    }

    @NeedTest
    public static double getImpact(double normalizedDistance, double exposure) {
        if (normalizedDistance >= 1.0D || exposure <= 0.0D) {
            return 0.0D;
        }
        return (1.0D - normalizedDistance) * MathHelper.clamp(exposure, 0.0D, 1.0D);
    }

    @NeedTest
    public static double getRawDamage(double damageScale, double impact) {
        if (impact <= 0.0D) {
            return 0.0D;
        }
        return ((impact * impact + impact) / 2.0D) * 7.0D * damageScale + 1.0D;
    }

    @NeedTest
    private static EstimatedDamageBreakdown estimateDamage(double rawDamage, EstimatedDamageContext context) {
        double afterArmor = applyArmorReduction(rawDamage, context.armor(), context.armorToughness());
        double afterResistance =
                applyResistanceReduction(afterArmor, context.visibleResistanceLevel(), context.hiddenResistanceLevel());
        int effectiveProtection = getEffectiveExplosionProtection(context.protection(), context.blastProtection());
        double estimatedDamage = applyProtectionReduction(afterResistance, effectiveProtection);
        return new EstimatedDamageBreakdown(
                rawDamage, afterArmor, afterResistance, effectiveProtection, estimatedDamage);
    }

    @NeedTest
    public static double applyArmorReduction(double damage, double armor, double armorToughness) {
        double armorRatio = Math.min(20.0D, Math.max(armor * 0.2D, armor - damage / (2.0D + armorToughness / 4.0D)));
        return damage * (1.0D - armorRatio / 25.0D);
    }

    @NeedTest
    public static double applyResistanceReduction(
            double damage, int visibleResistanceLevel, int hiddenResistanceLevel) {
        int totalLevel = Math.max(0, visibleResistanceLevel) + Math.max(0, hiddenResistanceLevel);
        if (totalLevel <= 0) {
            return damage;
        }
        double multiplier = Math.max(0.0D, 1.0D - totalLevel * 0.2D);
        return damage * multiplier;
    }

    @NeedTest
    public static int getEffectiveExplosionProtection(int protection, int blastProtection) {
        int epf = Math.max(0, protection) + Math.max(0, blastProtection) * 2;
        return Math.min(20, epf);
    }

    @NeedTest
    public static double applyProtectionReduction(double damage, int effectiveProtection) {
        return damage * (1.0D - MathHelper.clamp(effectiveProtection, 0, 20) / 25.0D);
    }

    @NeedTest
    public static EstimatedDamageContext createEstimatedDamageContext(LivingEntity entity) {
        return createEstimatedDamageContext(entity, 0);
    }

    @NeedTest
    public static EstimatedDamageContext createEstimatedDamageContext(LivingEntity entity, int hiddenResistanceLevel) {
        if (entity == null) {
            return EstimatedDamageContext.none().withHiddenResistanceLevel(hiddenResistanceLevel);
        }
        double armor = entity.getAttributeValue(EntityAttributes.ARMOR);
        double armorToughness = entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        int protection = 0;
        int blastProtection = 0;
        for (var slot :
                new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getEquippedStack(slot);
            var enchantments = stack.getEnchantments();
            protection +=
                    ItemStackUtils.getEnchantmentLevel(enchantments, net.minecraft.enchantment.Enchantments.PROTECTION);
            blastProtection += ItemStackUtils.getEnchantmentLevel(
                    enchantments, net.minecraft.enchantment.Enchantments.BLAST_PROTECTION);
        }
        int visibleResistanceLevel = entity.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE)
                ? entity.getStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE)
                                .getAmplifier()
                        + 1
                : 0;
        return new EstimatedDamageContext(
                armor,
                armorToughness,
                protection,
                blastProtection,
                visibleResistanceLevel,
                Math.max(0, hiddenResistanceLevel));
    }

    @NeedTest
    public static String getRawDamageFormulaDescription() {
        return "distanceRatio = distance(explosionPos, targetPos) / power; exposure = visibleSamples / totalSamples; impact = (1 - distanceRatio) * exposure; rawDamage = ((impact^2 + impact) / 2) * 7 * damageScale + 1";
    }

    @NeedTest
    public static String getEstimatedDamageFormulaDescription() {
        return "afterArmor = rawDamage * (1 - min(20, max(armor*0.2, armor - rawDamage/(2 + armorToughness/4))) / 25); afterResistance = afterArmor * (1 - 0.2 * (visibleResistanceLevel + hiddenResistanceLevel)); effectiveProtection = min(20, protection + 2 * blastProtection); estimatedDamage = afterResistance * (1 - effectiveProtection / 25)";
    }

    @NeedTest
    private static boolean doesLineIntersectShape(Vec3d start, Vec3d end, BlockPos pos, VoxelShape shape) {
        for (Box localBox : shape.getBoundingBoxes()) {
            Box worldBox = localBox.offset(pos.getX(), pos.getY(), pos.getZ());
            if (worldBox.raycast(start, end).isPresent()) {
                return true;
            }
            if (worldBox.contains(start) || worldBox.contains(end)) {
                return true;
            }
        }
        return false;
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
    private static double intBound(double start, double delta) {
        if (delta > 0.0D) {
            return (1.0D - frac(start)) / delta;
        }
        if (delta < 0.0D) {
            return frac(start) / -delta;
        }
        return Double.POSITIVE_INFINITY;
    }

    @NeedTest
    private static double frac(double value) {
        return value - Math.floor(value);
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
    }

    @NeedTest
    public enum ExplosionType {
        LARGE_FIREBALL,
        END_CRYSTAL,
        RESPAWN_ANCHOR,
        WIND_CHARGE
    }

    @NeedTest
    public record ExplosionProfile(
            ExplosionType type, float power, double damageScale, boolean damagesEntities, String note) {}

    @NeedTest
    public record EstimatedDamageContext(
            double armor,
            double armorToughness,
            int protection,
            int blastProtection,
            int visibleResistanceLevel,
            int hiddenResistanceLevel) {
        @NeedTest
        public static EstimatedDamageContext none() {
            return new EstimatedDamageContext(0.0D, 0.0D, 0, 0, 0, 0);
        }

        @NeedTest
        public EstimatedDamageContext withHiddenResistanceLevel(int level) {
            return new EstimatedDamageContext(
                    armor, armorToughness, protection, blastProtection, visibleResistanceLevel, Math.max(0, level));
        }
    }

    @NeedTest
    private record EstimatedDamageBreakdown(
            double rawDamage,
            double afterArmor,
            double afterResistance,
            int effectiveProtection,
            double estimatedDamage) {}

    @NeedTest
    public record QuickDamageResult(double rawDamage, double estimatedDamage) {}

    @NeedTest
    private record RayTraceStep(
            BlockPos pos,
            BlockState state,
            double resistance,
            boolean hasCollisionShape,
            boolean intersectsSegment,
            boolean blocksRay) {}

    @NeedTest
    private record RayTraceSample(
            Vec3d samplePoint,
            Vec3d explosionPoint,
            boolean visible,
            BlockPos firstBlockingBlock,
            List<RayTraceStep> steps) {}

    @NeedTest
    private record ExposureTrace(
            int sampleCount,
            int visibleSampleCount,
            double exposure,
            List<RayTraceSample> rays,
            List<BlockPos> visitedBlocks,
            List<BlockPos> affectingBlocks) {}

    @NeedTest
    private record ExplosionDamageTrace(
            ExplosionProfile profile,
            Vec3d explosionPos,
            Vec3d targetPos,
            Box targetBox,
            double normalizedDistance,
            double exposure,
            double impact,
            double rawDamage,
            double estimatedDamage,
            EstimatedDamageBreakdown estimatedDamageBreakdown,
            ExposureTrace exposureTrace) {}
}
