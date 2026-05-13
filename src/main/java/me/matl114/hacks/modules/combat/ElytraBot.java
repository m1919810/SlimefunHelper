package me.matl114.hacks.modules.combat;

import java.awt.*;
import java.util.HashMap;
import java.util.Locale;
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
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.algorithms.StateMachine;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ElytraBot extends BaseModule {
    public ElytraBot() {}

    public final FlagRef enable = flagBuilder(Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.enable"))
            .build();

    public final KeyBindRef keyBind = toggleHotkey(
                    Configs.COMBAT_CONFIG,
                    makePath("combat-bot.elytra-bot.hotkey"),
                    new MultiKeyBind(),
                    makePath("combat-bot.elytra-bot.enable"))
            .build();

    public final EnumRef<Mode> mode = builder(Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.mode"), Mode.class)
            .defaultValue(Mode.FOLLOW)
            .build();

    public final FlagRef autoFly = flagBuilder(
                    Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.auto-start-fallflying"))
            .build();

    public final DoubleRef maceHeight = builder(
                    Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.mace-height"), DoubleRef.TYPE)
            .defaultValue(3.0D)
            .build();

    public final FlagRef playerOnly = builder(
                    Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.player-only"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef onlyWhenNoWASD = flagBuilder(
                    Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.only-when-no-wasd"))
            .build();

    public final DoubleRef followOnGroundHeight = builder(
                    Configs.COMBAT_CONFIG,
                    makePath("combat-bot.elytra-bot.follow-on-ground-height-extra"),
                    Double.class)
            .defaultValue(0.5D)
            .validator(Configs.doubleRange(0.0D, 10.0D))
            .build();

    public final DoubleRef minimalAttackRange = builder(
                    Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.min-attack-range"), Double.class)
            .defaultValue(2.0D)
            .build();

    public final FlagRef render = flagBuilder(Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.render"))
            .build();

    public final KeyBindRef switchMode = hotkey(Configs.COMBAT_CONFIG, makePath("combat-bot.elytra-bot.switch-hotkey"))
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
    }

    Entity target;
    double chaseRange = 80;

    @Nullable
    AbstractBotBehaviour currentBehaviour;

    AbstractBotBehaviour defaultBehaviour;
    Map<Mode, AbstractBotBehaviour> behaviourMap = new HashMap<>();

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
            currentBehaviour.onUpdate();
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
                    Vec3d targetRender = currentBehaviour.followTarget;
                    RenderUtils.drawOutlinedBox(
                            stack, targetRender.add(RenderTasks.FROM), targetRender.add(RenderTasks.TO), Color.MAGENTA);
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
                || target.getPos().squaredDistanceTo(mc.player.getPos()) > chaseRange) {
            target = null;
        }
        if (target == null) {
            target = CombatTasks.getTargetSelector()
                    .searchAttackEntity(chaseRange, true, playerOnly.get() ? (e) -> e instanceof PlayerEntity : null);
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
        if (mc.player != null
                && mc.world != null
                && enable.get()
                && statusS2CPacket.getEntity(mc.world) == mc.player
                && statusS2CPacket.getStatus() == EntityStatuses.KINETIC_ATTACK) {
            if (currentBehaviour instanceof SpearListener sp) {
                sp.onSpearKinetic();
            }
        }
    }

    public static interface SpearListener {
        public void onSpearKinetic();
    }

    @Setter
    @Accessors(chain = true)
    public abstract static class AbstractBotBehaviour {
        ElytraBot base;
        Vec3d followTarget = null;
        // todo: update target considering blocks , can we async calculate to let
        // use pitch search
        public void onElytra(Event<EventContainer<FlightVelocity>> event) {
            if (followTarget != null) {
                Vec3d targetVec = followTarget.subtract(mc.player.getPos());
                double targetVecVelocity = targetVec.length();
                targetVec = targetVec
                        .normalize()
                        .multiply(Math.min(
                                targetVecVelocity, event.context.getValue().maxVelocity()));
                event.context.getValue().velocity(targetVec);
            }
        }

        public abstract void onUpdate();

        public void onInputEvent(Event<Void> eventInput) {}

        public void onAttack(Entity entity) {}

        public abstract void onEnable();

        public abstract void onDisable();
    }

    public static class Follower extends AbstractBotBehaviour {

        @Override
        public void onUpdate() {
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

    public static class MaceArua extends AbstractBotBehaviour {

        static final int STATE_PULL_UP = 1;
        static final int STATE_FOLLOW = 2;
        static final int STATE_WAIT_ATTACK = 3;
        static final int STATE_NONE = 0;
        static final int STATE_DOWN_ATTACK = 4;
        public StateMachine stateMachine;
        int startWaitAttack = -1;
        double maxHeightInAttack = Double.MIN_VALUE;

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
                return STATE_FOLLOW;
                //                followTarget = mc.player.getPos().subtract(0, -10, 0);
                //                machine.markForEndState();
                //                return STATE_DOWN_ATTACK;
            } else {
                double targetY = base.target.getY() + base.maceHeight.get();
                if (mc.player.getY() < targetY) {
                    followTarget = base.target.getPos().withAxis(Direction.Axis.Y, targetY + 0.5);
                    machine.markForEndState();
                    return STATE_PULL_UP;
                } else {
                    return STATE_FOLLOW;
                }
            }
        }

        public int onStateFollow(StateMachine machine) {
            followTarget = base.target.getPos();
            // already reach the target
            if (base.target.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                    < MathUtils.s2(Math.min(
                            base.minimalAttackRange.get(),
                            CombatTasks.getCombatExtra().getAttackRange()))) {
                scheduleAttack();
                machine.markForEndState();
                return STATE_WAIT_ATTACK;
            } else {
                boolean onGroundTargetAttack = base.target.isOnGround() || CollisionUtil.isEntitySupported(base.target);
                if (onGroundTargetAttack) {
                    followTarget = followTarget.add(0, base.followOnGroundHeight.get(), 0);
                }
            }
            machine.markForEndState();
            return STATE_FOLLOW;
        }

        public int onStateWaitAttack(StateMachine machine) {
            if (++startWaitAttack > 2) {
                return STATE_NONE;
            }
            machine.markForEndState();
            followTarget = mc.player.getPos();
            return STATE_WAIT_ATTACK;
        }

        public int onStateDownAttack(StateMachine machine) {
            machine.markForEndState();
            scheduleAttack();
            followTarget = mc.player.getPos();
            return STATE_WAIT_ATTACK;
        }

        public void onStartWaitAttack(boolean on) {
            startWaitAttack = 0;
        }

        @Override
        public void onUpdate() {
            if (mc.player.isFallFlying()) {
                maxHeightInAttack = Math.max(maxHeightInAttack, mc.player.getY());
                stateMachine.step();
                // todo: consider cooldown, do not attack too fast
                if (attackFlag) {
                    if (base.target != null) {
                        if (mc.player.getY() < maxHeightInAttack - 1.5D) {
                            Attack.AttackSettings settings =
                                    CombatTasks.getAttack().createAttackSettings();
                            CombatTasks.getAttack().attackEntity(base.target, settings.withMaceSwap(true));
                        } else if (mc.player.getAttackCooldownProgress(0.5F) > 0.95F) {
                            // can not deal mace attack anyway
                            Attack.AttackSettings settings =
                                    CombatTasks.getAttack().createAttackSettings();
                            CombatTasks.getAttack().attackEntity(base.target, settings);
                        }
                    }
                    maxHeightInAttack = mc.player.getY();
                    attackFlag = false;
                }
            } else {
                stateMachine.setState(STATE_NONE);
            }
        }

        boolean attackFlag = false;

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
            if (mc.player.isFallFlying()) {
                stateMachine.setState(STATE_PULL_UP);
            }
        }
    }

    public static class SpearArua extends AbstractBotBehaviour implements SpearListener {
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
            return 2.0D;
        }

        public int getMaxAttackPeriod() {
            // todo: complete
            return 4;
        }

        public int getCooldown() {
            return 12;
        }

        Vec3d lastHitPos = Vec3d.ZERO;

        @Override
        public void onUpdate() {
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
                } else if (++nearFollowTimer > getMaxAttackPeriod()) {
                    // catch up
                    lastHitPos = base.target.getEyePos();
                    state = STATE_PULL_OVER;
                    nearFollowTimer = 0;
                }
                if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos()) < MathUtils.s2(minRange)) {
                    lastHitPos = base.target.getEyePos();
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
                    Vec3d currentPos = base.target.getEyePos();
                    if (lastHitPos != null) {
                        if (currentPos.squaredDistanceTo(lastHitPos) < MathUtils.s2(getMinRange())) {
                            // stable
                            int leftTicks = getCooldown() - pullOverTimer;
                            double canChaseDistance = Math.max(0.0D, 3.4D * leftTicks - getActiveRange());
                            if (mc.player.getEyePos().squaredDistanceTo(base.target.getEyePos())
                                    > MathUtils.s2(canChaseDistance)) {
                                pullOverTimer = 0;
                                state = STATE_FOLLOW;
                            }
                        } else if (currentPos
                                        .subtract(lastHitPos)
                                        .dotProduct(currentPos.subtract(mc.player.getEyePos()))
                                > 0) {
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
                    followTarget =
                            mc.player.getPos().subtract(base.target.getEyePos().subtract(mc.player.getEyePos()));
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
        public void onSpearKinetic() {
            Debug.chat("Hit");
            nearFollowTimer = 0;
            state = STATE_PULL_OVER;
            lastHitPos = base.target.getEyePos();
            pullOverTimer = 0;
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

    public enum Mode implements ConfigEnum {
        FOLLOW,
        MACE_ARUA,
        SPEAR_ARUA;

        @Override
        public String getConfigEnumType() {
            return "elytra_bot_mode";
        }

        @Override
        public Text getDisplay() {
            return Text.of(name().toLowerCase(Locale.ROOT));
        }
    }
}
