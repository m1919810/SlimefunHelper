package me.matl114.hacks.modules.combat;

import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.events.impl.PosUpdate;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.task.DisablerManager;
import me.matl114.hacks.modules.task.PostManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.versioned.SupportVersion;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class CombatExtra extends BaseModule {
    public static CombatExtra INSTANCE;

    public CombatExtra() {
        super("CombatExtra");
        INSTANCE = this;
    }

    public final ModulePath combat = makePath(Configs.COMBAT_CONFIG, "attack");

    public final DoubleRef range = doubleBuilder(combat.add("att-range"))
            .defaultValue(0.0D)
            .validator(Configs.doubleRange(0.0D, 64.0D))
            .build();

    public final DoubleRef boatAttackRange = doubleBuilder(combat.add("boat-reach-range"))
            .defaultValue(0.0D)
            .validator(Configs.doubleRange(0.0D, 64.0D))
            .build();

    public final FlagRef spearReach =
            flagBuilder(combat.add("grim-spear-reach")).build();

    public final FlagRef shieldPredict =
            flagBuilder(combat.add("shielding-setback-log")).build();

    public final FlagRef useAttack = flagBuilder(combat.add("shielding-attack")).build();

    public final FlagRef rideAttack = flagBuilder(combat.add("riding-attack")).build();

    public final FlagRef noCooldown = flagBuilder(combat.add("cancel-interval")).build();

    private boolean ridingBypass(Entity entity) {
        return entity.hasVehicle();
    }

    public double getAttackAtTargetRange(Entity entity) {
        double d = ridingBypass(mc.player) || ridingBypass(entity) ? boatAttackRange.get() : range.get();
        return getAttackRangeAttribute() + d;
    }

    public double getAttackRange() {
        double d = ridingBypass(mc.player) ? boatAttackRange.get() : range.get();
        return getAttackRangeAttribute() + d;
    }

    public double getAttackRangeAttribute() {
        return mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(EntityTrackerUpdateS2CPacket.class), this::onShieldSetback);
        registerListener(
                Listener.getPacketPoint().getChannel(CooldownUpdateS2CPacket.class), this::asyncUpdateShieldCooldown);
        registerListener(Listener.getAttackAction(), this::onUseAttackNoSlow);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onMovementPacket);
        registerListener(Listener.getPostGameTick(), this::onClientTickEndPacket);
        registerListener(Listener.getEntityTrackedPositionUpdate(), this::onTrackedPosition);
        registerListener(Listener.getPacketPostHandlePoint().getChannel(EntityS2CPacket.class), this::onEntityPacket);
    }

    public int shieldExceptionspam = 0;

    public void onUseAttackNoSlow() {
        //        if (shieldAttack.get() && mc.player.isUsingItem()) {
        //            MovTasks.getNoSlowDown().setPreAttackUseTick();
        //        }
    }

    public void onPreInputTick() {
        if (checkNull()) return;
    }

    public void onShieldSetback(Event<EntityTrackerUpdateS2CPacket> trackerUpdateS2CPacketEvent) {
        if (trackerUpdateS2CPacketEvent.isCancelled()) {
            return;
        }
        var trackerUpdateS2CPacket = trackerUpdateS2CPacketEvent.context();
        if (shieldPredict.get()
                && mc.player != null
                && trackerUpdateS2CPacket.id() == mc.player.getId()
                && mc.player.isUsingItem()
                && VItem.getInstance().isShield(mc.player.getActiveItem())
                && !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getActiveItem())) {
            // shield not in cooldown
            // block shield from
            for (var trackerUpdate : trackerUpdateS2CPacket.trackedValues()) {
                // the ordinal  of LIVING FLAGS in LivingEntity, may vary with versionsl pls check
                if (trackerUpdate.id() == VDataFlag.ID_LIVING_FLAGS) {
                    byte byteValue = ((Number) trackerUpdate.value()).byteValue();
                    boolean bl = (byteValue & (1 << VDataFlag.USING_ITEM_FLAG_INDEX)) > 0;
                    Hand hand = (byteValue & (1 << VDataFlag.OFFHAND_ACTIVE_FLAG_INDEX)) > 0
                            ? Hand.OFF_HAND
                            : Hand.MAIN_HAND;
                    // cooldown should be ok,
                    // the only position the server disable shield correctly should be cooldown
                    // so we kick it back
                    if (!bl && hand == mc.player.getActiveHand()) {
                        // using shield , but banned
                        if (shieldExceptionspam + 4 < Tasks.getTick()) {
                            shieldExceptionspam = Tasks.getTick();
                            Debug.chat(Text.literal("[AC] 阻挡异常盾牌禁用").formatted(Formatting.RED));
                        }
                        // trackerUpdateS2CPacketEvent.cancel();
                    }
                }
            }
        }
    }

    public void asyncUpdateShieldCooldown(Event<CooldownUpdateS2CPacket> packetEvent) {
        if (packetEvent.isCancelled()) {
            return;
        }
        CooldownUpdateS2CPacket packet = packetEvent.context();
        if (packet.cooldown() > 0) {
            try {
                synchronized (CombatExtra.class) {
                    // async update, synchronize to protect concurrent cooldown update,
                    mc.player.getItemCooldownManager().set(packet.cooldownGroup(), packet.cooldown());
                    //                if(mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == shield){
                    //
                    //                }
                    // consume packet
                    packetEvent.cancel();
                }

            } catch (Throwable e) {
                // any exception

            }
        }
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        if (event.context().getValue().isOptionPreset()) return;
        switch (event.context.getValue()) {
            case HACKING -> {
                range.set(3.0);
                boatAttackRange.set(3.0);
            }
            case AC_GRIM, AC_GRIM_LEGACY -> {
                range.set(0.0);
                boatAttackRange.set(3.0);
            }
            default -> {
                range.set(0.0);
                boatAttackRange.set(0.0);
            }
        }
    }

    public void onMovementPacket(Event<PlayerMoveC2SPacket> packetEvent) {
        if (packetEvent.isCancelled()) {
            return;
        }
        var access = PlayerMoveC2SPacketAccess.of(packetEvent.context);
        if (access.getCause() != PlayerMoveC2SPacketAccess.Cause.SET_BACK
                && access.getCause() != PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP) {
            mc.world.getEntities().forEach(entity -> {
                if (entity instanceof LivingEntity) {
                    tickEntity(entity);
                }
            });
        }
    }

    private void tickEntity(Entity entity) {
        getTracker(entity).onMovement(maySkipInterpolationTick());
    }

    public boolean maySkipInterpolationTick() {
        boolean tickReliable = !DisablerManager.INSTANCE.isGrimSelfCheckDisabled()
                && (ViaFabricPlusHooks.isSupportEndTick() || mc.player.hasVehicle());
        return !tickReliable;
    }

    public void onClientTickEndPacket() {
        if (ViaFabricPlusHooks.isSupportEndTick() && !PlayerStateManager.INSTANCE.lastTickHasMovement) {
            mc.world.getEntities().forEach(entity -> {
                if (entity instanceof LivingEntity) {
                    tickEntity(entity);
                }
            });
        }
    }

    public void onTrackedPosition(Event<PosUpdate> event) {
        if (event.context.entity() instanceof LivingEntity living) {
            getTracker(living)
                    .onEntityPos(
                            living,
                            event.context.newPos(),
                            !living.hasVehicle() && !ViaFabricPlusHooks.isSupportEndTick());
        }
    }

    /**
     * After vanilla applied an {@link EntityS2CPacket}. Every position update already went through
     * {@code TrackedPosition.setPos} ({@link #onTrackedPosition}); only rotation-only packets skip it.
     */
    public void onEntityPacket(Event<EntityS2CPacket> event) {
        EntityS2CPacket packet = event.context;
        if (packet.isPositionChanged() || !packet.hasRotation() || mc.world == null) return;
        if (!(packet.getEntity(mc.world) instanceof LivingEntity living) || living.isLogicalSideForUpdatingMovement())
            return;
        getTracker(living).onRotationOnly(living, !living.hasVehicle() && !ViaFabricPlusHooks.isSupportEndTick());
    }

    private static final String KEY_TRACKER = "slimefunhelper:combat_extra/position_tracker";

    public LivingPositionTracker getTracker(Entity entity) {
        return ((MetadataHolder) entity).getMetadata().getOrPut(this, KEY_TRACKER, LivingPositionTracker::new);
    }

    public static class LivingPositionTracker {
        private InterpolationData oldPacketLocation;
        private InterpolationData newPacketLocation;
        private Vec3d trackedPosition;

        public void onEntityPos(LivingEntity entity, Vec3d pos, boolean maySkipTick) {
            // 1.21.11: getInterpolator() == null. 1.21.4 has no PositionInterpolator; every
            // LivingEntity lerps tracked positions except shulkers, which reset bodyTrackingIncrements to 0.
            boolean instant = entity instanceof ShulkerEntity;
            int interpolationSteps = instant ? 1 : 3;
            if (instant) {
                trackedPosition = pos;
                oldPacketLocation = null;
                newPacketLocation = new InterpolationData(pos, interpolationSteps, false);
                return;
            }
            if (trackedPosition == null || newPacketLocation == null) {
                trackedPosition = pos;
                oldPacketLocation = null;
                newPacketLocation = new InterpolationData(this.trackedPosition, interpolationSteps, maySkipTick);
            } else {
                trackedPosition = pos;
                oldPacketLocation = newPacketLocation;
                newPacketLocation = new InterpolationData(
                        oldPacketLocation.possiblePositions(), trackedPosition, interpolationSteps, maySkipTick);
                PostManager.INSTANCE.addNextTransactionAction((ch) -> oldPacketLocation = null);
            }
        }
        /**
         * {@code !packet.isPositionChanged() && packet.hasRotation()}: never reaches setPos, so
         * {@link #onEntityPos} does not see it. How the client treats it depends on the protocol
         * version the server sees (Grim {@code LegacyEntityInterpolation.begin}).
         */
        public void onRotationOnly(LivingEntity entity, boolean maySkipTick) {
            if (entity instanceof ShulkerEntity || trackedPosition == null || newPacketLocation == null) return;
            SupportVersion protocol = ViaFabricPlusHooks.getInstance().getCurrentVersion();
            oldPacketLocation = newPacketLocation;
            Box possible = oldPacketLocation.possiblePositions();
            // MC-255263: the entity freezes (target = current) on 1.15-1.20.1 and 1.21.5-1.21.8;
            // 1.20.2-1.21.4 and 1.21.9+ restart the lerp towards the server position.
            boolean freeze = (protocol.isHigherOrEqualTo(21, 5) && protocol.isLowerOrEqualTo(21, 8))
                    || (protocol.isHigherOrEqualTo(15, 1) && protocol.isLowerOrEqualTo(20, 1));
            newPacketLocation = freeze
                    ? InterpolationData.frozen(possible)
                    : new InterpolationData(possible, trackedPosition, 3, maySkipTick);
            PostManager.INSTANCE.addNextTransactionAction((ch) -> oldPacketLocation = null);
        }

        // 傻逼grim又发力了。依旧跳刻大法
        public void onMovement(boolean maySkipTick) {
            if (newPacketLocation == null) return;
            newPacketLocation.step(oldPacketLocation == null, maySkipTick);
            if (oldPacketLocation != null) {
                oldPacketLocation.step(true, maySkipTick);
                newPacketLocation.includePossibleStartingRange(oldPacketLocation.possiblePositions());
            }
        }

        public Box getInteractBox(Entity entity) {
            if (newPacketLocation == null) return entity.getBoundingBox();
            // 傻逼grim为什么会想到能用这种纯比操作来检测reach。不检测interpolate反而将所有可行box直接取轴线交集吗
            if (oldPacketLocation == null) {
                return expand(newPacketLocation.possiblePositions(), entity).union(entity.getBoundingBox());
            } else {
                return expand(oldPacketLocation.possiblePositions(), entity)
                        .union(expand(newPacketLocation.possiblePositions(), entity))
                        .union(entity.getBoundingBox());
            }
        }

        public Box getNextInteractBox(Entity entity, int ticksLater, boolean maySkipTick) {
            return getNextInterpolatedBox(entity, ticksLater, maySkipTick);
        }

        public Box getNextInterpolatedBox(Entity entity, int ticksLater, boolean maySkipTick) {
            if (ticksLater <= 0) return getInteractBox(entity);
            if (newPacketLocation == null) return entity.getBoundingBox();
            return predictInterpolation(entity, ticksLater, maySkipTick);
        }

        public Vec3d getNextInterpolatedPosition(Entity entity, int ticksLater, boolean maySkipTick) {
            if (ticksLater <= 0) return entity.getPos();
            if (newPacketLocation == null) {
                Vec3d tracked = entity.getTrackedPosition().getPos();
                return tracked.equals(Vec3d.ZERO) ? entity.getPos() : tracked;
            }
            return getNextInterpolatedBox(entity, ticksLater, maySkipTick)
                    .getCenter()
                    .subtract(0.0, entity.dimensions.height() / 2.0, 0.0);
        }

        private Box predictInterpolation(Entity entity, int ticksLater, boolean maySkipTick) {
            InterpolationData predictedOld = oldPacketLocation == null ? null : oldPacketLocation.copy();
            InterpolationData predictedNew = newPacketLocation.copy();
            for (int i = 0; i < ticksLater; i++) {
                predictedNew.step(predictedOld == null, maySkipTick);
                if (predictedOld != null) {
                    predictedOld.step(true, maySkipTick);
                    predictedNew.includePossibleStartingRange(predictedOld.possiblePositions());
                }
            }

            Box predictedBox = expand(predictedNew.possiblePositions(), entity).union(entity.getBoundingBox());
            if (predictedOld != null) {
                predictedBox = predictedBox.union(expand(predictedOld.possiblePositions(), entity));
            }
            return predictedBox;
        }

        private Box expand(Box box, Entity entity) {
            return box.expand(entity.dimensions.width() / 2, 0, entity.dimensions.width() / 2)
                    .stretch(0, entity.dimensions.height(), 0);
        }
    }

    public static final class InterpolationData {
        private final int steps;
        private final Vec3d target;
        private Box startRange;
        /** MC-255263 freeze: the entity stays at {@link #startRange}, the lerp never advances. */
        private boolean frozen;

        private int lowStep;
        private int highStep;

        public InterpolationData(Vec3d stable, int steps, boolean maySkipTicks) {
            this.steps = steps;
            this.target = stable;
            this.startRange = new Box(stable, stable);
            this.lowStep = 0;
            this.highStep = maySkipTicks ? steps : 0;
        }

        public InterpolationData(Box startRange, Vec3d target, int steps, boolean maySkipTicks) {
            this.startRange = startRange;
            this.target = target;
            this.steps = steps;
            this.lowStep = 0;
            this.highStep = maySkipTicks ? steps : 0;
        }

        /** Grim's finished-location constructor: the entity stays at {@code location}. */
        public static InterpolationData frozen(Box location) {
            InterpolationData data = new InterpolationData(location, location.getCenter(), 1, false);
            data.frozen = true;
            return data;
        }

        public Box possiblePositions() {
            return interpolate(lowStep).union(interpolate(highStep));
        }

        private Box interpolate(int step) {
            double t = (double) step / steps;
            return new Box(
                    lerp(startRange.minX, target.x, t),
                    lerp(startRange.minY, target.y, t),
                    lerp(startRange.minZ, target.z, t),
                    lerp(startRange.maxX, target.x, t),
                    lerp(startRange.maxY, target.y, t),
                    lerp(startRange.maxZ, target.z, t));
        }

        public void step(boolean increase, boolean maySkipTick) {
            if (frozen) return;
            if (maySkipTick) this.highStep = steps;
            if (increase) {
                lowStep = Math.min(lowStep + 1, steps);
            }
            this.highStep = Math.min(highStep + 1, steps);
        }

        public void includePossibleStartingRange(Box previousRange) {
            startRange = startRange.union(previousRange);
        }

        private InterpolationData copy() {
            InterpolationData copy = new InterpolationData(startRange, target, steps, false);
            copy.frozen = frozen;
            copy.lowStep = lowStep;
            copy.highStep = highStep;
            return copy;
        }

        private static double lerp(double from, double to, double t) {
            return from + (to - from) * t;
        }
    }
}
