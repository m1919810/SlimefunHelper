package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import me.matl114.utils.world.AlignedFace;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

@ApiMethod
public class RaycastUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean raycastAnySolidBlock(Entity e, Vec3d from, Vec3d to) {
        BlockHitResult bResult = raycastSolidBlockResult(e, from, to);
        return bResult != null && bResult.getType() != HitResult.Type.MISS;
    }

    public static BlockHitResult raycastSolidBlockResult(Entity e, Vec3d from, Vec3d to) {
        return mc.world.raycast(
                new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
    }

    public static boolean raycastHitAnyEntity(Entity e, Vec3d from, Vec3d to) {
        var re = ProjectileUtil.raycast(e, from, to, new Box(from, to), es -> !es.isSpectator() && es.canHit(), 16384);
        return re != null && re.getType() != HitResult.Type.MISS;
    }

    public static boolean raycastHitAnyEntityExceptPlayer(Entity e, Vec3d from, Vec3d to) {
        var re = raycastHitEntityExceptPlayerResult(e, from, to);
        return re != null && re.getType() != HitResult.Type.MISS;
    }

    public static EntityHitResult raycastHitEntityExceptPlayerResult(Entity e, Vec3d from, Vec3d to) {
        return ProjectileUtil.raycast(
                e, from, to, new Box(from, to), es -> !es.isSpectator() && es.canHit() && es != mc.player, 16384);
    }

    public static BlockHitResult createHitResult(BlockPos pos) {
        if (mc.player == null) return null;
        Vec3d startVec = mc.player.getCameraPosVec(1.0f);
        Vec3d endVec = pos.toCenterPos();
        Vec3d ray = startVec.subtract(endVec);
        Direction dir = Direction.getFacing(ray.x, ray.y, ray.z);
        Vec3d crossTargetPose = ray.lengthSquared() > 0.25
                ? switch (dir) {
                    case DOWN -> startVec.subtract(ray.multiply((startVec.y - (endVec.y - 0.5)) / ray.y));
                    case UP -> startVec.subtract(ray.multiply((startVec.y - (endVec.y + 0.5)) / ray.y));
                    case NORTH -> startVec.subtract(ray.multiply((startVec.z - (endVec.z - 0.5)) / ray.z));
                    case SOUTH -> startVec.subtract(ray.multiply((startVec.z - (endVec.z + 0.5)) / ray.z));
                    case WEST -> startVec.subtract(ray.multiply((startVec.x - (endVec.x - 0.5)) / ray.x));
                    case EAST -> startVec.subtract(ray.multiply((startVec.x - (endVec.x + 0.5)) / ray.x));
                }
                : endVec;
        return new BlockHitResult(crossTargetPose, dir, pos, false);
    }

    public static BlockHitResult createHitResult(BlockPos pos, Direction blockFace) {
        if (mc.player == null) return null;
        Vec3d endVec = pos.toCenterPos().offset(blockFace, 0.5);
        return new BlockHitResult(endVec, blockFace, pos, false);
    }

    public static Optional<BlockPos> rayTraceSpecificBlock(Predicate<Block> blockPredicate) {
        if (mc.world == null || mc.player == null) return Optional.empty();
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) mc.crosshairTarget;
            Block block = mc.world.getBlockState(blockHitResult.getBlockPos()).getBlock();
            if (blockPredicate.test(block)) {
                return Optional.of(blockHitResult.getBlockPos());
            }
        }
        // todo optimize
        Vec3d lookat = mc.player.getRotationVector().normalize().multiply(0.1);
        Vec3d cameraPose = mc.player.getCameraPosVec(1.0f);
        BlockPos.Mutable mutable = new BlockPos.Mutable(cameraPose.x, cameraPose.y, cameraPose.z);
        for (int i = 0; i < 75; ++i) {
            int x = (int) (lookat.x * i + cameraPose.x);
            int y = (int) (lookat.y * i + cameraPose.y);
            int z = (int) (lookat.z * i + cameraPose.z);
            if (x != mutable.getX() || y != mutable.getY() || z != mutable.getZ()) {
                mutable.set(x, y, z);
                BlockPos pos = mutable.toImmutable();
                Block block = mc.world.getBlockState(pos).getBlock();
                if (blockPredicate.test(block)) {
                    return Optional.of(pos);
                }
            }
        }
        return Optional.empty();
    }

    private static final Comparator<Vec3i> PRIORITIZE_LEAST_BLOCK_DISTANCE =
            Comparator.comparingDouble(vec -> -Vec3d.of(vec).add(0.5, 0.5, 0.5).squaredDistanceTo(mc.player.getPos()));

    public static HitResult findBestBlockPlacement(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isReplaceable()) {
            // zzz
            // todo complete it later
            return null;
        } else {
            return null;
        }
    }

    private static Vec3d findTargetPointOnFace(BlockState currState, BlockPos currPos, Direction direction) {
        List<Box> shapeBBs = currState
                .getOutlineShape(mc.world, currPos, ShapeContext.of(mc.player))
                .getBoundingBoxes();

        return shapeBBs.stream()
                .map(it -> {
                    AlignedFace face = getBoxFace(it, direction);

                    AlignedFace searchFace = face;

                    // Try to aim at the upper portion of the block which makes it easier to switch from full blocks to
                    // half blocks
                    if (searchFace.getTo().y >= 0.9) {
                        AlignedFace truncatedFace = searchFace.truncateY(0.6);
                        if (truncatedFace != null && !truncatedFace.isEmpty()) {
                            searchFace = truncatedFace;
                        }
                    }

                    Vec3d targetPos = searchFace.getCenter();

                    if (targetPos == null) {
                        return (Pair) null;
                    }

                    return new Pair<AlignedFace, Vec3d>(searchFace, targetPos);
                })
                .filter(Objects::<Pair<AlignedFace, Vec3d>>nonNull)
                .max(Comparator.comparingDouble((it) ->
                                // 取其中direction系列的分量 选择离得最近的
                                ((Pair<AlignedFace, Vec3d>) it)
                                        .getSecond()
                                        .subtract(new Vec3d(0.5, 0.5, 0.5))
                                        .multiply(Vec3d.of(direction.getVector()))
                                        .lengthSquared())
                        .thenComparingDouble(it -> ((Pair<AlignedFace, Vec3d>) it).getSecond().y))
                .map(Pair::getSecond)
                .map(Vec3d.class::cast)
                .orElse(null);
    }

    public static AlignedFace getBoxFace(Box box, Direction direction) {
        return switch (direction) {
            case Direction.DOWN -> new AlignedFace(
                    new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.maxZ));

            case Direction.UP -> new AlignedFace(
                    new Vec3d(box.minX, box.maxY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ));

            case Direction.SOUTH -> new AlignedFace(
                    new Vec3d(box.minX, box.minY, box.maxZ), new Vec3d(box.maxX, box.maxY, box.maxZ));

            case Direction.NORTH -> new AlignedFace(
                    new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.minZ));

            case Direction.EAST -> new AlignedFace(
                    new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ));

            case Direction.WEST -> new AlignedFace(
                    new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.minX, box.maxY, box.maxZ));
        };
    }

    // todo: add EntityHitResult;

    public static HitResult createCrossHairHitResult(
            Entity camera, double blockInteractionRange, double entityInteractionRange, float tickDelta) {
        double d = Math.max(blockInteractionRange, entityInteractionRange);
        double e = MathHelper.square(d);
        Vec3d vec3d = camera.getCameraPosVec(tickDelta);
        HitResult hitResult = camera.raycast(d, tickDelta, false);
        double f = hitResult.getPos().squaredDistanceTo(vec3d);
        if (hitResult.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            e = f;
            d = Math.sqrt(e);
        }

        Vec3d vec3d2 = camera.getRotationVec(tickDelta);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d);
        float g = 1.0F;
        Box box = camera.getBoundingBox().stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.raycast(
                camera,
                vec3d,
                vec3d3,
                box,
                (entity) -> {
                    return !entity.isSpectator() && entity.canHit();
                },
                e);
        return entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < f
                ? ensureTargetInRange(entityHitResult, vec3d, entityInteractionRange)
                : ensureTargetInRange(hitResult, vec3d, blockInteractionRange);
    }

    public static HitResult createEntityOnlyCrossHairResult(
            Entity camera, double entityInteractionRange, float tickDelta, Predicate<Entity> filter) {
        double d = entityInteractionRange;
        double e = MathHelper.square(d);
        Vec3d vec3d = camera.getCameraPosVec(tickDelta);
        Vec3d vec3d2 = camera.getRotationVec(tickDelta);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d);
        Box box = camera.getBoundingBox().stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.raycast(
                camera,
                vec3d,
                vec3d3,
                box,
                (entity) -> {
                    return !entity.isSpectator() && entity.canHit() && (filter == null || filter.test(entity));
                },
                e);
        return entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < e
                ? ensureTargetInRange(entityHitResult, vec3d, entityInteractionRange)
                : null;
    }

    private static HitResult ensureTargetInRange(HitResult hitResult, Vec3d cameraPos, double interactionRange) {
        Vec3d vec3d = hitResult.getPos();
        if (!vec3d.isInRange(cameraPos, interactionRange)) {
            Vec3d vec3d2 = hitResult.getPos();
            Direction direction =
                    Direction.getFacing(vec3d2.x - cameraPos.x, vec3d2.y - cameraPos.y, vec3d2.z - cameraPos.z);
            return BlockHitResult.createMissed(vec3d2, direction, BlockPos.ofFloored(vec3d2));
        } else {
            return hitResult;
        }
    }
}
