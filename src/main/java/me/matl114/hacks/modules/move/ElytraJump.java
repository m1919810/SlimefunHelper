package me.matl114.hacks.modules.move;

import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.mine.QueueMine;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.move.goal.GoalNearBlockPos;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollisionUtil;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ElytraJump extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraJump() {
        super("ElytraJump");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    ModulePath root = makePath(Configs.MOV_CONFIG, "elytra.elytra-flight-legit.elytra-jump");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();
    //
    public final FlagRef conditionalSprint =
            flagBuilder(root.add("conditional-sprint")).build();

    public final DoubleRef pitch =
            doubleBuilder(root.add("pitch")).defaultValue(80.0D).build();

    public final FlagRef sneak = flagBuilder(root.add("sneak")).build();

    public final DoubleRef groundHeight =
            doubleBuilder(root.add("ground-height")).defaultValue(3.0D).build();

    public final FlagRef axisStrict = flagBuilder(root.add("axis-strict")).build();
    public final FlagRef autoMineStoneAndNetherrackObstacles =
            flagBuilder(root.add("auto-mine-obstacles")).build();

    public final FlagRef autoAvoidObstacle =
            flagBuilder(root.add("auto-avoid-obstacles")).build();

    public final IntRef avoidObstacleYawRange = intBuilder(root.add("avoid-obstacles-yaw-range"))
            .defaultValue(50)
            .show(this.autoAvoidObstacle::get)
            .build();

    public final IntRef predictTicks = intBuilder(root.add("avoid-obstacles-predict-ticks"))
            .defaultValue(80)
            .build();

    public final FlagRef avoidHoles = flagBuilder(root.add("avoid-holes")).build();

    public final FlagRef usePacketQueue = flagBuilder(root.add("queue-packets"))
            .updateListener(s -> {
                if (!s) {
                    flushImmediately();
                }
            })
            .build();

    boolean workThisTick = false;
    BlockPos currentLandingBlock;
    List<BlockPos> obstacles = new ArrayList<>();
    private static final int OBSTACLE_LIMIT = 5;

    private void flushImmediately() {
        if (queueing) {
            queueing = false;
            if (checkNull()) return;
            // check for fallflying
            PacketManager.flushOutBound();
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(PacketManager.getPacketQueueEvent().getChannel(NetworkSide.SERVERBOUND), this::onPacketQueue);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onPacketFlush);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        if (usePacketQueue.get()) {
            flushImmediately();
        }
    }

    public void onPreInputEvent(Event<Void> event) {
        if (workThisTick
                && currentLandingBlock != null
                && autoMineStoneAndNetherrackObstacles.get()
                && obstacles != null
                && !obstacles.isEmpty()) {
            if (obstacles.size() <= OBSTACLE_LIMIT) {
                obstacles.forEach(re -> QueueMine.INSTANCE.sumitMine(re));
            }
        }
    }

    public void onPacketFlush(Event<Void> event) {
        queueing = false;
    }

    boolean queueing = false;

    public void onPacketQueue(Event<PacketStorage> eventIn) {
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

    public int searchRayLength(float newYaw) {

        double currentSpeed = mc.player.getVelocity().horizontalLength();
        Vec3d direction = EntityUtils.pitchYawToRotation(0, newYaw).normalize().multiply(currentSpeed);
        for (var i = 0; i < predictTicks.get(); ++i) {
            List<BlockPos> obs = checkForObstacles(direction, i, i + 1, currentLandingBlock.getY() + 1, 3);
            if (!obs.isEmpty()) {
                return i;
            }
        }
        return predictTicks.get();
    }

    boolean autoWalkAvoidObstacle = false;
    boolean needPathingToRoad = false;
    int lastStableHeight;
    int stableHeightCounter = 0;
    BlockPos lastStableBlockTarget = null;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        workThisTick = false;
        int lastLanding = currentLandingBlock == null ? Integer.MIN_VALUE : currentLandingBlock.getY();
        if (enable.get()) {
            // set the pitch first to avoid conflict with other mode
            List<BlockPos> groundings = CollisionUtil.getIntersectingBlockPositions(
                    mc.world, mc.player.getBoundingBox().stretch(0, -groundHeight.get(), 0), false);
            workThisTick = !groundings.isEmpty();
            if (workThisTick) {
                currentLandingBlock = groundings.stream()
                        .max(Comparator.comparingDouble(BlockPos::getY))
                        .orElseThrow();
            } else {
                currentLandingBlock = null;
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
        }
        if (workThisTick) {
            if (lastStableHeight < currentLandingBlock.getY()) {
                lastStableHeight = currentLandingBlock.getY();
            }
            if (lastStableHeight == currentLandingBlock.getY()) {
                lastStableBlockTarget = BlockPos.ofFloored(Vec3d.of(currentLandingBlock)
                        .add(PlayerStateManager.INSTANCE
                                .lastKnownClientVelocity
                                .withAxis(Direction.Axis.Y, 0)
                                .multiply(4.0)));
            }
            if (lastLanding == currentLandingBlock.getY()) {
                stableHeightCounter += 1;
                if (stableHeightCounter > 200) {
                    lastStableHeight = currentLandingBlock.getY();
                }
            } else {
                stableHeightCounter = 0;
            }
        } else if (!enable.get()) {
            lastStableHeight = Integer.MIN_VALUE;
            autoWalkAvoidObstacle = false;
            if (needPathingToRoad
                    && lastStableBlockTarget != null
                    && BaritoneHooks.getInstance().isBaritoneGoalPathingActive()) {
                BaritoneHooks.getInstance().cancelBaritone();
            }
            needPathingToRoad = false;
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
            if (autoAvoidObstacle.get()) {
                float yaw = mc.player.getYaw();
                float maxYaw = yaw;
                int maxLen = 0;

                for (var i = 0; i < avoidObstacleYawRange.get(); ++i) {
                    float newYaw = yaw + i;
                    int len = searchRayLength(newYaw);
                    if (len >= predictTicks.get()) {
                        maxYaw = newYaw;
                        maxLen = len;
                        break;
                    } else if (maxLen < len) {
                        maxLen = len;
                        maxYaw = newYaw;
                    }
                    if (i == 0) continue;
                    newYaw = yaw - i;
                    len = searchRayLength(newYaw);
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
        }
        if (workThisTick
                && lastStableHeight >= currentLandingBlock.getY()
                && avoidHoles.get()
                && CollisionUtil.isBoxCollided(
                        mc.world,
                        mc.player,
                        mc.player
                                .dimensions
                                .getBoxAt(
                                        currentLandingBlock.toBottomCenterPos().add(0, 1, 0))
                                .offset(EntityUtils.pitchYawToRotation(0, mc.player.getYaw())))) {
            // check horizontal collision
            Vec3d simulationMove = MovTasks.simulateMovement(
                    mc.player,
                    mc.player.getPos().withAxis(Direction.Axis.Y, currentLandingBlock.getY() + 1),
                    EntityUtils.pitchYawToRotation(0, mc.player.getYaw()),
                    false);
            if (simulationMove.lengthSquared() < 1E-2) {
                autoWalkAvoidObstacle = true;
                workThisTick = false;
                List<BlockPos> checkBox = CollisionUtil.getBoxCollision(
                        mc.world,
                        mc.player,
                        mc.player
                                .dimensions
                                .getBoxAt(
                                        currentLandingBlock.toBottomCenterPos().add(0, 1, 0))
                                .offset(EntityUtils.pitchYawToRotation(0, mc.player.getYaw())));
                if (!checkBox.isEmpty()) {
                    // climb up 1 block
                    checkBox.stream()
                            .max(Comparator.comparingDouble(BlockPos::getY))
                            .ifPresent(checkBoxPos -> {
                                if (checkBoxPos.getY() <= lastStableHeight + 1) {
                                    lastStableHeight = checkBoxPos.getY();
                                }
                            });
                }
            } else {
                autoWalkAvoidObstacle = false;
            }

        } else {

            autoWalkAvoidObstacle = false;
        }
        if (autoWalkAvoidObstacle) {
            if (mc.player.getY() >= lastStableHeight + 1) {
                autoWalkAvoidObstacle = false;
            } else if (mc.player.getY() < lastStableHeight) {
                needPathingToRoad = true;
                autoWalkAvoidObstacle = false;
            }
        }
        path:
        if (needPathingToRoad) {
            if (!enable.get()) {
                needPathingToRoad = false;
                break path;
            }
            if (mc.player.getY() >= lastStableBlockTarget.getY() + 1) {
                needPathingToRoad = false;
                if (BaritoneHooks.getInstance().isBaritoneGoalPathingActive()) {
                    BaritoneHooks.getInstance().cancelBaritone();
                }
                break path;
            }
            workThisTick = false;
            if (lastStableBlockTarget != null
                    && BaritoneHooks.getInstance().isBaritoneAPISupported()
                    && !BaritoneHooks.getInstance().isBaritoneGoalPathingActive()) {
                BlockPos target = lastStableBlockTarget;

                BaritoneHooks.getInstance().setBaritoneCurrentGoal(new GoalNearBlockPos(target));
            }
        }
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

    boolean lastOnGround = false;

    boolean lastFallFly = false;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        var re = PlayerInputUtils.of(mc.player);
        if (autoWalkAvoidObstacle) {
            re.jump(true).sprint(true).forward(true).sneak(false).applyInput(mc.player);
        }
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
                queueing = true;
            }
        } else {
            flushImmediately();
            ;
        }
        lastOnGround = mc.player.isOnGround();
        lastFallFly = mc.player.isFallFlying();
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> event, boolean enableThisTick) {

        return true;
    }
}
