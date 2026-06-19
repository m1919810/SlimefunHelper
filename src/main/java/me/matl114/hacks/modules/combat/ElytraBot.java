package me.matl114.hacks.modules.combat;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.SlimefunHelper;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.accessors.hacks.PlayerInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.entity.PredictorImpl;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.algorithms.StateMachine;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ElytraBot extends BaseModule {
    public final ModulePath combatBot = makePath(Configs.COMBAT_CONFIG, "combat-bot");
    public final ModulePath elytraBot = combatBot.add("elytra-bot");

    public ElytraBot() {}

    public final FlagRef enable = flagBuilder(elytraBot.add("enable")).build();

    public final KeyBindRef keyBind = moduleEntry(
                    elytraBot.add("hotkey"), new MultiKeyBind(), elytraBot.add("enable"), moduleMeta(() -> this.mode))
            .build();

    public final IntRef targetRange = intBuilder(elytraBot.add("range"))
            .defaultValue(80)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final EnumRef<Mode> mode =
            builder(elytraBot.add("mode"), Mode.class).defaultValue(Mode.FOLLOW).build();

    public final FlagRef autoFly =
            flagBuilder(elytraBot.add("auto-start-fallflying")).build();

    public final FlagRef playerOnly = builder(elytraBot.add("player-only"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef onlyWhenNoWASD =
            flagBuilder(elytraBot.add("only-when-no-wasd")).build();

    public final DoubleRef combatRange = builder(elytraBot.add("combat-range"), DoubleRef.TYPE)
            .defaultValue(7.0D)
            .build();

    public final DoubleRef followOnGroundHeight = builder(elytraBot.add("follow-on-ground-height-extra"), Double.class)
            .defaultValue(0.5D)
            .validator(Configs.doubleRange(0.0D, 10.0D))
            .show(() -> mode.get().isNotIn(Mode.SPEAR_ARUA))
            .build();

    public final DoubleRef followOnSkyHeight = builder(elytraBot.add("follow-on-sky-height-extra"), DoubleRef.TYPE)
            .defaultValue(0.0D)
            .show(() -> mode.get().isNotIn(Mode.SPEAR_ARUA))
            .build();

    public final DoubleRef maceHeight = builder(elytraBot.add("mace-height"), DoubleRef.TYPE)
            .defaultValue(10.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef minimalAttackHeight = builder(elytraBot.add("min-attack-height"), Double.class)
            .defaultValue(4.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final IntRef maceRemainPullUpTick = builder(elytraBot.add("mace-max-extra-pull-up-tick"), IntRef.TYPE)
            .defaultValue(20)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef maceMaxFollowLowHeight = doubleBuilder(elytraBot.add("mace-max-follow-height"))
            .defaultValue(1.5D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef macePullUpUsePredictor = flagBuilder(elytraBot.add("mace-pull-up-use-predictor"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef maceFollowUsePredictor = flagBuilder(elytraBot.add("mace-follow-use-predictor"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef combatSmoothFlight = flagBuilder(elytraBot.add("combat-smooth-flight"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef combatSmoothArg1 = doubleBuilder(elytraBot.add("combat-smooth-flight-argument-1"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(1.0D)
            .build();

    public final DoubleRef combatSmoothArg11 = doubleBuilder(elytraBot.add("combat-smooth-flight-argument-1-1"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(0.0D)
            .build();

    public final FlagRef combatSmoothFlight2 = flagBuilder(elytraBot.add("combat-smooth-flight-2"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef combatSmoothFlight3 = flagBuilder(elytraBot.add("combat-smooth-flight-3"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef combatSmoothArg2 = doubleBuilder(elytraBot.add("combat-smooth-flight-argument-2"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(2.5D)
            .build();

    public final FlagRef flyAntiSpear = flagBuilder(elytraBot.add("fly-anti-spear"))
            .show(() -> mode.get().isNotIn(Mode.SPEAR_ARUA))
            .build();

    public final FlagRef flyAntiSpearRandDir =
            flagBuilder(elytraBot.add("fly-anti-spear-rand-dir")).build();

    public final DoubleRef flyAntiSpearArg1 = doubleBuilder(elytraBot.add("fly-anti-spear-arg-1"))
            .show(() -> mode.get().isNotIn(Mode.SPEAR_ARUA))
            .defaultValue(0.0D)
            .build();

    public final FlagRef spearAntiSpear = flagBuilder(elytraBot.add("spear-anti-spear"))
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA))
            .build();

    public final DoubleRef spearAntiSpearExtraDistance = doubleBuilder(elytraBot.add("spear-anti-spear-extra-distance"))
            .defaultValue(0.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA))
            .build();

    public final FlagRef usePredictor =
            flagBuilder(elytraBot.add("spear-use-predictor")).build();

    public final FlagRef render = flagBuilder(elytraBot.add("render")).build();

    public final KeyBindRef switchMode = hotkey(elytraBot.add("switch-hotkey"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onSwitch))
            .build();

    public void onSwitch() {
        Mode mode1 = mode.get();
        var values = Mode.values();
        Mode mode2 = values[(mode1.ordinal() + 1) % values.length];
        Debug.chat("[ElytraBot] Mode switch to", mode2.getDisplay());
        mode.set(mode2);
    }

    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreTick(), this::onPreTick);
        registerListener(Listener.getCustomListener().getChannel(FlightVelocity.class), this::onElytraChase);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::onAttack);
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onEntityStatus);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        if (SlimefunHelper.DEV_ENV) {
            registerListener(RenderListener.getRenderGameHudTasks(), this::onDebugRender);
        }
        registerListener(Listener.getEntityPreTickListener().getChannel(EntityType.PLAYER), this::onEntityPreTick);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityDamage);
    }

    Entity target;

    @Nullable
    AbstractBotBehaviour currentBehaviour;

    AbstractBotBehaviour defaultBehaviour;
    Map<Mode, AbstractBotBehaviour> behaviourMap = new HashMap<>();

    Entity lastTarget;
    Box lastTargetHitBox;
    TargetAction currentAction;
    boolean currentInCombatRange;
    int afkTicker = 0;
    double speedMultiplier = 1.0D;

    public boolean isTargetUsingSpear() {
        return target instanceof PlayerEntity otherShit && SpearEnhance.isUsingSpear(otherShit);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        defaultBehaviour = new Follower();
        behaviourMap.put(Mode.FOLLOW, defaultBehaviour.setBase(this));
        behaviourMap.put(Mode.MACE_ARUA, new MaceArua().setBase(this));
        behaviourMap.put(Mode.SPEAR_ARUA, new SpearArua().setBase(this));
    }

    public void onPreTick(Event<Void> event) {
        var lastBehaviour = currentBehaviour;
        currentBehaviour = enable.get() ? behaviourMap.getOrDefault(mode.get(), defaultBehaviour) : null;
        if (currentBehaviour != lastBehaviour) {
            if (lastBehaviour != null) {
                lastBehaviour.onDisable();
            }
            if (currentBehaviour != null) {
                currentBehaviour.onEnable();
            }
        }
        if (checkNull()) return;
        if (currentBehaviour != null) {
            refreshTarget();
            updateTargetAction();
            currentBehaviour.onUpdate();
        }
    }

    Vec3d predictedPos;
    int predictedTick;

    private void test() {
        if (predictedTick <= Tasks.getTick()) {
            //            if(predictedTick == Tasks.getTick() && predictedPos != null){
            //                //
            //                Debug.info("Predict", predictedPos, "Real", target.getPos(),
            // predictedPos.subtract(target.getPos()).length());
            //            }
            //            predictedPos = PositionPredict.INSTANCE.getPredictor(target).predict(3, 1, 5);
            //            predictedTick = Tasks.getTick() + 3;
            //            predictedPos = PositionPredict.INSTANCE.getPredictor(target).predict(3, 1, 5);
        }
    }

    public void updateTargetAction() {
        if (target != lastTarget) {
            lastTarget = target;
            lastTargetHitBox = null;
            currentAction = null;
            afkTicker = 0;
        }
        if (target != null) {
            //
            test();
            // initialize pos
            if (lastTargetHitBox == null) {
                lastTargetHitBox = target.getBoundingBox();
                currentAction = TargetAction.AFK;
            }
            currentInCombatRange = TargetSelector.INSTANCE.isWithinAttackRange(
                    mc.player.getPos(), lastTargetHitBox, combatRange.get());
            if (target instanceof PlayerEntity pl) {
                // speed < 1, we can easily handle this speed
                if (pl.isOnGround() || CollisionUtil.isEntitySupported(pl)) {
                    currentAction = TargetAction.SLOW_SPEED;
                } else {
                    List<PredictorImpl.KnownPosition> knownPositions =
                            ((PlayerInternalAccess) target).getPredictorImpl().getLastKnownPositions(3);
                    if (knownPositions.size() < 2) {
                        // 数据不足，默认行为（可改为 TOWARDS 或不做处理）
                        currentAction = TargetAction.CIRCLING;
                    } else {
                        int currentTick = Tasks.getTick();
                        // 1. 最早的点（索引0）是否在10 tick之前
                        PredictorImpl.KnownPosition oldest = knownPositions.get(0);
                        if (currentTick - oldest.tick() > 20) {
                            currentAction = TargetAction.AFK;
                        } else {
                            // 相邻点距离检查
                            Vec3d pos0 = oldest.vec3d();
                            Vec3d pos1 = knownPositions.get(1).vec3d();
                            Vec3d pos2 = knownPositions.get(2).vec3d();

                            double dist01 = pos0.distanceTo(pos1);
                            double dist12 = pos1.distanceTo(pos2);

                            // 2. 若相邻两点距离小于1.5，判定为SLOW_SPEED
                            if (dist01 < 0.75 || dist12 < 0.75) {
                                currentAction = TargetAction.SLOW_SPEED;
                            } else if (knownPositions.size() >= 3) {
                                // 3. 计算向量 ab 和 bc 的夹角
                                Vec3d ab = pos1.subtract(pos0);
                                Vec3d bc = pos2.subtract(pos1);
                                double dot = ab.dotProduct(bc);
                                double magAB = ab.length();
                                double magBC = bc.length();
                                double angleRad = Math.acos(Math.min(1.0, Math.max(-1.0, dot / (magAB * magBC))));
                                double angleDeg = Math.toDegrees(angleRad);

                                if (angleDeg < 60.0) {
                                    // 方向变化小，判断朝向玩家还是远离玩家
                                    Vec3d playerPos = mc.player.getPos();
                                    // 使用从最新点(pos2)指向玩家的向量
                                    Vec3d toPlayer = playerPos.subtract(pos2);
                                    // 如果 bc 方向（移动方向）与指向玩家的方向夹角小于90度，视为向玩家靠近
                                    double moveDot = bc.normalize().dotProduct(toPlayer.normalize());
                                    if (moveDot > 0) {
                                        currentAction = TargetAction.TOWARDS; // 向我们来
                                    } else {
                                        currentAction = TargetAction.ESCAPING; // 离我们去
                                    }
                                } else {
                                    currentAction = TargetAction.CIRCLING;
                                }
                            } else {
                                // 点不足3个，默认行为
                                currentAction = TargetAction.TOWARDS;
                            }
                        }
                    }
                }
            } else {
                currentAction = TargetAction.SLOW_SPEED;
            }

            lastTargetHitBox = target.getBoundingBox();
        }
    }

    public void onEntityPreTick(Event<Entity> event) {
        if (event.context == mc.player
                && enable.get()
                && autoFly.get()
                && !mc.player.isFallFlying()
                && !PlayerInputUtils.of(mc.options).jump(false).hasMovementControl() // do not check jump
                && currentBehaviour != null
                && !Objects.equals(Vec3d.ZERO, currentBehaviour.movementDirection)) {
            if (mc.player.isOnGround()) {
                mc.options.jumpKey.setPressed(true);
            } else if (mc.player.checkGliding()) {
                mc.options.jumpKey.setPressed(false);
                mc.getNetworkHandler()
                        .sendPacket(
                                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    public void onRender(Event<MatrixStack> event) {
        if (enable.get() && render.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                MatrixStack stack = event.context;
                if (currentBehaviour != null) {
                    Vec3d targetRender = currentBehaviour.movementDirection.add(mc.player.getPos());
                    if (targetRender != null) {
                        RenderUtils.drawOutlinedBox(
                                stack,
                                targetRender.add(RenderTasks.FROM),
                                targetRender.add(RenderTasks.TO),
                                Color.MAGENTA);
                    }
                }

            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    public void onDebugRender(Event<VDrawContext> eventVDraw) {
        if (enable.get() && render.get() && currentBehaviour != null && target != null) {
            var vdraw = eventVDraw.context;
            vdraw.getMatrices().pushMatrix();
            vdraw.getMatrices().translate(200, 200);
            vdraw.drawText(
                    mc.textRenderer,
                    "Action: %s, Combating: %s".formatted(currentAction, String.valueOf(currentInCombatRange)),
                    0,
                    0,
                    -1,
                    true);
            vdraw.getMatrices().popMatrix();
        }
    }

    public void refreshTarget() {
        if (target == null
                || !target.isAlive()
                || target.isRemoved()
                || target.getPos().squaredDistanceTo(mc.player.getPos()) > targetRange.get()) {
            target = null;
        }
        if (target == null) {
            target = CombatTasks.getTargetSelector()
                    .searchAttackEntity(
                            targetRange.get(), true, playerOnly.get() ? (e) -> e instanceof PlayerEntity : null);
        }
    }

    public void onElytraChase(Event<EventContainer<FlightVelocity>> event) {
        if (enable.get()) {
            AbstractBotBehaviour behaviour = currentBehaviour;
            if (behaviour != null) {
                if (onlyWhenNoWASD.get()) {
                    PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                    if (input.hasMovementControl()) {
                        behaviour.onPauseControl();
                        return;
                    }
                }
                behaviour.onElytra(event);
            }
        }
    }

    public void onAttack(Event<PlayerInteractEntityC2SPacket> attack) {
        if (enable.get()
                && currentBehaviour != null
                && PlayerInteractEntityC2SPacketAccess.of(attack.context).isAttack()) {
            Entity entity = mc.world.getEntityById(
                    PlayerInteractEntityC2SPacketAccess.of(attack.context).getEntityId());
            if (entity != null) {
                currentBehaviour.onAttack(entity);
            }
        }
    }

    public void onInputEvent(Event<Void> event) {
        if (enable.get()) {
            AbstractBotBehaviour behaviour = currentBehaviour;
            if (behaviour != null) {
                behaviour.onInputEvent(event);
            }
        }
    }

    public void onEntityStatus(Event<EntityStatusS2CPacket> statusS2CPacketEvent) {
        var statusS2CPacket = statusS2CPacketEvent.context;
        if (currentBehaviour instanceof HitListener sp
                && mc.player != null
                && mc.world != null
                && enable.get()
                && statusS2CPacket.getEntity(mc.world) == mc.player
                && statusS2CPacket.getStatus() == VDataFlag.ENTITY_STATUS_KINETIC_ATTACK) {

            sp.onHit(HitListener.HIT_SPEAR);
        }
    }

    public void onEntityDamage(Event<EntityDamageS2CPacket> e) {
        if (checkNull()) return;
        if (currentBehaviour instanceof HitListener sp
                && enable.get()
                && e.context.sourceCauseId() == mc.player.getId()
                && mc.world.getEntityById(e.context.entityId()) == target) {
            var source = e.context.sourceType().getKey().orElse(null);
            if (DamageUtils.isType(source, "mace_smash")) {
                // we trigger a mace smash
                sp.onHit(HitListener.HIT_MACE);
                return;
            }
            sp.onHit(HitListener.HIT_ATTACK);
        }
    }

    public static interface HitListener {
        static int HIT_ATTACK = 0;
        static int HIT_MACE = 1;
        static int HIT_SPEAR = 2;

        public void onHit(int type);
    }

    @Setter
    @Accessors(chain = true)
    public abstract static class AbstractBotBehaviour {
        // todo： add target anaylsis

        ElytraBot base;
        Vec3d movementDirection = Vec3d.ZERO;
        // todo: update target considering blocks , can we async calculate to let
        // use pitch search

        // todo: calculate reachable, if entity can reach reach distance
        public void onElytra(Event<EventContainer<FlightVelocity>> event) {
            if (movementDirection != null
                    && event.context.getValue().mode() == FlightVelocity.Mode.ELYTRA_FLIGHT
                    && movementDirection.lengthSquared() > 1E-9) {
                Vec3d targetVec = movementDirection;
                double targetVecVelocity = targetVec.length();
                targetVec = targetVec
                        .normalize()
                        .multiply(Math.min(
                                targetVecVelocity, event.context.getValue().maxVelocity() * base.speedMultiplier));
                event.context.getValue().velocity(targetVec);
            }
        }

        public synchronized void onUpdate() {
            base.speedMultiplier = 1.0D;
        }

        public void onInputEvent(Event<Void> eventInput) {}

        public synchronized void onAttack(Entity entity) {}

        public abstract void onEnable();

        public abstract void onDisable();

        public void onPauseControl() {}
    }

    public static class Follower extends AbstractBotBehaviour {
        // todo: add in-hole behaviour, add hole-esp related, add landing
        @Override
        public synchronized void onUpdate() {
            super.onUpdate();
            if (base.target != null) {
                movementDirection = base.target.getPos().subtract(mc.player.getPos());
                if (base.target.isOnGround() || CollisionUtil.isEntitySupported(base.target)) {
                    movementDirection = movementDirection.add(0, base.followOnGroundHeight.get(), 0);
                }
            } else {
                movementDirection = Vec3d.ZERO;
            }
        }

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}
    }

    public static class MaceArua extends AbstractBotBehaviour implements HitListener {

        static final int STATE_PULL_UP = 1;
        static final int STATE_FOLLOW = 2;
        static final int STATE_WAIT_ATTACK = 3;
        static final int STATE_NONE = 0;
        static final int STATE_DOWN_ATTACK = 4;
        public StateMachine stateMachine;
        int startWaitAttack = -1;
        double maxHeightInAttack = Double.MIN_VALUE;
        double startPullUp = Double.MIN_VALUE;
        int startPullUpTick = 0;

        public MaceArua() {
            stateMachine = new StateMachine(
                    STATE_NONE,
                    this::onCondition,
                    this::onStateNone,
                    this::onStatePullUp,
                    this::onStateFollow,
                    this::onStateWaitAttack,
                    this::onStateDownAttack);
            stateMachine.registerListener(STATE_WAIT_ATTACK, this::onStartWaitAttack);
            stateMachine.registerListener(STATE_PULL_UP, this::onStartPullUp);
            stateMachine.registerListener(STATE_DOWN_ATTACK, this::onStartDownAttack);
        }

        public int onCondition(StateMachine machine, int state) {
            if (base.target == null) {
                machine.markForEndState();
                movementDirection = Vec3d.ZERO;
                return STATE_NONE;
            }
            return state;
        }

        public int onStateNone(StateMachine machine) {
            if (base.target != null) {
                if (PlayerStateManager.INSTANCE.fallDistance > 4
                        && mc.player.getPos().getY() > base.target.getPos().getY() + 4.4) {
                    return STATE_FOLLOW;
                }
                return STATE_PULL_UP;
            }
            movementDirection = Vec3d.ZERO;
            machine.markForEndState();
            return STATE_NONE;
        }

        public int onStatePullUp(StateMachine machine) {
            Vec3d testMovement = new Vec3d(0, 0.1, 0);
            Vec3d simulation = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testMovement, true);
            if (simulation.squaredDistanceTo(testMovement) > 1E-4) {
                // can not pull up
                if (mc.player.getY() > base.target.getY() + 2.0D) {
                    return STATE_FOLLOW;
                }
                if ((base.currentInCombatRange || base.currentAction == TargetAction.TOWARDS)
                        && mc.player.getPos().squaredDistanceTo(base.target.getPos()) < 150) {
                    return STATE_DOWN_ATTACK;
                }
                return STATE_FOLLOW;
                //                followTarget = mc.player.getPos().subtract(0, -10, 0);
                //                machine.markForEndState();
                //                return STATE_DOWN_ATTACK;
            } else {

                double targetY = base.target.getY() + base.maceHeight.get();
                if (mc.player.getY() > base.target.getY()) {
                    if (base.currentAction == TargetAction.ESCAPING) {
                        return STATE_FOLLOW;
                    }
                    // chasing but can not reach for a long time
                    // do not reach target, just smash
                    if (startPullUpTick != 0
                            && Tasks.getTick()
                                    > base.maceRemainPullUpTick.get() + startPullUpTick + base.maceHeight.get()) {
                        return STATE_FOLLOW;
                    }
                }
                if (mc.player.getY() < targetY) {
                    setTargetToPlayerUpper();
                    machine.markForEndState();
                    return STATE_PULL_UP;
                } else {
                    return STATE_FOLLOW;
                }
            }
        }

        public int onStateFollow(StateMachine machine) {
            if (PlayerStateManager.INSTANCE.fallDistance < 1E-6 && lastFallDistance > 1E-6) {
                // we trigger falldistance reset during chase
                return STATE_PULL_UP;
            }
            // already reach the target
            if ((mc.player.getY() < base.target.getY() + base.minimalAttackHeight.get())
                    && TargetSelector.INSTANCE.isWithinAttackRange(
                            mc.player.getPos(),
                            base.target.getBoundingBox(),
                            CombatTasks.getCombatExtra().getAttackRange())) {
                setTargetToPlayer(true);
                scheduleAttack();
                machine.markForEndState();
                return STATE_WAIT_ATTACK;
            } else {
                setTargetToPlayer(false);
            }

            machine.markForEndState();
            return STATE_FOLLOW;
        }

        private void setTargetToPlayerUpper() {
            Vec3d predictor = base.macePullUpUsePredictor.get()
                    ? PositionPredict.INSTANCE.attackPredictArgument.get().predict(base.target)
                    : base.target.getPos();
            if (base.combatSmoothFlight.get()) {
                double combatRange = base.combatRange.get();
                //                if (base.currentAction == TargetAction.COMBATING) {
                //                    //
                //                    Vec3d towardsVector = mc.player.getPos().subtract(base.target.getPos());
                //                    Vec3d vertical = MathUtils.getVerticalWithSameXZ(towardsVector);
                //                    // ensure y > 0
                //                    if (vertical.y < 0) {
                //                        vertical = vertical.negate();
                //                    }
                //                    movementDirection = vertical.multiply(10);
                //                    return;
                //                }
                if (predictor.getY() >= mc.player.getY()) {
                    Vec3d center = base.target.dimensions.getBoxAt(predictor).getCenter();
                    double radius = combatRange + base.combatSmoothArg1.get();
                    Pair<Vec3d, Vec3d> tangents = MathUtils.getTangentWithSameXZ(center, radius, mc.player.getEyePos());
                    Vec3d vec3d = tangents.getFirst();
                    Vec3d vec3d2 = tangents.getSecond();
                    Vec3d vec3d3 = vec3d.y < vec3d2.y ? vec3d2 : vec3d;
                    if (Math.abs(base.combatSmoothArg11.get()) > 1E-6
                            && center.squaredDistanceTo(mc.player.getEyePos()) < MathUtils.s2(radius)) {
                        // 垂线
                        vec3d3 = vec3d3.normalize();
                        Vec3d delta = mc.player.getEyePos().subtract(center);
                        Vec3d horizontalMul = new Vec3d(delta.x, 0, delta.z).multiply(base.combatSmoothArg11.get());
                        vec3d3 = vec3d3.add(horizontalMul).normalize();
                    }
                    if (vec3d3.y > 0) {
                        movementDirection = vec3d3.multiply(10);
                        return;
                    }
                }
            }
            movementDirection = predictor
                    .withAxis(Direction.Axis.Y, (predictor.getY() + (2 * base.maceHeight.get()) + 0.5))
                    .subtract(mc.player.getPos());
        }

        private void setTargetToPlayer(boolean waitAttack) {
            Vec3d targetPos = base.maceFollowUsePredictor.get()
                    ? PositionPredict.INSTANCE.attackPredictArgument.get().predict(base.target)
                    : base.target.getPos();
            movementDirection = targetPos.subtract(mc.player.getPos());
            double minimalHeightLow = waitAttack
                    ? (CombatExtra.INSTANCE.getAttackRange() + mc.player.getEyeHeight(mc.player.getPose()))
                    : base.maceMaxFollowLowHeight.get();
            if (base.target.isOnGround() || CollisionUtil.isEntitySupported(base.target)) {
                // handle on ground target
                movementDirection = movementDirection.add(0, base.followOnGroundHeight.get(), 0);
            } else {
                movementDirection = movementDirection.add(0, base.followOnSkyHeight.get(), 0);
                if (base.target.getY() > mc.player.getY() && base.target.getY() < mc.player.getY() + minimalHeightLow) {
                    // do not go up if it is just a bit higher than
                    movementDirection = movementDirection.withAxis(Direction.Axis.Y, 0);
                } else if (base.target.getY() >= mc.player.getY() + minimalHeightLow) {
                    // to nothing modify
                } else if (base.combatSmoothFlight2.get()
                        && TargetSelector.INSTANCE.isWithinAttackRange(
                                mc.player.getPos(), base.target.getBoundingBox(), base.combatRange.get())) {
                    // todo: smooth flight 3, use xz cut , find fastest y low and acceptable
                    smoothFlightAttack();
                } else if (base.combatSmoothFlight3.get()) {
                    //                if (base.currentAction == TargetAction.COMBATING) {
                    //                    //
                    //                    Vec3d towardsVector = mc.player.getPos().subtract(base.target.getPos());
                    //                    Vec3d vertical = MathUtils.getVerticalWithSameXZ(towardsVector);
                    //                    // ensure y > 0
                    //                    if (vertical.y < 0) {
                    //                        vertical = vertical.negate();
                    //                    }
                    //                    movementDirection = vertical.multiply(10);
                    //                    return;
                    //                }
                    if (targetPos.getY() <= mc.player.getY()) {
                        Vec3d center =
                                base.target.dimensions.getBoxAt(targetPos).getCenter();
                        double radius = base.combatSmoothArg2.get();
                        Pair<Vec3d, Vec3d> tangents =
                                MathUtils.getTangentWithSameXZ(center, radius, mc.player.getEyePos());
                        Vec3d vec3d = tangents.getFirst();
                        Vec3d vec3d2 = tangents.getSecond();
                        Vec3d vec3d3 = vec3d.y < vec3d2.y ? vec3d : vec3d2;
                        if (vec3d3.y < 0) {
                            movementDirection = vec3d3.multiply(10);
                        }
                    }
                }
            }
            if (base.flyAntiSpear.get()
                    && Math.abs(base.flyAntiSpearArg1.get()) > 1E-6
                    && base.isTargetUsingSpear()
                    && mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos())
                            < MathUtils.s2(base.combatRange.get() + 6.0D)) {
                Vec3d originalLookHorizontal = movementDirection.getHorizontal();
                Vec3d vertical = new Vec3d(0, 1, 0);
                Vec3d side = vertical.crossProduct(originalLookHorizontal).normalize();
                if (base.flyAntiSpearRandDir.get() && (Tasks.getTick() % 8 < 4)) {
                    side = side.negate();
                }
                Vec3d origin = movementDirection.normalize();

                Vec3d multiply = side.multiply(base.flyAntiSpearArg1.get());
                movementDirection = origin.add(multiply).normalize().multiply(10);
            }
        }
        // compat delay attack shit, add cd,
        public int onStateWaitAttack(StateMachine machine) {

            if (base.currentAction != TargetAction.AFK && base.currentAction != TargetAction.SLOW_SPEED) {
                machine.markForEndState();
                return STATE_PULL_UP;
            }
            if (++startWaitAttack > 1) {
                return STATE_NONE;
            }
            machine.markForEndState();
            // stay!
            setTargetToPlayer(true);
            return STATE_WAIT_ATTACK;
        }

        private void smoothFlightAttack() {

            var re = MathUtils.getTangentWithSamePlate(
                    Vec3d.ZERO, Math.max(0, CombatExtra.INSTANCE.getAttackRange() / 2.0D), movementDirection.negate());
            var look = re.getFirst();
            movementDirection = look.normalize().multiply(movementDirection.length());
        }

        int startDownAttackTick = 0;
        // try create attack chance
        public int onStateDownAttack(StateMachine machine) {
            // take argument
            // down about 1.7 * 4 = 6.8 blocks
            //
            if (startDownAttackTick < 3 && (base.currentInCombatRange || base.currentAction == TargetAction.TOWARDS)) {
                startDownAttackTick += 1;
                machine.markForEndState();
                movementDirection = base.target.getPos().subtract(0, 10, 0).subtract(mc.player.getPos());
                if (base.combatSmoothFlight2.get()
                        && TargetSelector.INSTANCE.isWithinAttackRange(
                                mc.player.getPos(), base.target.getBoundingBox(), base.combatRange.get())) {
                    smoothFlightAttack();
                }
                Vec3d look = movementDirection.normalize();
                if (look.y < 0) {
                    // player height = 1.8
                    // 3 + 1.62 = 4.62 height
                    // 3 ticks 5.1 height ~ 0.9
                    // estimate 0.85
                    double lookY = -look.y;
                    // downward < 1.7 * 0.85
                    base.speedMultiplier = Math.min(1.0D, (0.75) / lookY);
                }
                return STATE_DOWN_ATTACK;
            }

            if (TargetSelector.INSTANCE.isWithinAttackRange(
                    mc.player.getPos(),
                    base.target.getBoundingBox(),
                    CombatTasks.getCombatExtra().getAttackRange())) {
                scheduleAttack();
                movementDirection = Vec3d.ZERO;
                machine.markForEndState();
                return STATE_WAIT_ATTACK;
            }
            return STATE_FOLLOW;
        }

        public void onStartWaitAttack(boolean on) {
            startWaitAttack = 0;
        }

        public void onStartDownAttack(boolean on) {
            startDownAttackTick = 0;
        }

        public void onStartPullUp(boolean on) {
            if (on) {
                startPullUp = mc.player.getY();
                startPullUpTick = Tasks.getTick();
            } else {
                startPullUp = Double.MIN_VALUE;
                startPullUpTick = 0;
            }
        }
        // todo: add downward
        double lastFallDistance;

        @Override
        public synchronized void onUpdate() {
            super.onUpdate();
            if (mc.player.isFallFlying()) {
                maxHeightInAttack = Math.max(maxHeightInAttack, mc.player.getY());
                stateMachine.step();
                // Debug.info("State", stateMachine.getState(), "height", PlayerStateManager.INSTANCE.fallDistance);
                // todo: consider cooldown, do not attack too fast
                if (attackFlag) {
                    if (base.target != null) {
                        // anti shield
                        boolean cooldown = lastAttackTick <= Tasks.getTick() - 3;
                        boolean useAntiShield = Attack.shouldUseAntiShield(base.target);
                        if ((mc.player.getAttackCooldownProgress(0.5F) > 0.95F) || useAntiShield) {
                            // can not deal mace attack anyway
                            Attack.AttackSettings settings =
                                    CombatTasks.getAttack().createAttackSettings();
                            if (useAntiShield) {
                                settings = settings.withAntiShieldSwap(true);
                            }
                            CombatTasks.getAttack().attackEntity(base.target, settings);
                            lastAttackTick = Tasks.getTick();
                        }
                        if (cooldown || PlayerStateManager.INSTANCE.fallDistance > 3) {
                            Attack.AttackSettings settings =
                                    CombatTasks.getAttack().createAttackSettings();
                            CombatTasks.getAttack()
                                    .attackEntity(
                                            base.target,
                                            settings.withMaceSwap(true)
                                                    .withInvSwap(false)
                                                    .withAntiShieldSwap(false));
                            lastAttackTick = Tasks.getTick();
                        }
                    }
                    maxHeightInAttack = mc.player.getY();
                    attackFlag = false;
                }
            } else {
                stateMachine.setState(STATE_NONE);
                movementDirection = Vec3d.ZERO;
            }
            lastFallDistance = PlayerStateManager.INSTANCE.fallDistance;
        }

        public synchronized void onPauseControl() {
            stateMachine.setState(STATE_NONE);
        }

        boolean attackFlag = false;
        int lastAttackTick;

        public void scheduleAttack() {
            attackFlag = true;
        }
        // todo: check mace swap

        @Override
        public void onEnable() {
            stateMachine.setState(STATE_NONE);
        }

        @Override
        public void onDisable() {}

        @Override
        public synchronized void onAttack(Entity entity) {
            if (mc.player.isFallFlying()
                    && stateMachine.getState() != STATE_PULL_UP
                    && stateMachine.getState() != STATE_DOWN_ATTACK) {
                stateMachine.setState(STATE_FOLLOW);
                lastAttackTick = Tasks.getTick();
            }
        }

        @Override
        public synchronized void onHit(int type) {
            if (lastAttackTick > Tasks.getTick() - 5
                    && stateMachine.getState() != STATE_PULL_UP
                    && stateMachine.getState() != STATE_DOWN_ATTACK) {
                stateMachine.setState(STATE_PULL_UP);
            }
            //            else if (type == HIT_ATTACK && stateMachine.getState() != STATE_PULL_UP &&
            // stateMachine.getState() != STATE_DOWN_ATTACK) {
            //                stateMachine.setState(STATE_FOLLOW);
            //            }
        }
    }

    public static class SpearArua extends AbstractBotBehaviour implements HitListener {
        static final int STATE_NONE = 0;
        static final int STATE_FOLLOW = 1;
        static final int STATE_NEAR_FOLLOW = 2;
        static final int STATE_PULL_OVER = 3;
        int nearFollowTimer;
        int pullOverTimer;
        StateMachine stateMachine;

        public SpearArua() {
            this.stateMachine = new StateMachine(
                    STATE_NONE,
                    this::onStateUpdate,
                    this::onStateNone,
                    this::onStateFollow,
                    this::onStateNearFollow,
                    this::onStatePullOver);
            this.stateMachine.registerListener(STATE_NEAR_FOLLOW, this::onSwitchToNearFollow);
            this.stateMachine.registerListener(STATE_PULL_OVER, this::onSwitchToPullOver);
        }

        public int onStateUpdate(StateMachine machine, int state) {
            if (base.target == null) {
                return STATE_NONE;
            }
            return state;
        }

        public int onStateNone(StateMachine machine) {
            if (base.target != null) {
                return STATE_FOLLOW;
            }
            machine.markForEndState();
            movementDirection = Vec3d.ZERO;
            return STATE_NONE;
        }

        private Vec3d getTargetPosition() {
            Vec3d predictedPosition = base.usePredictor.get()
                    ? PositionPredict.INSTANCE
                            .getPredictor(base.target)
                            .predict(2, PositionPredict.Mode.PREDICTOR_NV.ordinal(), 10)
                    : base.target.getPos();
            Vec3d delta = predictedPosition.subtract(base.target.getPos());
            if (base.target.isOnGround() || CollisionUtil.isEntitySupported(base.target)) {
                return base.target.getEyePos().add(delta);
            }

            return base.target.getBoundingBox().getCenter().add(delta);
        }

        public int onStateFollow(StateMachine machine) {

            if (mc.player
                            .getEyePos()
                            .squaredDistanceTo(base.target.getBoundingBox().getCenter())
                    < MathUtils.s2(getActiveRange())) {
                return STATE_NEAR_FOLLOW;
            }
            machine.markForEndState();
            /// compute their
            Vec3d originalLook = getTargetPosition().subtract(mc.player.getEyePos());
            if (canAdjustMovement()) {
                if (adjustMovementForSpear((PlayerEntity) base.target, originalLook, false)) {
                    return STATE_FOLLOW;
                }
            }
            movementDirection = originalLook;
            return STATE_FOLLOW;
        }

        private boolean canAdjustMovement() {
            return base.spearAntiSpear.get() && base.isTargetUsingSpear();
        }

        private boolean adjustMovementForSpear(PlayerEntity otherShit, Vec3d originalLook, boolean expand) {
            // shit not work
            // handle their shit ass spear

            // filter run away
            //            if (otherShit.getRotationVector().dotProduct(mc.player.getPos().subtract(otherShit.getPos()))
            // < 0) {
            //                return false;
            //            }
            if (otherShit.squaredDistanceTo(mc.player.getPos())
                    < MathUtils.s2(getActiveRange() * 2 + base.spearAntiSpearExtraDistance.get() * 2)) {
                if (Tasks.getTick() % 6 < 3) {
                    return moveAdjust(originalLook);
                } else {
                    return movementPredictAdjust(originalLook);
                }
            }

            Box ourBox = expand ? mc.player.getBoundingBox().expand(0.85, 0.85, 0.85) : mc.player.getBoundingBox();
            Vec3d theirKnownMovement = PositionPredict.INSTANCE.predictKnownMovement(otherShit);
            Vec3d theirPredictedPos = PositionPredict.INSTANCE
                    .spearPredictArgument
                    .get()
                    .predict(otherShit); // predictFlyingPosition(otherShit, 2, 6);
            Vec3d facing = otherShit.getRotationVector();
            Debug.debug("Spear judgement", theirKnownMovement, theirPredictedPos, mc.player.getPos());
            double reachD = theirKnownMovement.dotProduct(facing);
            Vec3d theirPredictedEyePos = theirPredictedPos.add(0, otherShit.getEyeHeight(otherShit.getPose()), 0);
            Vec3d raycastStart = theirPredictedEyePos.add(facing.multiply(getMinRange()));
            Vec3d raycastEnd = theirPredictedEyePos.add(
                    facing.multiply(getActiveRange() + reachD + base.spearAntiSpearExtraDistance.get()));
            if (ourBox.raycast(raycastStart, raycastEnd).isPresent()) {
                return moveAdjust(originalLook);
            }
            return false;
            // do spear raytrace
        }

        private boolean moveAdjust(Vec3d originalLook) {
            //
            Debug.debug("Judget may hit");
            Vec3d originalLookHorizontal = originalLook.getHorizontal();
            Vec3d vertical = new Vec3d(0, 1, 0);
            Vec3d side = vertical.crossProduct(originalLookHorizontal);
            Vec3d revertDirection =
                    side.normalize().multiply(originalLookHorizontal.length()).add(0, originalLookHorizontal.y, 0);
            Vec3d testVector = revertDirection.normalize().multiply(0.5);
            Vec3d simulate = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testVector, false);
            if (simulate.squaredDistanceTo(testVector) < 0.1) {
                movementDirection = revertDirection;
                Debug.debug("JudgeA", movementDirection);
                RenderTasks.drawBoxMov(
                        mc.player.getBoundingBox(), revertDirection.normalize().multiply(1.7), 50, Color.BLUE);
                return true;
            } else {
                revertDirection = revertDirection.negate();
                testVector = testVector.negate();
                simulate = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testVector, false);
                if (simulate.squaredDistanceTo(testVector) < 0.1) {
                    movementDirection = revertDirection;
                    Debug.debug("JudgeB", movementDirection);
                    RenderTasks.drawBoxMov(
                            mc.player.getBoundingBox(),
                            revertDirection.normalize().multiply(1.7),
                            50,
                            Color.BLUE);
                    return true;
                }
            }
            return false;
        }

        private boolean movementPredictAdjust(Vec3d originalLook) {
            return false;
        }

        public int onStateNearFollow(StateMachine machine) {
            if ((base.currentInCombatRange && base.currentAction != TargetAction.ESCAPING)
                    || base.currentAction == TargetAction.TOWARDS) {
                if (!SpearEnhance.canSpearKineticAttack()) {
                    return STATE_PULL_OVER;
                }
            }
            if (mc.player
                            .getEyePos()
                            .squaredDistanceTo(base.target.getBoundingBox().getCenter())
                    > MathUtils.s2(getActiveRange())) {
                return STATE_FOLLOW;
            } else {
                //                else if (++nearFollowTimer > getMaxAttackPeriod()) {
                //                    // catch up
                //                    state = STATE_PULL_OVER;
                //                    nearFollowTimer = 0;
                //                }
                machine.markForEndState();
                Vec3d look = getTargetPosition().subtract(mc.player.getEyePos());
                if (canAdjustMovement()) {
                    if (adjustMovementForSpear((PlayerEntity) base.target, look, false)) {
                        return STATE_NEAR_FOLLOW;
                    }
                }
                movementDirection = look;
                return STATE_NEAR_FOLLOW;
            }
        }
        // todo: howto when combating
        public int onStatePullOver(StateMachine machine) {
            if (++pullOverTimer > getCooldown()) {
                return STATE_FOLLOW;
            } else {
                // calculate left time
                if (base.currentAction != null) {
                    if (base.currentAction == TargetAction.AFK
                            || base.currentAction == TargetAction.SLOW_SPEED
                            || (base.currentInCombatRange && base.currentAction != TargetAction.ESCAPING)) {
                        // stable
                        int leftTicks = getCooldown() - pullOverTimer;
                        double canChaseDistance = Math.max(0.0D, 3.4D * (leftTicks));
                        if (mc.player
                                        .getEyePos()
                                        .squaredDistanceTo(
                                                base.target.getBoundingBox().getCenter())
                                > MathUtils.s2(canChaseDistance)) {
                            return STATE_FOLLOW;
                        }
                    } else if (base.currentAction == TargetAction.ESCAPING) {
                        // chasing
                        // do not too close,
                        double canChaseDistance = getMinRange();
                        if (mc.player
                                        .getEyePos()
                                        .squaredDistanceTo(
                                                base.target.getBoundingBox().getCenter())
                                > MathUtils.s2(canChaseDistance)) {
                            return STATE_FOLLOW;
                        }
                    } else {
                        // meeting
                        // escape their attack range, can hit
                        double canChaseDistance = getActiveRange();
                        if (mc.player
                                        .getEyePos()
                                        .squaredDistanceTo(
                                                base.target.getBoundingBox().getCenter())
                                > MathUtils.s2(canChaseDistance)) {
                            return STATE_FOLLOW;
                        }
                    }
                }
            }
            machine.markForEndState();
            Vec3d look = getTargetPosition().subtract(mc.player.getEyePos());
            if (canAdjustMovement()) {
                if (adjustMovementForSpear((PlayerEntity) base.target, look, true)) {
                    return STATE_PULL_OVER;
                }
            }
            if (base.target.isOnGround() || CollisionUtil.isEntitySupported(base.target)) {
                if (look.lengthSquared() < getMinRange()) {
                    movementDirection = look.negate().add(0, 1, 0).multiply(10);
                } else {
                    movementDirection = look.negate().multiply(10);
                }
            } else {
                movementDirection = look.negate().multiply(10);
                movementDirection = movementDirection.withAxis(Direction.Axis.Y, Math.abs(movementDirection.y));
            }
            return STATE_PULL_OVER;
        }

        public void onSwitchToNearFollow(boolean isOn) {
            nearFollowTimer = 0;
        }

        public void onSwitchToPullOver(boolean isOn) {
            pullOverTimer = 0;
        }

        public double getActiveRange() {

            return 8.0D;
        }

        public double getMinRange() {
            return 1.0D;
        }

        public int getCooldown() {
            return 12;
        }

        @Override
        public synchronized void onUpdate() {
            super.onUpdate();
            if (mc.player.isFallFlying()) {
                stateMachine.step();
            } else {
                movementDirection = Vec3d.ZERO;
                stateMachine.setState(STATE_NONE);
            }
        }

        @Override
        public void onEnable() {
            stateMachine.setState(STATE_NONE);
            pullOverTimer = 0;
            nearFollowTimer = 0;
        }

        @Override
        public void onDisable() {}

        @Override
        public synchronized void onHit(int spear) {
            if (spear == HIT_SPEAR) {
                Debug.chat("Hit");
                nearFollowTimer = 0;
                stateMachine.setState(STATE_PULL_OVER);
                pullOverTimer = 0;
            }
        }
    }

    public static class WeaponArua extends AbstractBotBehaviour {

        @Override
        public void onUpdate() {}

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}
    }

    public static class LandingControl extends AbstractBotBehaviour {

        @Override
        public void onUpdate() {}

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}
    }

    public enum TargetAction {
        ESCAPING,
        TOWARDS,
        CIRCLING,
        SLOW_SPEED,
        AFK;
    }

    public enum Mode implements ConfigEnum {
        FOLLOW,
        MACE_ARUA,
        SPEAR_ARUA;

        @Override
        public String getConfigEnumType() {
            return "elytra_bot_mode";
        }
    }
}
