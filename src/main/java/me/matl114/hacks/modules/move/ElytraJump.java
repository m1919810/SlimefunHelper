package me.matl114.hacks.modules.move;

import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.mine.QueueMine;
import me.matl114.hacks.modules.task.MovementManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.PlayerInputUtils;
import me.matl114.hacks.utils.RaycastUtils;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.config.Pos3;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.move.goal.GoalNearBlockPos;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollisionUtil;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ElytraJump extends BaseModule implements LegalMovementManager.MovementModifier {
    public ElytraJump() {
        super("ElytraJump");
        MovementManager.registerMovementPipeline(this);
        bindFlag(enable);
    }

    ModulePath root = makePath(Configs.MOV_CONFIG, "elytra.elytra-flight-legit.elytra-jump");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef routeLock = flagBuilder(root.add("route-lock")).build();
    public final KeyBindRef routeLockHotkey = toggleHotkey(
                    root.add("route-lock-hotkey"), new MultiKeyBind(), root.add("route-lock"))
            .build();

    public final NBTRef<OptionalPrimitive<Pos3>> destination = builder(
                    root.add("destination"), OptionalPrimitive.type(Pos3.class))
            .defaultValue(new OptionalPrimitive<>(false, Pos3.TYPE, new Pos3(0, 0, 0)))
            .build();
    //

    public final DoubleRef pitch = doubleBuilder(root.add("pitch"))
            .defaultValue(80.0D)
            .validator(Configs.doubleRange(-90.0D, 90.0D))
            .build();

    public final FlagRef sneak = flagBuilder(root.add("sneak")).build();

    public final DoubleRef groundHeight = doubleBuilder(root.add("ground-height"))
            .defaultValue(3.0D)
            .validator(Configs.doubleRange(0.0D, 10.0D))
            .build();

    public final FlagRef axisStrict = flagBuilder(root.add("axis-strict")).build();
    public final FlagRef autoMineStoneAndNetherrackObstacles =
            flagBuilder(root.add("auto-mine-obstacles")).build();

    public final IntRef avoidObstacleYawRange = intBuilder(root.add("avoid-obstacles-yaw-range"))
            .defaultValue(50)
            .validator(Configs.intRange(0, 180))
            .build();

    public final IntRef predictTicks = intBuilder(root.add("avoid-obstacles-predict-ticks"))
            .defaultValue(30)
            .validator(Configs.intRange(0, 50))
            .build();

    public final FlagRef usePacketQueue = flagBuilder(root.add("queue-packets"))
            .updateListener(s -> {
                if (!s) {
                    flushImmediately();
                }
            })
            .build();

    private boolean workThisTick;
    private List<BlockPos> obstacles = List.of();
    private static final int OBSTACLE_LIMIT = 5;
    private static final double ROUTE_SEARCH_DISTANCE = 128.0D;
    private static final double ROUTE_SEARCH_STEP = 0.5D;
    private Route route;
    private BlockPos routeSafeTarget;
    private boolean ownsRoadPath;
    private final RenderCollector<List<Vec3d>> lineCollector = RenderCollectors.createLinesCollector();

    private record Route(Vec3d origin, Vec3d direction) {
        private Route {
            origin = origin.withAxis(Direction.Axis.Y, Math.floor(origin.y));
            direction = direction.withAxis(Direction.Axis.Y, 0.0D).normalize();
        }

        private Vec3d project(Vec3d position) {
            double alongRoute = position.subtract(origin).dotProduct(direction);
            return origin.add(direction.multiply(Math.max(0.0D, alongRoute)));
        }
    }

    private void flushImmediately() {
        if (checkNull()) return;
        PacketManager.schedule(() -> {
            if (queueing) {
                // check for fallflying
                PacketManager.flushOutBound(() -> {
                    queueing = false;
                });
            }
        });
    }

    private void startQueue() {
        if (checkNull()) return;
        PacketManager.schedule(() -> {
            queueing = true;
        });
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(PacketManager.getPacketQueueOutEvent(), this::onPacketQueue);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onPacketFlush);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
    }

    private void onRender3D(Event<Render3D> event) {
        if (checkNull() || !routeLock.get()) return;
        lineCollector.render3D(event.context.stack(), event.context.partialTicks());
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        clearRoadPath();
        if (usePacketQueue.get()) {
            flushImmediately();
        }
    }

    public void onPreInputEvent() {
        if (workThisTick && autoMineStoneAndNetherrackObstacles.get() && !obstacles.isEmpty()) {
            if (obstacles.size() <= OBSTACLE_LIMIT) {
                obstacles.forEach(re -> QueueMine.INSTANCE.sumitMine(re));
            }
        }
    }

    public void onPacketFlush() {
        queueing = false;
    }

    boolean queueing = false;

    public void onPacketQueue(Event<PacketStorage.Send> eventIn) {
        if (queueing && usePacketQueue.get()) {
            PacketType<?> packetType = eventIn.context.packetType();
            if (PacketManager.isAsyncOrNotTransactionC2SPacket(packetType)) {
                return;
            }
            eventIn.cancel();
        }
    }

    private List<BlockPos> checkForObstacles(Vec3d horizontalDirection, double min, double max, int baseY, int height) {
        Vec3d center = mc.player.getPos().withAxis(Direction.Axis.Y, baseY);
        Vec3d velocity = horizontalDirection.withAxis(Direction.Axis.Y, 0);
        center = center.add(velocity.multiply(min));
        Vec3d checkCenter = center.add(velocity.multiply(max - min)).add(0, 1, 0);
        Vec3d center1 = center.add(-0.4, 0.1, -0.4);
        Vec3d center2 = center.add(0.4, 0.1, 0.4);
        Vec3d checkCenter1 = checkCenter.add(-0.4, -0.1, -0.4);
        Vec3d checkCenter2 = checkCenter.add(0.4, -0.1, 0.4);
        Set<BlockPos> basePoses = new HashSet<>();
        List<BlockPos> checkPoses = new ArrayList<>();
        for (var re : RaycastUtils.createRaycastBlockPoses(center1, checkCenter1)) {
            basePoses.add(re);
        }
        for (var re : RaycastUtils.createRaycastBlockPoses(center2, checkCenter2)) {
            basePoses.add(re);
        }
        for (var re : basePoses) {
            for (var i = 0; i < height; ++i) {
                BlockPos testPos = re.add(0, i, 0);
                if (!mc.world
                        .getBlockState(testPos)
                        .getCollisionShape(mc.world, testPos)
                        .isEmpty()) {
                    checkPoses.add(re.add(0, i, 0));
                }
            }
        }
        return checkPoses;
    }

    private int searchRayLength(float newYaw, int groundY) {
        double currentSpeed = mc.player.getVelocity().horizontalLength();
        Vec3d direction = EntityUtils.pitchYawToRotation(0, newYaw).normalize().multiply(currentSpeed);
        for (var i = 0; i < predictTicks.get(); ++i) {
            List<BlockPos> obs = checkForObstacles(direction, i, i + 1, groundY + 1, 3);
            if (!obs.isEmpty()) {
                return i;
            }
        }
        return predictTicks.get();
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        lineCollector.clear();
        workThisTick = false;
        BlockPos currentLandingBlock = null;
        if (!routeLock.get()) {
            if (route != null) clearRoute();
        } else if (route == null) {
            enable.set(true);
        }

        if (enable.get()) {
            // set the pitch first to avoid conflict with other mode
            List<BlockPos> groundings = CollisionUtil.getIntersectingBlockPositions(
                    mc.world, mc.player.getBoundingBox().stretch(0, -groundHeight.get(), 0), false);
            workThisTick = !groundings.isEmpty();
            if (workThisTick) {
                currentLandingBlock = groundings.stream()
                        .max(Comparator.comparingDouble(BlockPos::getY))
                        .orElseThrow();
            }
            if (workThisTick) {
                Vec3d velocity = mc.player.getVelocity().withAxis(Direction.Axis.Y, 0);

                var b = checkForObstacles(velocity, -1, 3, currentLandingBlock.getY() + 1, 3);
                List<BlockPos> miningBlocks = new ArrayList<>();
                for (var re : b) {
                    BlockState state = mc.world.getBlockState(re);
                    if (!state.isAir() && !state.isLiquid() && state.getHardness(mc.world, re) < 3.0) {
                        miningBlocks.add(re);
                    }
                }
                obstacles = miningBlocks;
            } else {
                obstacles = List.of();
            }
        } else {
            obstacles = List.of();
        }

        // 2 blocks lower
        if (workThisTick) {
            if (mc.player.isFallFlying() || lastFallFly) {
                if (mc.player.getVelocity().y >= 0) {
                    mc.player.setPitch((float) pitch.get());
                } else {
                    mc.player.setPitch((float) pitch.get());
                }
            }
            if (axisStrict.get()) {
                PlayerStateManager.setPlayerYawSafe(mc.player, axis(mc.player.getYaw()));
            }
            movementManagerEvent.context.markForResetRot();
        }

        if (routeLock.get() && route == null) {
            initializeRoute(currentLandingBlock);
        }

        Vec3d routeProjection = null;
        boolean routeInRange = false;
        if (routeLock.get() && route != null) {
            routeProjection = route.project(mc.player.getPos());
            double horizontalProjectionDistanceSquared =
                    mc.player.getPos().subtract(routeProjection).horizontalLengthSquared();
            routeInRange = horizontalProjectionDistanceSquared < 100.0D;
            if (horizontalProjectionDistanceSquared <= 256.0D * 256.0D) {
                lineCollector.submit(
                        List.of(
                                routeProjection,
                                routeProjection.add(route.direction().multiply(256.0D))),
                        0xFF00FF00);
            }
        }

        if (routeInRange) {
            if (hasReachedDestination(mc.player.getPos())) {
                if (enable.get()) enable.set(false);
                routeLock.set(false);
                workThisTick = false;
                clearRoute();
                return;
            }
            float routeYaw = EntityUtils.rotationToYaw(route.direction());
            mc.player.setYaw(routeYaw);

            if (currentLandingBlock != null) {
                int groundY = currentLandingBlock.getY();
                float maxYaw = routeYaw;
                int maxLen = 0;
                for (var i = 0; i < avoidObstacleYawRange.get(); i += 2) {
                    float newYaw = routeYaw + i;
                    int len = searchRayLength(newYaw, groundY);
                    if (len >= predictTicks.get()) {
                        maxYaw = newYaw;
                        maxLen = len;
                        break;
                    } else if (maxLen < len) {
                        maxLen = len;
                        maxYaw = newYaw;
                    }
                    if (i == 0) continue;
                    newYaw = routeYaw - i;
                    len = searchRayLength(newYaw, groundY);
                    if (len >= predictTicks.get()) {
                        maxYaw = newYaw;
                        maxLen = len;
                        break;
                    } else if (maxLen < len) {
                        maxLen = len;
                        maxYaw = newYaw;
                    }
                }
                mc.player.setYaw(maxYaw);
            }
            movementManagerEvent.context.markForResetRot();
            updateRouteRecovery(routeProjection, currentLandingBlock);
        } else {
            clearRoadPath();
        }
    }

    private void initializeRoute(BlockPos currentLandingBlock) {
        int groundY = currentLandingBlock != null
                ? currentLandingBlock.getY()
                : BlockPos.ofFloored(mc.player.getPos().add(0.0D, -0.1D, 0.0D)).getY();
        float yaw = mc.player.getYaw();
        if (axisStrict.get()) {
            yaw = axis(yaw);
            PlayerStateManager.setPlayerYawSafe(mc.player, yaw);
        }
        route = new Route(
                mc.player.getPos().withAxis(Direction.Axis.Y, groundY + 1.0D),
                EntityUtils.pitchYawToRotation(0.0F, yaw));
        routeSafeTarget = null;
    }

    private boolean hasReachedDestination(Vec3d playerPosition) {
        OptionalPrimitive<Pos3> target = destination.get();
        if (!target.isPresent()) return false;
        Pos3 position = target.getValue();
        Vec3d targetProjection = route.project(new Vec3d(position.x(), position.y(), position.z()));
        return playerPosition.subtract(targetProjection).dotProduct(route.direction()) >= 0.0D;
    }

    private void updateRouteRecovery(Vec3d routeProjection, BlockPos currentLandingBlock) {
        if (!enable.get() || currentLandingBlock == null) {
            clearRoadPath();
            return;
        }
        int routeGroundY = BlockPos.ofFloored(route.origin()).getY() - 1;
        if (currentLandingBlock.getY() == routeGroundY) {
            routeSafeTarget = null;
            clearRoadPath();
            return;
        }

        if (mc.player.isOnGround()) {
            routeSafeTarget = findSafeRouteTarget(routeProjection);
        }
        if (routeSafeTarget == null) {
            clearRoadPath();
            return;
        }
        if (new GoalNearBlockPos(routeSafeTarget).isInGoal(mc.player.getPos())) {
            routeSafeTarget = null;
            clearRoadPath();
            return;
        }
        if (!BaritoneHooks.getInstance().isBaritoneAPISupported()) {
            clearRoadPath();
            return;
        }

        workThisTick = false;
        if (!BaritoneHooks.getInstance().isBaritoneGoalPathingActive()) {
            BaritoneHooks.getInstance().setBaritoneCurrentGoal(new GoalNearBlockPos(routeSafeTarget));
            ownsRoadPath = true;
        }
    }

    private BlockPos findSafeRouteTarget(Vec3d routeProjection) {
        if (routeSafeTarget != null && hasSafeRouteFloor(routeSafeTarget.toBottomCenterPos())) {
            return routeSafeTarget;
        }
        for (double distance = 0.0D; distance <= ROUTE_SEARCH_DISTANCE; distance += ROUTE_SEARCH_STEP) {
            Vec3d candidate = routeProjection.add(route.direction().multiply(distance));
            BlockPos candidateTarget = BlockPos.ofFloored(candidate);
            if (hasSafeRouteFloor(candidateTarget.toBottomCenterPos())) {
                return candidateTarget;
            }
        }
        return null;
    }

    private boolean hasSafeRouteFloor(Vec3d feetPosition) {
        var playerBox = mc.player.dimensions.getBoxAt(feetPosition);
        if (CollisionUtil.isBoxCollided(mc.world, mc.player, playerBox)) return false;

        int supportY = BlockPos.ofFloored(feetPosition).getY() - 1;
        int minBlockX = BlockPos.ofFloored(playerBox.minX, feetPosition.y, feetPosition.z)
                .getX();
        int maxBlockX = BlockPos.ofFloored(playerBox.maxX, feetPosition.y, feetPosition.z)
                .getX();
        int minBlockZ = BlockPos.ofFloored(feetPosition.x, feetPosition.y, playerBox.minZ)
                .getZ();
        int maxBlockZ = BlockPos.ofFloored(feetPosition.x, feetPosition.y, playerBox.maxZ)
                .getZ();
        for (int x = minBlockX; x <= maxBlockX; ++x) {
            for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                BlockPos supportPos = new BlockPos(x, supportY, z);
                var shape = mc.world.getBlockState(supportPos).getCollisionShape(mc.world, supportPos);
                for (var collisionBox : shape.getBoundingBoxes()) {
                    var worldBox = collisionBox.offset(supportPos);
                    if (Math.abs(worldBox.maxY - feetPosition.y) < 0.05D
                            && worldBox.maxX > playerBox.minX
                            && worldBox.minX < playerBox.maxX
                            && worldBox.maxZ > playerBox.minZ
                            && worldBox.minZ < playerBox.maxZ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void clearRoute() {
        lineCollector.clear();
        clearRoadPath();
        route = null;
        routeSafeTarget = null;
    }

    private void clearRoadPath() {
        if (ownsRoadPath && BaritoneHooks.getInstance().isBaritoneGoalPathingActive()) {
            BaritoneHooks.getInstance().cancelBaritone();
        }
        ownsRoadPath = false;
    }

    private float axis(float currentYaw) {
        float yaw = EntityUtils.normalizeYaw(currentYaw);
        float nearest = Math.round(yaw / 45.0f) * 45.0f;

        // 处理 -180 / 180 附近
        if (nearest > 180) nearest -= 360;
        if (nearest < -180) nearest += 360;

        // 判断与最近轴线的角度差
        float diff = Math.abs(yaw - nearest);
        if (diff > 180) diff = 360 - diff;

        return diff <= 10 ? nearest : yaw;
    }

    boolean lastFallFly = false;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        var re = PlayerInputUtils.of(mc.player);
        if (workThisTick) {
            if (mc.player.isOnGround()) {
                re.jump(true).sprint(true).forward(true).sneak(sneak.get()).applyInput(mc.player);
                mc.player.setSprinting(true);
            } else {
                if (!mc.player.isFallFlying()) {
                    if (mc.player.checkFallFlying()) {
                        MovTasks.getMovExtra().sendPacketsForPreStartFallFlying();
                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                    }
                }
                re.sprint(true).jump(false).forward(true).sneak(sneak.get()).applyInput(mc.player);
            }
        }
        if (workThisTick && usePacketQueue.get()) {
            if (mc.player.isOnGround()) {
                flushImmediately();
            }
            if (re.jump()) {
                startQueue();
            }
        } else {
            flushImmediately();
            ;
        }
        lastFallFly = mc.player.isFallFlying();
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> event, boolean enableThisTick) {

        return true;
    }
}
