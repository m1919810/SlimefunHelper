package me.matl114.hacks.modules.combat;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
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
            .defaultValue(3.6D)
            .build();

    public final DoubleRef followOnGroundHeight = builder(elytraBot.add("follow-on-ground-height-extra"), Double.class)
            .defaultValue(0.5D)
            .validator(Configs.doubleRange(0.0D, 10.0D))
            .show(() -> mode.get().isNotIn(Mode.SPEAR_ARUA))
            .build();

    public final DoubleRef maceHeight = builder(elytraBot.add("mace-height"), DoubleRef.TYPE)
            .defaultValue(10.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef minimalAttackRange = builder(elytraBot.add("min-attack-range"), Double.class)
            .defaultValue(2.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final IntRef maceRemainPullUpTick = builder(elytraBot.add("mace-max-extra-pull-up-tick"), IntRef.TYPE)
            .defaultValue(20)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef combatSmoothFlight = flagBuilder(elytraBot.add("combat-smooth-flight"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

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
    int afkTicker = 0;
    double speedMultiplier = 1.0D;

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

    public void updateTargetAction() {
        if (target != lastTarget) {
            lastTarget = target;
            lastTargetHitBox = null;
            currentAction = null;
            afkTicker = 0;
        }
        if (target != null) {
            // initialize pos
            if (lastTargetHitBox == null) {
                lastTargetHitBox = target.getBoundingBox();
                currentAction = TargetAction.AFK;
            }
            if (lastTargetHitBox.squaredMagnitude(mc.player.getEyePos()) < MathUtils.s2(combatRange.get())) {
                currentAction = TargetAction.COMBATING;
            } else {
                // speed < 1, we can easily handle this speed
                Vec3d lastTargetCenter = lastTargetHitBox.getCenter();
                Vec3d targetCenter = target.getBoundingBox().getCenter();
                if (lastTargetCenter.squaredDistanceTo(targetCenter) < 0.25) {
                    afkTicker++;
                    if (afkTicker > 20) {
                        currentAction = TargetAction.AFK;
                    }
                } else if (lastTargetCenter.squaredDistanceTo(targetCenter) < 1) {
                    currentAction = TargetAction.SLOW_SPEED;
                } else {
                    // it is escaping
                    if (lastTargetCenter
                                    .subtract(targetCenter)
                                    .dotProduct(target.getPos().subtract(mc.player.getPos()))
                            > 0) {
                        currentAction = TargetAction.ESCAPING;
                    } else {
                        currentAction = TargetAction.TOWARDS;
                    }
                }
            }
            lastTargetHitBox = target.getBoundingBox();
        }
    }

    public void onEntityPreTick(Event<Entity> event) {
        if (event.context == mc.player
                && enable.get()
                && autoFly.get()
                && !mc.player.isFallFlying()
                && currentBehaviour != null
                && !Objects.equals(mc.player.getPos(), currentBehaviour.followTarget)) {
            if (mc.player.isOnGround()) {
                mc.options.jumpKey.setPressed(true);
            } else if (mc.player.checkFallFlying()) {
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
                    Vec3d targetRender = currentBehaviour.followTarget;
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
            if (onlyWhenNoWASD.get()) {
                PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                if (input.hasMovementControl()) {
                    return;
                }
            }
            AbstractBotBehaviour behaviour = currentBehaviour;
            if (behaviour != null) {
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
        Vec3d followTarget = null;
        // todo: update target considering blocks , can we async calculate to let
        // use pitch search

        // todo: calculate reachable, if entity can reach reach distance
        public void onElytra(Event<EventContainer<FlightVelocity>> event) {
            if (followTarget != null && event.context.getValue().mode() == FlightVelocity.Mode.ELYTRA_FLIGHT) {
                Vec3d targetVec = followTarget.subtract(mc.player.getPos());
                double targetVecVelocity = targetVec.length();
                targetVec = targetVec
                        .normalize()
                        .multiply(Math.min(
                                targetVecVelocity, event.context.getValue().maxVelocity() * base.speedMultiplier));
                event.context.getValue().velocity(targetVec);
            }
        }

        public void onUpdate() {
            base.speedMultiplier = 1.0D;
        }

        public void onInputEvent(Event<Void> eventInput) {}

        public void onAttack(Entity entity) {}

        public abstract void onEnable();

        public abstract void onDisable();
    }

    public static class Follower extends AbstractBotBehaviour {
        // todo: add in-hole behaviour, add hole-esp related, add landing
        @Override
        public void onUpdate() {
            super.onUpdate();
            if (base.target != null) {
                followTarget = base.target.getPos();
                if (base.target.isOnGround() || CollisionUtil.isEntitySupported(base.target)) {
                    followTarget = followTarget.add(0, base.followOnGroundHeight.get(), 0);
                }
            } else {
                followTarget = mc.player.getPos();
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
                followTarget = mc.player.getPos();
                return STATE_NONE;
            }
            return state;
        }

        public int onStateNone(StateMachine machine) {
            if (base.target != null) {
                return STATE_PULL_UP;
            }
            followTarget = mc.player.getPos();
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
                if ((base.currentAction == TargetAction.COMBATING || base.currentAction == TargetAction.TOWARDS)) {
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
            if (PlayerStateManager.INSTANCE.fallDistance < 1E-6 && lastFallDistance > 1.5F) {
                // we trigger falldistance reset during chase
                return STATE_PULL_UP;
            }
            // already reach the target
            if (base.target.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                    < MathUtils.s2(Math.min(
                            base.minimalAttackRange.get(),
                            CombatTasks.getCombatExtra().getAttackRange()))) {
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
            if (base.combatSmoothFlight.get()) {
                double combatRange = base.combatRange.get();
                if (base.currentAction == TargetAction.COMBATING) {
                    //
                    Vec3d towardsVector = mc.player.getPos().subtract(base.target.getPos());
                    Vec3d vertical = MathUtils.getVerticalWithSameXZ(towardsVector);
                    // ensure y > 0
                    if (vertical.y < 0) {
                        vertical = vertical.negate();
                    }
                    followTarget = mc.player.getPos().add(vertical.multiply(10));
                    return;
                }
                if (base.target.getY() >= mc.player.getY()) {
                    Vec3d center = base.target.getBoundingBox().getCenter();
                    double radius = combatRange + (base.target.getBoundingBox().getLengthY() / 2.0D);
                    Pair<Vec3d, Vec3d> tangents = MathUtils.getTangentWithSameXZ(center, radius, mc.player.getEyePos());
                    Vec3d vec3d = tangents.getFirst();
                    Vec3d vec3d2 = tangents.getSecond();
                    Vec3d vec3d3 = vec3d.y < vec3d2.y ? vec3d2 : vec3d;
                    if (vec3d3.y > 0) {
                        followTarget = mc.player.getPos().add(vec3d3.multiply(10));
                        return;
                    }
                }
            }
            followTarget = base.target
                    .getPos()
                    .withAxis(Direction.Axis.Y, (base.target.getY() + (2 * base.maceHeight.get()) + 0.5));
        }

        private void setTargetToPlayer(boolean waitAttack) {
            followTarget = base.target.getPos();
            double minimalHeightLow = waitAttack
                    ? (CombatExtra.INSTANCE.getAttackRange() + mc.player.getEyeHeight(mc.player.getPose()))
                    : 1.5;
            if (base.target.isOnGround() || CollisionUtil.isEntitySupported(base.target)) {
                // handle on ground target
                followTarget = followTarget.add(0, base.followOnGroundHeight.get(), 0);
            } else if (base.target.getY() > mc.player.getY()
                    && base.target.getY() < mc.player.getY() + minimalHeightLow) {
                // do not go up if it is just a bit higher than
                followTarget = followTarget.withAxis(Direction.Axis.Y, mc.player.getY());
            }
        }
        // compat delay attack shit, add cd,
        public int onStateWaitAttack(StateMachine machine) {
            if (++startWaitAttack > 2) {
                return STATE_NONE;
            }
            machine.markForEndState();
            // stay!
            setTargetToPlayer(true);
            return STATE_WAIT_ATTACK;
        }

        int startDownAttackTick = 0;
        // try create attack chance
        public int onStateDownAttack(StateMachine machine) {
            // take argument
            // down about 1.7 * 4 = 6.8 blocks
            //
            if (startDownAttackTick < 3
                    && (base.currentAction == TargetAction.COMBATING || base.currentAction == TargetAction.TOWARDS)) {
                startDownAttackTick += 1;
                machine.markForEndState();
                followTarget = base.target.getPos().subtract(0, 10, 0);
                Vec3d look = followTarget.subtract(mc.player.getPos()).normalize();
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

            if (base.target.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                    < MathUtils.s2(Math.min(
                            base.minimalAttackRange.get(),
                            CombatTasks.getCombatExtra().getAttackRange()))) {
                scheduleAttack();
                followTarget = mc.player.getPos();
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
        public void onUpdate() {
            super.onUpdate();
            if (mc.player.isFallFlying()) {
                maxHeightInAttack = Math.max(maxHeightInAttack, mc.player.getY());
                stateMachine.step();
                // Debug.info("State", stateMachine.getState(), "height", PlayerStateManager.INSTANCE.fallDistance);
                // todo: consider cooldown, do not attack too fast
                if (attackFlag) {
                    if (base.target != null && lastAttackTick <= Tasks.getTick() - 3) {
                        if (PlayerStateManager.INSTANCE.fallDistance > 1.5) {
                            Attack.AttackSettings settings =
                                    CombatTasks.getAttack().createAttackSettings();
                            CombatTasks.getAttack()
                                    .attackEntity(
                                            base.target,
                                            settings.withMaceSwap(true).withInvSwap(false));
                        } else if (mc.player.getAttackCooldownProgress(0.5F) > 0.95F) {
                            // can not deal mace attack anyway
                            Attack.AttackSettings settings =
                                    CombatTasks.getAttack().createAttackSettings();
                            CombatTasks.getAttack().attackEntity(base.target, settings);
                        }
                        lastAttackTick = Tasks.getTick();
                    }
                    maxHeightInAttack = mc.player.getY();
                    attackFlag = false;
                }
            } else {
                stateMachine.setState(STATE_NONE);
            }
            lastFallDistance = PlayerStateManager.INSTANCE.fallDistance;
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
        public void onAttack(Entity entity) {
            if (mc.player.isFallFlying()
                    && stateMachine.getState() != STATE_PULL_UP
                    && stateMachine.getState() != STATE_DOWN_ATTACK) {
                stateMachine.setState(STATE_FOLLOW);
            }
        }

        @Override
        public void onHit(int type) {
            if ((type == HIT_MACE || type == HIT_ATTACK)
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
        int state;
        static int STATE_NONE = 0;
        static int STATE_FOLLOW = 1;
        static int STATE_NEAR_FOLLOW = 2;
        static int STATE_PULL_OVER = 3;
        int nearFollowTimer;
        int pullOverTimer;

        public double getActiveRange() {
            if (SpearEnhance.isUsingSpear()) {}

            return 5.0D;
        }

        public double getMinRange() {
            return 1.0D;
        }

        public int getCooldown() {
            return 12;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (base.target == null) {
                state = STATE_NONE;
            }
            if (state == STATE_NONE) {
                if (base.target != null) {
                    state = STATE_FOLLOW;
                }
            }
            double activeRange = getActiveRange();
            double minRange = getMinRange();
            if (state == STATE_FOLLOW) {
                if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos()) < MathUtils.s2(activeRange)) {
                    state = STATE_NEAR_FOLLOW;
                }
            }
            if (state == STATE_NEAR_FOLLOW) {
                // enermy leave attack range, continue follow,
                if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos()) > MathUtils.s2(activeRange)) {
                    state = STATE_FOLLOW;
                    nearFollowTimer = 0;
                } else
                //                else if (++nearFollowTimer > getMaxAttackPeriod()) {
                //                    // catch up
                //                    state = STATE_PULL_OVER;
                //                    nearFollowTimer = 0;
                //                }
                if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos()) <= MathUtils.s2(minRange)) {
                    state = STATE_PULL_OVER;
                    nearFollowTimer = 0;
                }

            } else {
                nearFollowTimer = 0;
            }
            if (state == STATE_PULL_OVER) {
                if (++pullOverTimer > getCooldown()) {
                    pullOverTimer = 0;
                    state = STATE_FOLLOW;
                } else {
                    // calculate left time
                    if (base.currentAction != null) {
                        if (base.currentAction == TargetAction.AFK
                                || base.currentAction == TargetAction.SLOW_SPEED
                                || base.currentAction == TargetAction.COMBATING) {
                            // stable
                            int leftTicks = getCooldown() - pullOverTimer;
                            double canChaseDistance = Math.max(0.0D, 3.4D * (leftTicks) - getActiveRange());
                            if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos())
                                    > MathUtils.s2(canChaseDistance)) {
                                pullOverTimer = 0;
                                state = STATE_FOLLOW;
                            }
                        } else if (base.currentAction == TargetAction.ESCAPING) {
                            // chasing
                            // do not too close,
                            double canChaseDistance = getMinRange();
                            if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos())
                                    > MathUtils.s2(canChaseDistance)) {
                                pullOverTimer = 0;
                                state = STATE_FOLLOW;
                            }
                        } else {
                            // meeting
                            // escape their attack range, can hit
                            double canChaseDistance = getActiveRange();
                            if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos())
                                    > MathUtils.s2(canChaseDistance)) {
                                pullOverTimer = 0;
                                state = STATE_FOLLOW;
                            }
                        }
                    }
                }
            }
            if (base.target != null) {
                if (state == STATE_FOLLOW || state == STATE_NEAR_FOLLOW) {
                    followTarget =
                            mc.player.getPos().add(base.target.getEyePos().subtract(mc.player.getEyePos()));
                } else if (state == STATE_PULL_OVER) {
                    Vec3d look = base.target.getEyePos().subtract(mc.player.getEyePos());
                    if (look.y > 0) {
                        look = look.withAxis(Direction.Axis.Y, 0);
                    }
                    followTarget = mc.player.getPos().subtract(look.multiply(10));
                } else if (state == STATE_NONE) {
                    followTarget = mc.player.getPos();
                }
            } else {
                followTarget = mc.player.getPos();
            }
        }

        @Override
        public void onEnable() {
            state = 0;
            pullOverTimer = 0;
            nearFollowTimer = 0;
        }

        @Override
        public void onDisable() {}

        @Override
        public void onHit(int spear) {
            if (spear == HIT_SPEAR) {
                Debug.chat("Hit");
                nearFollowTimer = 0;
                state = STATE_PULL_OVER;

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
        COMBATING,
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
