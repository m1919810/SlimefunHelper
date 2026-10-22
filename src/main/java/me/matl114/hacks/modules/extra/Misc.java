package me.matl114.hacks.modules.extra;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.utils.entity.EntityMovementStatus;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.PlayPackets;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Misc extends BaseModule {
    private record ParkourPlan(
            List<Vec3d> trajectory, double runupStartOffset, boolean feasible, int takeoffDelayTicks) {
        private ParkourPlan(List<Vec3d> trajectory, double runupStartOffset, boolean feasible) {
            this(trajectory, runupStartOffset, feasible, 0);
        }
    }

    private record RunupState(Vec3d takeoff, double speed, List<Vec3d> approach) {}

    private record LandingTrajectory(List<Vec3d> points, double landingAlong) {}

    private record LandingRange(double near, double far) {}

    private record TargetMovement(
            double deltaX,
            double deltaY,
            double deltaZ,
            boolean blockedX,
            boolean blockedY,
            boolean blockedZ,
            boolean landed) {}

    public Misc() {
        super("Misc");
    }

    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "misc");

    public final ModulePath friendsWine = root.add("friends_wine");
    public final ModulePath autoParkour = root.add("auto-parko");

    public final FlagRef friendsWineEnable =
            flagBuilder(friendsWine.addEnable()).build();

    public final FlagRef sneak = flagBuilder(friendsWine.add("sneak")).build();
    public final FlagRef autoParkourEnable =
            flagBuilder(autoParkour.addEnable()).build();
    public final IntRef parkourSearchDistance = intBuilder(autoParkour.add("search-distance"))
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .build();
    public final FlagRef parkourAutoStart =
            flagBuilder(autoParkour.add("auto-start")).defaultValue(false).build();
    public final FlagRef parkourYawControl =
            flagBuilder(autoParkour.add("yaw-control")).defaultValue(true).build();
    private final RenderCollector<Box> parkourTarget = RenderCollectors.createBoxCollector(true, false, false);
    private final RenderCollector<List<Vec3d>> parkourTrajectory = RenderCollectors.createLinesCollector();
    public final FlagRef parkourUsePacketQueue =
            flagBuilder(autoParkour.add("use-packet-queue")).defaultValue(false).build();

    private static final int PARKOUR_COLOR = Color.CYAN.getRGB();
    private static final int JUMP_COLOR = Color.GREEN.getRGB();
    private static final double PARKOUR_PLAYER_HALF_WIDTH = 0.3D;
    private static final double PARKOUR_LANDING_SUPPORT_OVERLAP = 0.01D;
    private static final double PARKOUR_GROUND_CHECK_DEPTH = 0.01D;
    private static final float PARKOUR_INPUT_SCALE = 0.98F;
    private static final float PARKOUR_GROUND_ACCELERATION_FACTOR = 0.21600002F;
    private static final float PARKOUR_MOVEMENT_FRICTION = 0.91F;
    private static final float PARKOUR_AIR_WALK_ACCELERATION = 0.02F;
    private static final float PARKOUR_AIR_SPRINT_ACCELERATION = 0.025999999F;
    private static final double PARKOUR_SPRINT_SPEED_MODIFIER = 0.30000001192092896D;
    private static final double PARKOUR_VERTICAL_FRICTION = 0.98D;
    private static final double PARKOUR_AIR_FRICTION = 0.91F;
    private boolean parkourJumping;
    private float parkourJumpYaw;
    private boolean parkourPacketQueueActive;
    private volatile boolean parkourPacketQueueing;
    private EntityMovementStatus<ClientPlayerEntity> parkourPacketStart;
    private Box parkourJumpTarget;
    private double parkourJumpMinimumY;
    private boolean parkourJumpTargetLadder;
    private boolean parkourAutoStartJump;
    private boolean parkourClimbingLadder;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
        registerListener(Listener.getPostGameTick(), this::updateParkour);
        registerListener(PacketManager.getPacketQueueOutEvent(), this::onParkourPacketQueue);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onParkourQueueShutdown);
        registerListener(RenderListener.getRender3DEvent(), this::renderParkour);
    }

    public void onPostTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
    }

    public void onPreGameTick() {
        if (checkNull()) return;
        int tick = Tasks.getTick() % 10;
        if (friendsWineEnable.get() && !mc.player.isFallFlying()) {
            if (sneak.get()) {
                PlayerInputManager.INSTANCE.addSneakModifier(0, tick <= 4, 1);
            }
            RotationManager.INSTANCE.request(
                    RotationManager.ofDirection(Optional.empty(), Optional.of(mc.player.getYaw() + 36 * tick))
                            .priority(-114514));
        }
    }

    private void updateParkour() {
        parkourTarget.clear();
        parkourTrajectory.clear();
        if (checkNull()) {
            parkourPacketQueueing = false;
            parkourPacketQueueActive = false;
            parkourPacketStart = null;
            parkourJumping = false;
            parkourJumpTarget = null;
            parkourJumpTargetLadder = false;
            parkourAutoStartJump = false;
            parkourClimbingLadder = false;
            return;
        }
        if (parkourClimbingLadder) {
            if (mc.player.isClimbing()) {
                applyParkourLadderClimbInput();
                return;
            }
            parkourClimbingLadder = false;
            return;
        }
        if (parkourJumping) {
            if (parkourJumpTargetLadder && mc.player.isClimbing()) {
                parkourClimbingLadder = true;
                parkourJumping = false;
                parkourAutoStartJump = false;
                parkourJumpTarget = null;
                parkourJumpTargetLadder = false;
                if (parkourPacketQueueActive) finishParkourPacketQueue(true);
                applyParkourLadderClimbInput();
                return;
            }
            if (parkourPacketQueueActive && !parkourJumpTargetLadder && isLandedOnParkourTarget()) {
                finishParkourPacketQueue(true);
            }
            if (mc.player.isOnGround() || mc.player.isClimbing()) {
                boolean climbingLadder = mc.player.isClimbing();
                boolean successfulLanding = !climbingLadder && isLandedOnParkourTarget();
                if (parkourPacketQueueActive) {
                    finishParkourPacketQueue(isLandedOnParkourTarget());
                }
                parkourJumping = false;
                parkourAutoStartJump = false;
                parkourJumpTarget = null;
                parkourJumpTargetLadder = false;
                if (climbingLadder) {
                    parkourClimbingLadder = true;
                    applyParkourLadderClimbInput();
                    return;
                }
                if (!successfulLanding) return;
            } else {
                PlayerInputManager.INSTANCE.addSprintModifier(Integer.MAX_VALUE, true, 1);
                PlayerInputManager.INSTANCE.addForwardModifier(Integer.MAX_VALUE, true, 1);
                if (parkourYawControl.get() && !parkourJumpTargetLadder) {
                    PlayerInputManager.INSTANCE.addYawModifier(Integer.MAX_VALUE, parkourJumpYaw, 1);
                }
                if (parkourAutoStartJump) {
                    applyParkourAutoStartInput(parkourJumpYaw, parkourYawControl.get() && !parkourJumpTargetLadder);
                }
                if (parkourJumpTargetLadder) {
                    PlayerInputManager.INSTANCE.addJumpModifier(Integer.MAX_VALUE, true, 1);
                }
                if (parkourPacketQueueActive
                        && mc.player.getVelocity().y < 0.0D
                        && mc.player.getY() < parkourJumpMinimumY) {
                    finishParkourPacketQueue(false);
                    parkourJumping = false;
                    parkourAutoStartJump = false;
                    parkourJumpTarget = null;
                    parkourJumpTargetLadder = false;
                }
                return;
            }
        }
        if (!autoParkourEnable.get() || mc.player.isFallFlying()) return;

        double yaw = Math.toRadians(mc.player.getYaw());
        double directionX = -Math.sin(yaw);
        double directionZ = Math.cos(yaw);
        double directionLength = Math.hypot(directionX, directionZ);
        directionX /= directionLength;
        directionZ /= directionLength;
        BlockPos support = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.01, mc.player.getZ());
        BlockPos target = findGapTarget(support, directionX, directionZ);
        if (target == null) return;
        BlockState targetState = mc.world.getBlockState(target);
        boolean ladderTarget = targetState.getBlock() instanceof LadderBlock;
        var shape = targetState.getCollisionShape(mc.world, target);
        if (shape.isEmpty()) return;
        Box targetBox = shape.getBoundingBox().offset(target);
        Box trajectoryTargetBox = ladderTarget
                ? new Box(
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        target.getX() + 1.0D,
                        target.getY() + 1.0D,
                        target.getZ() + 1.0D)
                : targetBox;
        boolean controlTargetYaw = parkourYawControl.get() && !ladderTarget;
        parkourJumpYaw = controlTargetYaw ? getParkourTargetYaw(targetBox) : mc.player.getYaw();
        if (controlTargetYaw) {
            PlayerInputManager.INSTANCE.addYawModifier(Integer.MAX_VALUE, parkourJumpYaw, 1);
        }
        double targetYaw = Math.toRadians(parkourJumpYaw);
        directionX = -Math.sin(targetYaw);
        directionZ = Math.cos(targetYaw);
        if (mc.options.forwardKey.isPressed()) {
            PlayerInputManager.INSTANCE.addSprintModifier(0, true, 1);
        }

        parkourTarget.submit(targetBox, PARKOUR_COLOR);
        ParkourPlan plan = findFutureJumpTrajectory(trajectoryTargetBox, directionX, directionZ);
        boolean simulatedTrajectory = plan != null;
        if (!simulatedTrajectory) {
            plan = findJumpTrajectory(trajectoryTargetBox, directionX, directionZ);
        }
        int color = simulatedTrajectory ? JUMP_COLOR : Color.RED.getRGB();
        parkourTrajectory.submit(plan.trajectory(), color);
        boolean autoStartActive = simulatedTrajectory
                && ((parkourAutoStart.get() || mc.options.forwardKey.isPressed()))
                && !mc.options.sneakKey.isPressed();
        if (autoStartActive) applyParkourAutoStartInput(parkourJumpYaw, controlTargetYaw);
        boolean canStartCurrentJump = simulatedTrajectory
                && plan.takeoffDelayTicks() == 0
                && canJumpReach(trajectoryTargetBox, directionX, directionZ);
        boolean shouldJump = !parkourJumping
                && mc.player.isOnGround()
                && (mc.options.forwardKey.isPressed() || autoStartActive)
                && canStartCurrentJump;
        if (shouldJump) {
            parkourJumpMinimumY = getParkourMinimumY(trajectoryTargetBox);
            PlayerInputManager.INSTANCE.addJumpModifier(0, true, 1);
            parkourJumping = true;
            parkourAutoStartJump = autoStartActive;
            parkourJumpTarget = trajectoryTargetBox;
            parkourJumpTargetLadder = ladderTarget;
            if (parkourUsePacketQueue.get()) startParkourPacketQueue();
        }
    }

    private void applyParkourLadderClimbInput() {
        PlayerInputManager.INSTANCE.addForwardModifier(Integer.MAX_VALUE, true, 1);
        PlayerInputManager.INSTANCE.addJumpModifier(Integer.MAX_VALUE, true, 1);
    }

    private void applyParkourAutoStartInput(float yaw, boolean controlYaw) {
        PlayerInputManager.Modifier modifier = PlayerInputManager.Modifier.empty(Integer.MAX_VALUE)
                .forward(true)
                .backward(false)
                .left(false)
                .right(false)
                .sneak(false)
                .sprint(true);
        if (controlYaw) modifier = modifier.yaw(yaw);
        PlayerInputManager.INSTANCE.addInputModifier(modifier, 1);
    }

    private float getParkourTargetYaw(Box targetBox) {
        double nearestX = Math.max(targetBox.minX, Math.min(mc.player.getX(), targetBox.maxX));
        double nearestZ = Math.max(targetBox.minZ, Math.min(mc.player.getZ(), targetBox.maxZ));
        double deltaX = nearestX - mc.player.getX();
        double deltaZ = nearestZ - mc.player.getZ();
        if (deltaX * deltaX + deltaZ * deltaZ <= 1.0E-8D) return mc.player.getYaw();
        return (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
    }

    private void startParkourPacketQueue() {
        parkourPacketStart = new EntityMovementStatus<>(mc.player);
        parkourPacketQueueActive = true;
        PacketManager.schedule(() -> PacketManager.flushOutBound(() -> parkourPacketQueueing = true));
    }

    private void finishParkourPacketQueue(boolean success) {
        parkourPacketQueueActive = false;
        EntityMovementStatus<ClientPlayerEntity> start = parkourPacketStart;
        parkourPacketStart = null;
        Runnable afterFlush = () -> parkourPacketQueueing = false;
        if (success) {
            PacketManager.schedule(() -> PacketManager.flushOutBound(afterFlush));
        } else {
            PacketManager.schedule(() -> PacketManager.flushOutBound(this::parkourRevertPacketAction, afterFlush));
            logI18N("message.module.misc.parkour-jump-failed");
            if (start != null) {
                start.restore();
                ClientPlayerAccess.of(mc.player).resyncInput();
            }
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
            PlayerInputManager.INSTANCE.addSneakModifier(Integer.MAX_VALUE, true, 1, 5);
            PlayerInputManager.INSTANCE.addBackwardModifier(Integer.MAX_VALUE, true, 1, 4);
        }
    }

    private PacketManager.FlushAction parkourRevertPacketAction(PacketStorage packet) {
        var packetType = packet.packetType();
        if (packetType == PlayPackets.MOVE_PLAYER_POS
                || packetType == PlayPackets.MOVE_PLAYER_ROT
                || packetType == PlayPackets.MOVE_PLAYER_POS_ROT
                || packetType == PlayPackets.MOVE_PLAYER_STATUS_ONLY
                || packetType == PlayPackets.PLAYER_INPUT) {
            return PacketManager.FlushAction.DROP;
        }
        return PacketManager.FlushAction.FLUSH;
    }

    private void onParkourPacketQueue(Event<PacketStorage.Send> event) {
        if (!parkourPacketQueueing) return;
        if (PacketManager.isAsyncOrNotTransactionC2SPacket(event.context.packetType())) return;
        if (event.context.packetType() == PlayPackets.ACCEPT_TELEPORTATION) {
            PacketManager.schedule(() -> PacketManager.flushOutBound(() -> parkourPacketQueueing = false));
            parkourPacketQueueActive = false;
            parkourPacketStart = null;
            return;
        }
        event.cancel();
    }

    private void onParkourQueueShutdown() {
        parkourPacketQueueing = false;
        parkourPacketQueueActive = false;
        parkourPacketStart = null;
    }

    private boolean isLandedOnParkourTarget() {
        if (parkourJumpTargetLadder) {
            return parkourJumpTarget != null && mc.player.isClimbing();
        }
        return parkourJumpTarget != null
                && Math.abs(mc.player.getY() - parkourJumpTarget.maxY) < 0.08D
                && hasLandingSupport(mc.player.getPos(), parkourJumpTarget);
    }

    private boolean isNearPlayerHorizontalNeighbor(BlockPos candidate, BlockPos playerBlock) {
        return Math.abs(candidate.getX() - playerBlock.getX()) <= 1
                && Math.abs(candidate.getZ() - playerBlock.getZ()) <= 1;
    }

    private BlockPos findGapTarget(BlockPos support, double directionX, double directionZ) {
        boolean gapSeen = false;
        Vec3d rayStart = new Vec3d(mc.player.getX(), support.getY() + 0.5, mc.player.getZ());
        double directionScale = Math.max(Math.abs(directionX), Math.abs(directionZ));
        double searchDistance = parkourSearchDistance.get() / directionScale;
        Vec3d rayEnd = rayStart.add(directionX * searchDistance, 0.0, directionZ * searchDistance);
        BlockPos playerBlock = mc.player.getBlockPos();
        List<BlockPos> projectedPositions = new ArrayList<>();
        for (BlockPos projected : RaycastUtils.createRaycastBlockPoses(rayStart, rayEnd)) {
            if (projected.equals(support)) continue;
            BlockState projectedState = mc.world.getBlockState(projected);
            var projectedShape = projectedState.getCollisionShape(mc.world, projected);
            if (!gapSeen) {
                if (!projectedShape.isEmpty()) continue;
                gapSeen = true;
                continue;
            }
            projectedPositions.add(projected);
        }

        for (int y = support.getY() + 1; y >= support.getY() - 2; y--) {
            for (BlockPos projected : projectedPositions) {
                BlockPos candidate = projected.withY(y);
                if (isNearPlayerHorizontalNeighbor(candidate, playerBlock)) continue;
                BlockState state = mc.world.getBlockState(candidate);
                boolean ladder = state.getBlock() instanceof LadderBlock;
                var shape = state.getCollisionShape(mc.world, candidate);
                if (shape.isEmpty()) continue;
                Box bounds = shape.getBoundingBox();
                double top = candidate.getY() + bounds.maxY;
                Box playerSpace = mc.player
                        .getBoundingBox()
                        .offset(
                                candidate.getX() + 0.5 - mc.player.getX(),
                                top - mc.player.getY(),
                                candidate.getZ() + 0.5 - mc.player.getZ());
                if (!mc.world.isSpaceEmpty(playerSpace)) continue;
                return ladder ? findBottomLadder(candidate) : candidate;
            }
        }
        return null;
    }

    private BlockPos findBottomLadder(BlockPos ladder) {
        BlockPos bottomLadder = ladder;
        while (bottomLadder.getY() > mc.world.getBottomY()) {
            BlockPos below = bottomLadder.down();
            if (!(mc.world.getBlockState(below).getBlock() instanceof LadderBlock)) break;
            bottomLadder = below;
        }
        return bottomLadder;
    }

    private ParkourPlan findJumpTrajectory(Box targetBox, double directionX, double directionZ) {
        Vec3d currentPosition = mc.player.getPos();
        ParkourPlan newPlan = findNewJumpSpot(currentPosition, 0.0D, directionX, directionZ, targetBox);
        if (newPlan != null) return newPlan;
        ParkourPlan oldPlan = findOldJumpSpot(currentPosition, directionX, directionZ, targetBox);
        if (oldPlan != null) return oldPlan;

        Vec3d velocity = mc.player.getVelocity();
        LandingTrajectory currentJump = simulateJump(
                currentPosition, velocity.x, velocity.z, mc.player.isSprinting(), directionX, directionZ, targetBox);
        if (currentJump != null) {
            Vec3d landing = currentJump.points().get(currentJump.points().size() - 1);
            if (hasLandingSupport(landing, targetBox)) {
                return new ParkourPlan(currentJump.points(), 0.0D, true);
            }
        }
        return findFlatGroundFallbackPlan(currentPosition, directionX, directionZ, targetBox);
    }

    private ParkourPlan findNewJumpSpot(
            Vec3d runupStart, double runupStartOffset, double directionX, double directionZ, Box targetBox) {
        RunupState fullRunup = simulateGroundedRunup(runupStart, directionX, directionZ);
        if (fullRunup == null) return null;
        double maxRunupDistance = (fullRunup.takeoff().x - runupStart.x) * directionX
                + (fullRunup.takeoff().z - runupStart.z) * directionZ;
        LandingRange landingRange = landingRange(targetBox, runupStart, directionX, directionZ);
        if (landingRange == null) return null;
        RunupState candidateState = simulateRunupTo(runupStart, 0.0D, directionX, directionZ);
        LandingTrajectory candidateLanding =
                simulateJump(candidateState.takeoff(), candidateState.speed(), directionX, directionZ, targetBox);
        if (candidateLanding == null) return null;
        if (candidateLanding.landingAlong() < landingRange.near()) {
            LandingTrajectory fullSpeedLanding =
                    simulateJump(fullRunup.takeoff(), fullRunup.speed(), directionX, directionZ, targetBox);
            if (fullSpeedLanding == null || fullSpeedLanding.landingAlong() < landingRange.near()) return null;

            double low = 0.0D;
            double high = maxRunupDistance;
            candidateState = fullRunup;
            candidateLanding = fullSpeedLanding;
            while (high - low > 0.005D) {
                double middle = (low + high) * 0.5D;
                RunupState middleState = simulateRunupTo(runupStart, middle, directionX, directionZ);
                if (!hasGroundAt(middleState.takeoff())) {
                    high = middle;
                    continue;
                }
                LandingTrajectory middleLanding =
                        simulateJump(middleState.takeoff(), middleState.speed(), directionX, directionZ, targetBox);
                if (middleLanding == null) return null;
                if (middleLanding.landingAlong() >= landingRange.near()) {
                    high = middle;
                    candidateState = middleState;
                    candidateLanding = middleLanding;
                } else {
                    low = middle;
                }
            }
        }
        if (!hasGroundAt(candidateState.takeoff())) return null;
        if (candidateLanding.landingAlong() > landingRange.far()) return null;
        Vec3d landingPoint =
                candidateLanding.points().get(candidateLanding.points().size() - 1);
        if (!hasLandingSupport(landingPoint, targetBox)) return null;

        List<Vec3d> trajectory = new ArrayList<>(candidateState.approach());
        trajectory.addAll(
                candidateLanding.points().subList(1, candidateLanding.points().size()));
        return new ParkourPlan(trajectory, runupStartOffset, true);
    }

    private ParkourPlan findOldJumpSpot(Vec3d currentPosition, double directionX, double directionZ, Box targetBox) {
        LandingRange landingRange = landingRange(targetBox, currentPosition, directionX, directionZ);
        if (landingRange == null) return null;

        RunupState fullRunup = simulateFullRunup(currentPosition, directionX, directionZ);
        LandingTrajectory jumpFromCurrent =
                simulateJump(currentPosition, fullRunup.speed(), directionX, directionZ, targetBox);
        if (jumpFromCurrent == null) return null;

        double currentAlong = currentPosition.x * directionX + currentPosition.z * directionZ;
        double jumpDistance = jumpFromCurrent.landingAlong() - currentAlong;
        int landingSamples = Math.max(1, (int) Math.ceil((landingRange.far() - landingRange.near()) / 0.01D));
        for (int sample = 0; sample <= landingSamples; sample++) {
            double landingAlong =
                    landingRange.near() + (landingRange.far() - landingRange.near()) * sample / landingSamples;
            double takeoffAlong = landingAlong - jumpDistance;
            Vec3d takeoff = currentPosition.add(
                    directionX * (takeoffAlong - currentAlong), 0.0D, directionZ * (takeoffAlong - currentAlong));
            if (!hasGroundAt(takeoff)) continue;

            RunupState reversedRunup = reverseFullRunup(takeoff, directionX, directionZ);
            LandingTrajectory jump = translateTrajectory(
                    jumpFromCurrent,
                    takeoff.x - currentPosition.x,
                    takeoff.z - currentPosition.z,
                    directionX,
                    directionZ);
            Vec3d actualLanding = jump.points().get(jump.points().size() - 1);
            if (!hasLandingSupport(actualLanding, targetBox)) continue;

            double runupStartOffset = (reversedRunup.approach().get(0).x - currentPosition.x) * directionX
                    + (reversedRunup.approach().get(0).z - currentPosition.z) * directionZ;
            List<Vec3d> trajectory = new ArrayList<>(reversedRunup.approach());
            trajectory.addAll(jump.points().subList(1, jump.points().size()));
            return new ParkourPlan(trajectory, runupStartOffset, true);
        }
        return null;
    }

    private RunupState simulateFullRunup(Vec3d runupStart, double directionX, double directionZ) {
        Vec3d position = runupStart;
        double speed = 0.0D;
        List<Vec3d> approach = new ArrayList<>();
        approach.add(position);
        for (int tick = 0; tick < 40; tick++) {
            double movementSpeed = speed + getParkourGroundAcceleration(true, position);
            float horizontalFriction = getParkourGroundHorizontalFriction(position);
            position = position.add(directionX * movementSpeed, 0.0D, directionZ * movementSpeed);
            approach.add(position);
            speed = movementSpeed * horizontalFriction;
        }
        return new RunupState(position, speed, approach);
    }

    private RunupState simulateGroundedRunup(Vec3d runupStart, double directionX, double directionZ) {
        if (!hasGroundAt(runupStart)) return null;

        Vec3d position = runupStart;
        double speed = 0.0D;
        List<Vec3d> approach = new ArrayList<>();
        approach.add(position);
        for (int tick = 0; tick < 40; tick++) {
            double movementSpeed = speed + getParkourGroundAcceleration(true, position);
            float horizontalFriction = getParkourGroundHorizontalFriction(position);
            Vec3d next = position.add(directionX * movementSpeed, 0.0D, directionZ * movementSpeed);
            if (!hasGroundAlong(position, next)) break;
            position = next;
            approach.add(position);
            speed = movementSpeed * horizontalFriction;
        }
        return new RunupState(position, speed, approach);
    }

    private boolean hasGroundAlong(Vec3d start, Vec3d end) {
        double distance = Math.hypot(end.x - start.x, end.z - start.z);
        int samples = Math.max(1, (int) Math.ceil(distance / 0.1D));
        for (int sample = 1; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            Vec3d position = start.lerp(end, progress);
            if (!hasGroundAt(position)) return false;
        }
        return true;
    }

    private boolean hasGroundAt(Vec3d position) {
        Vec3d currentPosition = mc.player.getPos();
        Box playerBox = mc.player
                .getBoundingBox()
                .offset(
                        position.x - currentPosition.x,
                        position.y - currentPosition.y - PARKOUR_GROUND_CHECK_DEPTH,
                        position.z - currentPosition.z);
        int groundY = BlockPos.ofFloored(position.x, position.y - PARKOUR_GROUND_CHECK_DEPTH, position.z)
                .getY();
        int minBlockX =
                BlockPos.ofFloored(playerBox.minX, position.y, position.z).getX();
        int maxBlockX =
                BlockPos.ofFloored(playerBox.maxX, position.y, position.z).getX();
        int minBlockZ =
                BlockPos.ofFloored(position.x, position.y, playerBox.minZ).getZ();
        int maxBlockZ =
                BlockPos.ofFloored(position.x, position.y, playerBox.maxZ).getZ();

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                BlockPos ground = new BlockPos(x, groundY, z);
                var shape = mc.world.getBlockState(ground).getCollisionShape(mc.world, ground);
                for (Box collisionBox : shape.getBoundingBoxes()) {
                    if (playerBox.intersects(collisionBox.offset(ground))) return true;
                }
            }
        }
        return false;
    }

    private RunupState reverseFullRunup(Vec3d takeoff, double directionX, double directionZ) {
        List<Double> movementSteps = new ArrayList<>();
        double speed = 0.0D;
        double groundAcceleration = getParkourGroundAcceleration(true, takeoff);
        float horizontalFriction = getParkourGroundHorizontalFriction(takeoff);
        for (int tick = 0; tick < 40; tick++) {
            double movementSpeed = speed + groundAcceleration;
            movementSteps.add(movementSpeed);
            speed = movementSpeed * horizontalFriction;
        }

        List<Vec3d> reversedApproach = new ArrayList<>();
        Vec3d position = takeoff;
        reversedApproach.add(position);
        for (int tick = movementSteps.size() - 1; tick >= 0; tick--) {
            double movementSpeed = movementSteps.get(tick);
            position = position.subtract(directionX * movementSpeed, 0.0D, directionZ * movementSpeed);
            reversedApproach.add(position);
        }
        java.util.Collections.reverse(reversedApproach);
        return new RunupState(takeoff, speed, reversedApproach);
    }

    private LandingTrajectory translateTrajectory(
            LandingTrajectory trajectory, double offsetX, double offsetZ, double directionX, double directionZ) {
        List<Vec3d> points = trajectory.points().stream()
                .map(point -> point.add(offsetX, 0.0D, offsetZ))
                .toList();
        return new LandingTrajectory(points, trajectory.landingAlong() + offsetX * directionX + offsetZ * directionZ);
    }

    private RunupState simulateRunupTo(Vec3d runupStart, double distance, double directionX, double directionZ) {
        Vec3d position = runupStart;
        double speed = 0.0D;
        List<Vec3d> approach = new ArrayList<>();
        approach.add(position);
        double travelled = 0.0D;
        for (int tick = 0; tick < 40 && travelled < distance; tick++) {
            double movementSpeed = speed + getParkourGroundAcceleration(true, position);
            double step = Math.min(movementSpeed, distance - travelled);
            float horizontalFriction = getParkourGroundHorizontalFriction(position);
            position = position.add(directionX * step, 0.0D, directionZ * step);
            travelled += step;
            approach.add(position);
            speed = step * horizontalFriction;
        }
        return new RunupState(position, speed, approach);
    }

    private LandingTrajectory simulateJump(
            Vec3d takeoff, double initialSpeed, double directionX, double directionZ, Box targetBox) {
        return simulateJump(
                takeoff, directionX * initialSpeed, directionZ * initialSpeed, true, directionX, directionZ, targetBox);
    }

    private LandingTrajectory simulateJump(
            Vec3d takeoff,
            double initialVelocityX,
            double initialVelocityZ,
            boolean sprinting,
            double directionX,
            double directionZ,
            Box targetBox) {
        List<Vec3d> points = new ArrayList<>();
        double velocityX = initialVelocityX;
        double velocityZ = initialVelocityZ;
        if (sprinting) {
            velocityX += directionX * 0.2D;
            velocityZ += directionZ * 0.2D;
        }
        double y = takeoff.y;
        double vy = Math.max(
                mc.player.getVelocity().y, LivingEntityAccess.of(mc.player).getJumpUpwardSpeed(1.0F));
        double x = takeoff.x;
        double z = takeoff.z;
        boolean ladderTarget = isLadderTarget(targetBox);
        List<Box> targetCollisionBoxes = ladderTarget ? List.of() : getParkourTargetCollisionBoxes(targetBox);
        points.add(new Vec3d(x, y, z));
        for (int tick = 0; tick < 40; tick++) {
            double inputAcceleration = tick == 0
                    ? getParkourGroundAcceleration(sprinting, new Vec3d(x, y, z))
                    : getParkourAirAcceleration(sprinting);
            double movementX = velocityX + directionX * inputAcceleration;
            double movementY = vy;
            double movementZ = velocityZ + directionZ * inputAcceleration;
            Vec3d position = new Vec3d(x, y, z);
            List<Box> collisionBoxes = ladderTarget
                    ? getParkourWorldCollisionBoxes(position, movementX, movementY, movementZ)
                    : targetCollisionBoxes;
            TargetMovement targetMovement =
                    moveAgainstTarget(position, movementX, movementY, movementZ, collisionBoxes);
            x += targetMovement.deltaX();
            y += targetMovement.deltaY();
            z += targetMovement.deltaZ();
            double horizontalFriction = tick == 0 ? getParkourGroundHorizontalFriction(takeoff) : PARKOUR_AIR_FRICTION;
            velocityX = (targetMovement.blockedX() ? 0.0D : targetMovement.deltaX()) * horizontalFriction;
            velocityZ = (targetMovement.blockedZ() ? 0.0D : targetMovement.deltaZ()) * horizontalFriction;
            Vec3d point = new Vec3d(x, y, z);
            if (!hasClearPlayerSpace(point)) {
                return null;
            }
            if (ladderTarget) {
                points.add(point);
                if (isLadderClimbPosition(point, targetBox)) {
                    return new LandingTrajectory(points, x * directionX + z * directionZ);
                }
            } else {
                points.add(point);
                if (targetMovement.landed()) {
                    return new LandingTrajectory(points, x * directionX + z * directionZ);
                }
            }
            double verticalVelocity = targetMovement.blockedY() ? 0.0D : movementY;
            vy = (verticalVelocity - mc.player.getFinalGravity()) * PARKOUR_VERTICAL_FRICTION;
        }
        return null;
    }

    private List<Box> getParkourTargetCollisionBoxes(Box targetBox) {
        BlockPos target = BlockPos.ofFloored(targetBox.minX, targetBox.minY, targetBox.minZ);
        return mc.world.getBlockState(target).getCollisionShape(mc.world, target).getBoundingBoxes().stream()
                .map(box -> box.offset(target))
                .toList();
    }

    private TargetMovement moveAgainstTarget(
            Vec3d position, double movementX, double movementY, double movementZ, List<Box> collisionBoxes) {
        if (collisionBoxes.isEmpty()) {
            return new TargetMovement(movementX, movementY, movementZ, false, false, false, false);
        }

        Box playerBox = new Box(
                position.x - PARKOUR_PLAYER_HALF_WIDTH,
                position.y,
                position.z - PARKOUR_PLAYER_HALF_WIDTH,
                position.x + PARKOUR_PLAYER_HALF_WIDTH,
                position.y + mc.player.getHeight(),
                position.z + PARKOUR_PLAYER_HALF_WIDTH);
        // Resolve vertical movement first, matching the player's collision order.
        double deltaY = movementY;
        boolean blockedY = false;
        boolean landed = false;
        for (Box collisionBox : collisionBoxes) {
            boolean overlapsHorizontally = movementY < 0.0D
                    ? hasMinimumOverlap(
                                    playerBox.minX,
                                    playerBox.maxX,
                                    collisionBox.minX,
                                    collisionBox.maxX,
                                    PARKOUR_LANDING_SUPPORT_OVERLAP)
                            && hasMinimumOverlap(
                                    playerBox.minZ,
                                    playerBox.maxZ,
                                    collisionBox.minZ,
                                    collisionBox.maxZ,
                                    PARKOUR_LANDING_SUPPORT_OVERLAP)
                    : intervalsOverlap(playerBox.minX, playerBox.maxX, collisionBox.minX, collisionBox.maxX)
                            && intervalsOverlap(playerBox.minZ, playerBox.maxZ, collisionBox.minZ, collisionBox.maxZ);
            if (!overlapsHorizontally) {
                continue;
            }
            if (movementY < 0.0D
                    && playerBox.minY >= collisionBox.maxY
                    && playerBox.minY + deltaY < collisionBox.maxY) {
                deltaY = Math.max(deltaY, collisionBox.maxY - playerBox.minY);
                blockedY = true;
                landed = true;
            } else if (movementY > 0.0D
                    && playerBox.maxY <= collisionBox.minY
                    && playerBox.maxY + deltaY > collisionBox.minY) {
                deltaY = Math.min(deltaY, collisionBox.minY - playerBox.maxY);
                blockedY = true;
            }
        }

        playerBox = playerBox.offset(0.0D, deltaY, 0.0D);
        double deltaX = movementX;
        boolean blockedX = false;
        for (Box collisionBox : collisionBoxes) {
            if (!intervalsOverlap(playerBox.minY, playerBox.maxY, collisionBox.minY, collisionBox.maxY)
                    || !intervalsOverlap(playerBox.minZ, playerBox.maxZ, collisionBox.minZ, collisionBox.maxZ)) {
                continue;
            }
            if (movementX > 0.0D
                    && playerBox.maxX <= collisionBox.minX
                    && playerBox.maxX + deltaX > collisionBox.minX) {
                deltaX = Math.min(deltaX, collisionBox.minX - playerBox.maxX);
                blockedX = true;
            } else if (movementX < 0.0D
                    && playerBox.minX >= collisionBox.maxX
                    && playerBox.minX + deltaX < collisionBox.maxX) {
                deltaX = Math.max(deltaX, collisionBox.maxX - playerBox.minX);
                blockedX = true;
            }
        }

        playerBox = playerBox.offset(deltaX, 0.0D, 0.0D);
        double deltaZ = movementZ;
        boolean blockedZ = false;
        for (Box collisionBox : collisionBoxes) {
            if (!intervalsOverlap(playerBox.minY, playerBox.maxY, collisionBox.minY, collisionBox.maxY)
                    || !intervalsOverlap(playerBox.minX, playerBox.maxX, collisionBox.minX, collisionBox.maxX)) {
                continue;
            }
            if (movementZ > 0.0D
                    && playerBox.maxZ <= collisionBox.minZ
                    && playerBox.maxZ + deltaZ > collisionBox.minZ) {
                deltaZ = Math.min(deltaZ, collisionBox.minZ - playerBox.maxZ);
                blockedZ = true;
            } else if (movementZ < 0.0D
                    && playerBox.minZ >= collisionBox.maxZ
                    && playerBox.minZ + deltaZ < collisionBox.maxZ) {
                deltaZ = Math.max(deltaZ, collisionBox.maxZ - playerBox.minZ);
                blockedZ = true;
            }
        }
        return new TargetMovement(deltaX, deltaY, deltaZ, blockedX, blockedY, blockedZ, landed);
    }

    private boolean hasClearPlayerSpace(Vec3d position) {
        Vec3d currentPosition = mc.player.getPos();
        Box playerBox = mc.player
                .getBoundingBox()
                .offset(position.x - currentPosition.x, position.y - currentPosition.y, position.z - currentPosition.z);
        return mc.world.isSpaceEmpty(mc.player, playerBox);
    }

    private List<Box> getParkourWorldCollisionBoxes(
            Vec3d position, double movementX, double movementY, double movementZ) {
        Vec3d currentPosition = mc.player.getPos();
        Box playerBox = mc.player
                .getBoundingBox()
                .offset(position.x - currentPosition.x, position.y - currentPosition.y, position.z - currentPosition.z);
        Box destinationBox = playerBox.offset(movementX, movementY, movementZ);
        Box sweptBox = new Box(
                Math.min(playerBox.minX, destinationBox.minX),
                Math.min(playerBox.minY, destinationBox.minY),
                Math.min(playerBox.minZ, destinationBox.minZ),
                Math.max(playerBox.maxX, destinationBox.maxX),
                Math.max(playerBox.maxY, destinationBox.maxY),
                Math.max(playerBox.maxZ, destinationBox.maxZ));
        List<Box> collisionBoxes = new ArrayList<>();
        for (var collisionShape : mc.world.getBlockCollisions(mc.player, sweptBox)) {
            collisionBoxes.addAll(collisionShape.getBoundingBoxes());
        }
        return collisionBoxes;
    }

    private boolean intervalsOverlap(double minA, double maxA, double minB, double maxB) {
        return maxA > minB && minA < maxB;
    }

    private boolean hasMinimumOverlap(double minA, double maxA, double minB, double maxB, double minimumOverlap) {
        return Math.min(maxA, maxB) - Math.max(minA, minB) >= minimumOverlap;
    }

    private boolean canJumpReach(Box targetBox, double directionX, double directionZ) {
        // Current velocity already includes the run-up acceleration from previous ticks.
        Vec3d velocity = mc.player.getVelocity();
        LandingTrajectory trajectory =
                simulateJump(mc.player.getPos(), velocity.x, velocity.z, true, directionX, directionZ, targetBox);
        if (trajectory == null) return false;
        Vec3d landing = trajectory.points().get(trajectory.points().size() - 1);
        return hasLandingSupport(landing, targetBox);
    }

    private double getParkourGroundAcceleration(boolean sprinting, Vec3d position) {
        float movementSpeed = mc.player.getMovementSpeed();
        if (sprinting != mc.player.isSprinting()) {
            double sprintMultiplier = 1.0D + PARKOUR_SPRINT_SPEED_MODIFIER;
            movementSpeed = (float) (movementSpeed * (sprinting ? sprintMultiplier : 1.0D / sprintMultiplier));
        }
        float slipperiness = getParkourGroundSlipperiness(position);
        float frictionSquared = slipperiness * slipperiness;
        float acceleration = movementSpeed * PARKOUR_GROUND_ACCELERATION_FACTOR / (frictionSquared * slipperiness);
        return (double) (acceleration * PARKOUR_INPUT_SCALE);
    }

    private double getParkourAirAcceleration(boolean sprinting) {
        float acceleration = sprinting ? PARKOUR_AIR_SPRINT_ACCELERATION : PARKOUR_AIR_WALK_ACCELERATION;
        return (double) (acceleration * PARKOUR_INPUT_SCALE);
    }

    private float getParkourGroundSlipperiness(Vec3d position) {
        BlockPos below = BlockPos.ofFloored(position.x, position.y - 0.500001D, position.z);
        return mc.world.getBlockState(below).getBlock().getSlipperiness();
    }

    private float getParkourGroundHorizontalFriction(Vec3d position) {
        return getParkourGroundSlipperiness(position) * PARKOUR_MOVEMENT_FRICTION;
    }

    private LandingRange landingRange(Box targetBox, Vec3d runupStart, double directionX, double directionZ) {
        double landingMargin =
                isLadderTarget(targetBox) ? 0.0D : PARKOUR_PLAYER_HALF_WIDTH - PARKOUR_LANDING_SUPPORT_OVERLAP;
        double minX = targetBox.minX - landingMargin;
        double maxX = targetBox.maxX + landingMargin;
        double minZ = targetBox.minZ - landingMargin;
        double maxZ = targetBox.maxZ + landingMargin;
        if (minX > maxX || minZ > maxZ) return null;

        double nearDistance = Double.NEGATIVE_INFINITY;
        double farDistance = Double.POSITIVE_INFINITY;
        if (Math.abs(directionX) < 1.0E-9D) {
            if (runupStart.x < minX || runupStart.x > maxX) return null;
        } else {
            double first = (minX - runupStart.x) / directionX;
            double second = (maxX - runupStart.x) / directionX;
            nearDistance = Math.max(nearDistance, Math.min(first, second));
            farDistance = Math.min(farDistance, Math.max(first, second));
        }
        if (Math.abs(directionZ) < 1.0E-9D) {
            if (runupStart.z < minZ || runupStart.z > maxZ) return null;
        } else {
            double first = (minZ - runupStart.z) / directionZ;
            double second = (maxZ - runupStart.z) / directionZ;
            nearDistance = Math.max(nearDistance, Math.min(first, second));
            farDistance = Math.min(farDistance, Math.max(first, second));
        }
        if (nearDistance > farDistance) return null;

        double startAlong = runupStart.x * directionX + runupStart.z * directionZ;
        return new LandingRange(startAlong + nearDistance, startAlong + farDistance);
    }

    private boolean hasLandingSupport(Vec3d landing, Box targetBox) {
        if (isLadderTarget(targetBox)) return isLadderClimbPosition(landing, targetBox);
        return landing.x >= targetBox.minX - PARKOUR_PLAYER_HALF_WIDTH + PARKOUR_LANDING_SUPPORT_OVERLAP
                && landing.x <= targetBox.maxX + PARKOUR_PLAYER_HALF_WIDTH - PARKOUR_LANDING_SUPPORT_OVERLAP
                && landing.z >= targetBox.minZ - PARKOUR_PLAYER_HALF_WIDTH + PARKOUR_LANDING_SUPPORT_OVERLAP
                && landing.z <= targetBox.maxZ + PARKOUR_PLAYER_HALF_WIDTH - PARKOUR_LANDING_SUPPORT_OVERLAP;
    }

    private boolean isLadderTarget(Box targetBox) {
        BlockPos target = BlockPos.ofFloored(targetBox.minX, targetBox.minY, targetBox.minZ);
        return mc.world.getBlockState(target).getBlock() instanceof LadderBlock;
    }

    private double getParkourTargetPlaneY(Box targetBox) {
        BlockPos target = BlockPos.ofFloored(targetBox.minX, targetBox.minY, targetBox.minZ);
        return target.getY() + (isLadderTarget(targetBox) ? 0.0D : 1.0D);
    }

    private double getParkourMinimumY(Box targetBox) {
        if (!isLadderTarget(targetBox)) return targetBox.maxY;

        BlockPos bottomLadder = BlockPos.ofFloored(targetBox.minX, targetBox.minY, targetBox.minZ);
        while (bottomLadder.getY() > mc.world.getBottomY()) {
            BlockPos below = bottomLadder.down();
            if (!(mc.world.getBlockState(below).getBlock() instanceof LadderBlock)) break;
            bottomLadder = below;
        }
        return bottomLadder.getY() - 2.0D;
    }

    private boolean isLadderClimbPosition(Vec3d position, Box targetBox) {
        BlockPos target = BlockPos.ofFloored(targetBox.minX, targetBox.minY, targetBox.minZ);
        BlockPos playerFeet = BlockPos.ofFloored(position.x, position.y, position.z);
        return playerFeet.getX() == target.getX()
                && playerFeet.getZ() == target.getZ()
                && mc.world.getBlockState(playerFeet).getBlock() instanceof LadderBlock;
    }

    private ParkourPlan findFlatGroundFallbackPlan(
            Vec3d currentPosition, double directionX, double directionZ, Box targetBox) {
        LandingRange landingRange = landingRange(targetBox, currentPosition, directionX, directionZ);
        if (landingRange == null) {
            double nearestX = Math.max(targetBox.minX, Math.min(currentPosition.x, targetBox.maxX));
            double nearestZ = Math.max(targetBox.minZ, Math.min(currentPosition.z, targetBox.maxZ));
            double aimX = nearestX - currentPosition.x;
            double aimZ = nearestZ - currentPosition.z;
            double length = Math.hypot(aimX, aimZ);
            if (length > 1.0E-9D) {
                directionX = aimX / length;
                directionZ = aimZ / length;
            }
            landingRange = landingRange(targetBox, currentPosition, directionX, directionZ);
        }

        RunupState fullRunup = simulateFullRunup(currentPosition, directionX, directionZ);
        LandingTrajectory jumpFromCurrent =
                simulateJump(currentPosition, fullRunup.speed(), directionX, directionZ, targetBox);
        boolean reachesTargetHeight = jumpFromCurrent != null;
        if (jumpFromCurrent == null) {
            jumpFromCurrent = simulateJumpOnLevel(
                    currentPosition, fullRunup.speed(), directionX, directionZ, getParkourTargetPlaneY(targetBox));
        }

        double currentAlong = currentPosition.x * directionX + currentPosition.z * directionZ;
        double jumpDistance = jumpFromCurrent.landingAlong() - currentAlong;
        if (landingRange == null) {
            double nearestX = Math.max(targetBox.minX, Math.min(currentPosition.x, targetBox.maxX));
            double nearestZ = Math.max(targetBox.minZ, Math.min(currentPosition.z, targetBox.maxZ));
            double nearestAlong = nearestX * directionX + nearestZ * directionZ;
            landingRange = new LandingRange(nearestAlong, nearestAlong);
        }
        int landingSamples = Math.max(1, (int) Math.ceil((landingRange.far() - landingRange.near()) / 0.01D));
        for (int sample = 0; sample <= landingSamples; sample++) {
            double landingAlong =
                    landingRange.near() + (landingRange.far() - landingRange.near()) * sample / landingSamples;
            double takeoffAlong = landingAlong - jumpDistance;
            double takeoffOffset = takeoffAlong - currentAlong;
            Vec3d takeoff = currentPosition.add(directionX * takeoffOffset, 0.0D, directionZ * takeoffOffset);
            RunupState reversedRunup = reverseFullRunup(takeoff, directionX, directionZ);
            LandingTrajectory jump = translateTrajectory(
                    jumpFromCurrent,
                    takeoff.x - currentPosition.x,
                    takeoff.z - currentPosition.z,
                    directionX,
                    directionZ);
            Vec3d actualLanding = jump.points().get(jump.points().size() - 1);
            if (!hasLandingSupport(actualLanding, targetBox)) continue;

            double runupStartOffset = (reversedRunup.approach().get(0).x - currentPosition.x) * directionX
                    + (reversedRunup.approach().get(0).z - currentPosition.z) * directionZ;
            List<Vec3d> trajectory = new ArrayList<>(reversedRunup.approach());
            trajectory.addAll(jump.points().subList(1, jump.points().size()));
            return new ParkourPlan(trajectory, runupStartOffset, reachesTargetHeight);
        }
        double landingAlong = landingRange.near();
        double takeoffOffset = landingAlong - jumpDistance - currentAlong;
        Vec3d takeoff = currentPosition.add(directionX * takeoffOffset, 0.0D, directionZ * takeoffOffset);
        RunupState reversedRunup = reverseFullRunup(takeoff, directionX, directionZ);
        LandingTrajectory jump = translateTrajectory(
                jumpFromCurrent, directionX * takeoffOffset, directionZ * takeoffOffset, directionX, directionZ);
        List<Vec3d> trajectory = new ArrayList<>(reversedRunup.approach());
        trajectory.addAll(jump.points().subList(1, jump.points().size()));
        return new ParkourPlan(
                trajectory,
                (reversedRunup.approach().get(0).x - currentPosition.x) * directionX
                        + (reversedRunup.approach().get(0).z - currentPosition.z) * directionZ,
                reachesTargetHeight);
    }

    private LandingTrajectory simulateJumpOnLevel(
            Vec3d takeoff, double initialSpeed, double directionX, double directionZ, double landingY) {
        List<Vec3d> points = new ArrayList<>();
        double velocityX = directionX * initialSpeed + directionX * 0.2D;
        double velocityZ = directionZ * initialSpeed + directionZ * 0.2D;
        double x = takeoff.x;
        double y = takeoff.y;
        double z = takeoff.z;
        double vy = Math.max(
                mc.player.getVelocity().y, LivingEntityAccess.of(mc.player).getJumpUpwardSpeed(1.0F));
        points.add(takeoff);
        for (int tick = 0; tick < 40; tick++) {
            double previousX = x;
            double previousY = y;
            double previousZ = z;
            double inputAcceleration =
                    tick == 0 ? getParkourGroundAcceleration(true, takeoff) : getParkourAirAcceleration(true);
            double movementX = velocityX + directionX * inputAcceleration;
            double movementZ = velocityZ + directionZ * inputAcceleration;
            double movementY = vy;
            x += movementX;
            z += movementZ;
            y += movementY;
            double horizontalFriction = tick == 0 ? getParkourGroundHorizontalFriction(takeoff) : PARKOUR_AIR_FRICTION;
            velocityX = movementX * horizontalFriction;
            velocityZ = movementZ * horizontalFriction;
            vy = (vy - mc.player.getFinalGravity()) * PARKOUR_VERTICAL_FRICTION;
            if (movementY < 0.0D && previousY >= landingY && y <= landingY) {
                double progress = (previousY - landingY) / (previousY - y);
                points.add(new Vec3d(
                        previousX + (x - previousX) * progress, landingY, previousZ + (z - previousZ) * progress));
                break;
            }
            points.add(new Vec3d(x, y, z));
        }
        Vec3d landing = points.get(points.size() - 1);
        return new LandingTrajectory(points, landing.x * directionX + landing.z * directionZ);
    }

    private ParkourPlan findFutureJumpTrajectory(Box targetBox, double directionX, double directionZ) {
        if (!mc.player.isOnGround()) return null;

        Vec3d position = mc.player.getPos();
        Vec3d velocity = mc.player.getVelocity();
        double velocityX = velocity.x;
        double velocityZ = velocity.z;
        boolean ladderTarget = isLadderTarget(targetBox);
        ParkourPlan bestLadderPlan = null;
        double bestLadderContactY = Double.NEGATIVE_INFINITY;
        List<Vec3d> approach = new ArrayList<>();
        approach.add(position);

        if (!hasGroundAt(position)) return null;
        for (int tick = 0; tick <= 20; tick++) {
            LandingTrajectory jump =
                    simulateJump(position, velocityX, velocityZ, true, directionX, directionZ, targetBox);
            if (jump != null) {
                Vec3d landing = jump.points().get(jump.points().size() - 1);
                if (hasLandingSupport(landing, targetBox)) {
                    List<Vec3d> trajectory = new ArrayList<>(approach);
                    trajectory.addAll(jump.points().subList(1, jump.points().size()));
                    ParkourPlan candidate = new ParkourPlan(trajectory, 0.0D, true, tick);
                    if (!ladderTarget) return candidate;
                    if (landing.y > bestLadderContactY + 1.0E-6D
                            || (Math.abs(landing.y - bestLadderContactY) <= 1.0E-6D
                                    && (bestLadderPlan == null || tick > bestLadderPlan.takeoffDelayTicks()))) {
                        bestLadderPlan = candidate;
                        bestLadderContactY = landing.y;
                    }
                }
            }

            if (tick == 20) break;
            double groundAcceleration = getParkourGroundAcceleration(true, position);
            double movementX = velocityX + directionX * groundAcceleration;
            double movementZ = velocityZ + directionZ * groundAcceleration;
            float horizontalFriction = getParkourGroundHorizontalFriction(position);
            Vec3d nextPosition = position.add(movementX, 0.0D, movementZ);
            if (!hasGroundAlong(position, nextPosition)) return bestLadderPlan;

            position = nextPosition;
            velocityX = movementX * horizontalFriction;
            velocityZ = movementZ * horizontalFriction;
            approach.add(position);
        }
        return bestLadderPlan;
    }

    private void renderParkour(Event<Render3D> event) {
        if (!autoParkourEnable.get()) return;
        parkourTarget.render3D(event.context.stack(), event.context.partialTicks());
        parkourTrajectory.render3D(event.context.stack(), event.context.partialTicks());
    }
}
