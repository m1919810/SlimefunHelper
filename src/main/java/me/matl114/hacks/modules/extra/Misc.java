package me.matl114.hacks.modules.extra;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Misc extends BaseModule {
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
    private final RenderCollector<Box> parkourTarget = RenderCollectors.createBoxCollector(true, false, false);
    private final RenderCollector<List<Vec3d>> parkourTrajectory = RenderCollectors.createLinesCollector();
    private static final int PARKOUR_COLOR = Color.CYAN.getRGB();
    private static final int JUMP_COLOR = Color.GREEN.getRGB();
    private static final double PARKOUR_LANDING_MARGIN = 0.1D;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
        registerListener(Listener.getPostGameTick(), this::updateParkour);
        registerListener(RenderListener.getRender3DEvent(), this::renderParkour);
    }

    public void onPostTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
    }

    public void onPreGameTick(Event<ClientPlayerEntity> event) {
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

    private void updateParkour(Event<ClientPlayerEntity> event) {
        parkourTarget.clear();
        parkourTrajectory.clear();
        if (!autoParkourEnable.get() || checkNull() || !mc.player.isOnGround() || mc.player.isFallFlying()) return;

        double yaw = Math.toRadians(mc.player.getYaw());
        double directionX = -Math.sin(yaw);
        double directionZ = Math.cos(yaw);
        double directionLength = Math.hypot(directionX, directionZ);
        directionX /= directionLength;
        directionZ /= directionLength;
        BlockPos support = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.01, mc.player.getZ());
        BlockPos target = findGapTarget(support, directionX, directionZ);
        if (target == null) return;
        if (mc.options.forwardKey.isPressed()) {
            PlayerInputManager.INSTANCE.addSprintModifier(0, true, 1);
        }
        if (isNearHorizontalNeighbor(support, target)) return;

        BlockState state = mc.world.getBlockState(target);
        var collision = state.getCollisionShape(mc.world, target);
        if (collision.isEmpty()) return;
        Box targetBox = collision.getBoundingBox().offset(target);
        parkourTarget.submit(targetBox, PARKOUR_COLOR);

        double playerCenter = mc.player.getX() * directionX + mc.player.getZ() * directionZ;
        double playerHalfWidth = mc.player.getWidth() * 0.5 * (Math.abs(directionX) + Math.abs(directionZ));
        double targetFar = maxProjection(targetBox, directionX, directionZ);
        double maxTravelDistance = targetFar + playerHalfWidth - playerCenter;

        List<Vec3d> trajectory = simulateJump(maxTravelDistance, directionX, directionZ);
        boolean shouldJump = reachesLanding(trajectory, targetBox, directionX, directionZ)
                && mc.options.forwardKey.isPressed();
        parkourTrajectory.submit(trajectory, shouldJump ? JUMP_COLOR : Color.RED.getRGB());
        if (shouldJump) {
            PlayerInputManager.INSTANCE.addJumpModifier(0, true, 1);
        }
    }

    private boolean isNearHorizontalNeighbor(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) <= 1 && Math.abs(first.getZ() - second.getZ()) <= 1;
    }

    private BlockPos findGapTarget(BlockPos support, double directionX, double directionZ) {
        if (mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()) return null;
        boolean gapSeen = false;
        Vec3d rayStart = new Vec3d(mc.player.getX(), support.getY() + 0.5, mc.player.getZ());
        double searchDistance = parkourSearchDistance.get();
        Vec3d rayEnd = rayStart.add(directionX * searchDistance, 0.0, directionZ * searchDistance);
        List<BlockPos> projectedPositions = new ArrayList<>();
        for (BlockPos projected : RaycastUtils.createRaycastBlockPoses(rayStart, rayEnd)) {
            if (projected.equals(support)) continue;
            BlockState projectedState = mc.world.getBlockState(projected);
            if (!gapSeen) {
                if (!projectedState.getCollisionShape(mc.world, projected).isEmpty()) return null;
                if (!mc.world.getBlockState(projected.up()).isAir()) return null;
                gapSeen = true;
                continue;
            }
            projectedPositions.add(projected);
        }

        for (int y = support.getY() + 1; y >= support.getY() - 2; y--) {
            for (BlockPos projected : projectedPositions) {
                BlockPos candidate = projected.withY(y);
                BlockState state = mc.world.getBlockState(candidate);
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
                if (mc.world.isSpaceEmpty(playerSpace)) return candidate;
            }
        }
        return null;
    }

    private List<Vec3d> simulateJump(double maxTravelDistance, double directionX, double directionZ) {
        List<Vec3d> points = new ArrayList<>();
        double x = 0.0;
        double y = 0.0;
        double vx = Math.max(0.0, mc.player.getVelocity().x * directionX + mc.player.getVelocity().z * directionZ);
        double vy;
        points.add(worldPoint(directionX, directionZ, x, y));
        vy = 0.42;
        for (int tick = 0; tick < 40 && x < maxTravelDistance; tick++) {
            double acceleration = tick == 0 ? 0.13 : 0.026;
            vx = (vx + acceleration) * 0.91;
            x += vx;
            y += vy;
            vy = (vy - 0.08) * 0.98;
            points.add(worldPoint(directionX, directionZ, x, y));
        }
        return points;
    }

    private Vec3d worldPoint(double directionX, double directionZ, double along, double height) {
        return mc.player.getPos().add(directionX * along, height, directionZ * along);
    }

    private boolean reachesLanding(List<Vec3d> trajectory, Box targetBox, double directionX, double directionZ) {
        double landingY = targetBox.maxY;
        // Accept the full interior span with 0.1 clearance from both projected edges.
        double landingNear = minProjection(targetBox, directionX, directionZ) + PARKOUR_LANDING_MARGIN;
        double landingFar = maxProjection(targetBox, directionX, directionZ) - PARKOUR_LANDING_MARGIN;
        if (landingNear > landingFar || trajectory.size() < 2) return false;

        Vec3d previous = trajectory.get(0);
        for (int i = 1; i < trajectory.size(); i++) {
            Vec3d current = trajectory.get(i);
            if (current.y < previous.y && previous.y >= landingY && current.y <= landingY) {
                double tickProgress = (previous.y - landingY) / (previous.y - current.y);
                double x = previous.x + (current.x - previous.x) * tickProgress;
                double z = previous.z + (current.z - previous.z) * tickProgress;
                double landingAlong = x * directionX + z * directionZ;
                return landingAlong >= landingNear && landingAlong <= landingFar;
            }
            previous = current;
        }
        return false;
    }

    private double minProjection(Box box, double directionX, double directionZ) {
        return Math.min(box.minX * directionX, box.maxX * directionX)
                + Math.min(box.minZ * directionZ, box.maxZ * directionZ);
    }

    private double maxProjection(Box box, double directionX, double directionZ) {
        return Math.max(box.minX * directionX, box.maxX * directionX)
                + Math.max(box.minZ * directionZ, box.maxZ * directionZ);
    }

    private void renderParkour(Event<Render3D> event) {
        if (!autoParkourEnable.get()) return;
        parkourTarget.render3D(event.context.stack(), event.context.partialTicks());
        parkourTrajectory.render3D(event.context.stack(), event.context.partialTicks());
    }
}
