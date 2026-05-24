package me.matl114.hacks.modules.move;

import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class ForwardTp extends BaseModule {
    public final ModulePath quickMove = makePath(Configs.MOV_CONFIG, "quick-move");

    public ForwardTp() {}

    public final KeyBindRef frontKey = hotkey(quickMove.add("quick-move"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::quickMovFront))
            .build();

    public final KeyBindRef wallKey = hotkey(quickMove.add("quick-to-wall"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::quickMovTowardsWall))
            .build();

    public final DoubleRef maxDistance = builder(quickMove.add("max-distance"), DoubleRef.TYPE)
            .defaultValue(120.0D)
            .validator(Configs.doubleRange(0.0D, 114514.0D))
            .build();

    public final FlagRef useTpMethod = builder(quickMove.add("ignore-move-collision"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    final double distance = 0.1;

    public boolean quickMovFront() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        Vec3d vec3d = player.getPos();
        Vec3d lookat = player.getRotationVector().normalize();
        Vec3d lastAvailablePos = calculateAvailableMovPlace(mc.player, vec3d, lookat, distance, maxDistance.get());
        //        DEBUG_RENDER_COLLISION_RENDERING = true;
        //        STATIC_DEBUG_COLOR = Color.RED;
        //        RenderTasks.debugBox(mc.player.dimensions.getBoxAt(lastAvailablePos));
        //        STATIC_DEBUG_COLOR = Color.YELLOW;
        //        collide(mc.player, lastAvailablePos.subtract(vec3d));
        //        DEBUG_RENDER_COLLISION_RENDERING = false;

        if (lastAvailablePos != vec3d) {
            if (useTpMethod.get()) {
                MovTasks.executeTp(lastAvailablePos, 2147483647, false, true);
            } else {
                MovTasks.farawayMoveTo(lastAvailablePos, true);
            }
            return true;
        } else {
            Debug.chat("no available position in front of you!");
            return true;
        }
    }

    public boolean quickMovTowardsWall() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        Vec3d vec3d = player.getPos();
        Vec3d lookat = player.getRotationVector().normalize();
        Vec3d lastAvailablePos = calculateNextWallPosition(mc.player, vec3d, lookat, distance, maxDistance.get());
        if (lastAvailablePos != vec3d) {
            if (useTpMethod.get()) {
                MovTasks.executeTp(lastAvailablePos, 2147483647, false, true);
            } else {
                MovTasks.farawayMoveTo(lastAvailablePos, true);
            }
            return true;
        } else {
            Debug.chat("no available position in front of you!");
            return true;
        }
    }

    public Vec3d calculateAvailableMovPlace(Entity executor, Vec3d curPose, Vec3d lookAt, double delta, double max) {
        //        double stepHeight = executor.getStepHeight();
        //        boolean onGround = executor.isOnGround();
        lookAt = lookAt.normalize();
        Vec3d maxinumMovement = lookAt.multiply(max);
        // fixme: when max too high , creating cache costs too much
        // fixme: should in lower case and higher case when searching i
        // fixme: most of case we move less than 100
        MovTasks.CollisionContext engin =
                new MovTasks.CollisionCache(executor, curPose, curPose.add(maxinumMovement), false);
        //        Box originalBox = executor.dimensions.getBoxAt(curPose);
        //        Box originalBox = executor.getBoundingBox();
        //        Box bigBox = makeCollectorBoxInvolvingCollision(originalBox, maxinumMovement, stepHeight, onGround);
        //        List<VoxelShape> involvedVoxel = new ArrayList<>();
        //        List<Box> involvedAABB = new ArrayList<>();
        //        CollisionUtil.getCollisions(
        //            executor.getWorld(), executor, bigBox, involvedVoxel, involvedAABB,
        //            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
        //            null, null, null
        //        );

        lookAt = lookAt.multiply(delta);
        Vec3d originalPos = curPose;
        Vec3d lastAvailablePos = curPose;
        boolean hasWall = false;
        for (double i = 0; i < max; i += delta) {
            curPose = curPose.add(lookAt);
            //            BlockPos pos1= BlockPos.ofFloored(vec3d);
            //            BlockPos pos2 = pos1.up();
            // check
            boolean noCollision = useTpMethod.get();
            boolean value;
            if (noCollision) {
                value = true;
            } else {
                Vec3d totalMovement = curPose.subtract(originalPos);
                Vec3d sim = engin.simulateMovement(
                        executor,
                        originalPos,
                        totalMovement); // collideWithTrustedList(originalBox, totalMovement, involvedVoxel,
                // involvedAABB, stepHeight, onGround);
                value = MovTasks.validMovementAsServer(totalMovement, sim);
            }

            boolean pass = value && !engin.checkEnvironmentCollision(executor, curPose, true);

            if (pass) {
                lastAvailablePos = curPose;
                if (hasWall) {
                    break;
                } else {
                    continue;
                }
            } else {
                hasWall = true;
            }
        }
        return lastAvailablePos;
    }

    public static Vec3d calculateNextWallPosition(
            Entity executor, Vec3d curPose, Vec3d lookAt, double delta, double max) {
        //        double stepHeight = executor.getStepHeight();
        //        boolean onGround = executor.isOnGround();
        lookAt = lookAt.normalize();
        Vec3d maxinumMovement = lookAt.multiply(max);
        //        Box originalBox = executor.getBoundingBox();
        //        Box bigBox = makeCollectorBoxInvolvingCollision(originalBox, maxinumMovement, stepHeight, onGround);
        //        List<VoxelShape> involvedVoxel = new ArrayList<>();
        //        List<Box> involvedAABB = new ArrayList<>();
        //        CollisionUtil.getCollisions(
        //            executor.getWorld(), executor, bigBox, involvedVoxel, involvedAABB,
        //            CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
        //            null, null, null
        //        );
        MovTasks.CollisionContext engin =
                new MovTasks.CollisionCache(executor, curPose, curPose.add(maxinumMovement), false);

        lookAt = lookAt.multiply(delta);
        Vec3d originalPos = curPose;
        Vec3d lastAvailablePos = curPose;
        for (double i = 0; i < max; i += delta) {
            curPose = curPose.add(lookAt);
            //            BlockPos pos1= BlockPos.ofFloored(vec3d);
            //            BlockPos pos2 = pos1.up();
            // check

            boolean value;

            Vec3d totalMovement = curPose.subtract(originalPos);
            Vec3d sim = engin.simulateMovement(
                    executor,
                    originalPos,
                    totalMovement); // collideWithTrustedList(originalBox, totalMovement, involvedVoxel, involvedAABB,
            // stepHeight, onGround);
            value = MovTasks.validMovementAsServer(totalMovement, sim);

            boolean pass = value && !engin.checkEnvironmentCollision(executor, curPose, true);

            if (pass) {
                lastAvailablePos = curPose;
            } else {
                break;
            }
        }
        return lastAvailablePos;
    }
}
