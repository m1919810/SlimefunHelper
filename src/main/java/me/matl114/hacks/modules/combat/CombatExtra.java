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
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.ac.PostManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
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
        return mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE) + d;
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
        registerListener(
                Listener.getPacketPoint().getChannel(ClientTickEndC2SPacket.class), this::onClientTickEndPacket);
        registerListener(
                Listener.getEntityTrackedPositionUpdate().getChannel(EntityType.PLAYER), this::onTrackedPosition);
    }

    public int shieldExceptionspam = 0;

    public void onUseAttackNoSlow(Event<HitResult> event) {
        //        if (shieldAttack.get() && mc.player.isUsingItem()) {
        //            MovTasks.getNoSlowDown().setPreAttackUseTick();
        //        }
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
            mc.world.getPlayers().forEach(s -> {
                if (s instanceof OtherClientPlayerEntity) {
                    tickEntity(s);
                }
            });
        }
    }

    private void tickEntity(Entity entity) {
        boolean tickReliable = !DisablerManager.INSTANCE.isGrimSelfCheckDisabled()
                && (ViaFabricPlusHooks.isSupportEndTick() || mc.player.hasVehicle());
        getTracker(entity).onMovement(!tickReliable);
    }

    public void onClientTickEndPacket(Event<ClientTickEndC2SPacket> event) {
        if (ViaFabricPlusHooks.isSupportEndTick() && !PlayerStateManager.INSTANCE.lastTickHasMovement) {
            mc.world.getPlayers().forEach(s -> {
                if (s instanceof OtherClientPlayerEntity) {
                    tickEntity(s);
                }
            });
        }
    }

    public void onTrackedPosition(Event<PosUpdate> event) {
        if (event.context.entity() instanceof OtherClientPlayerEntity entities) {
            getTracker(entities)
                    .onEntityPos(
                            event.context.newPos(), !entities.hasVehicle() && !ViaFabricPlusHooks.isSupportEndTick());
        }
    }

    private static final String KEY_TRACKER = "slimefunhelper:combat_extra/position_tracker";

    public PlayerPositionTracker getTracker(Entity player) {
        return ((MetadataHolder) player).getMetadata().getOrPut(this, KEY_TRACKER, PlayerPositionTracker::new);
    }

    public static class PlayerPositionTracker {
        private static final int interpolationSteps = 3;

        private InterpolationData oldPacketLocation;
        private InterpolationData newPacketLocation;
        private Vec3d trackedPosition;

        public void onEntityPos(Vec3d pos, boolean maySkipTick) {
            if (trackedPosition == null || newPacketLocation == null) {
                trackedPosition = pos;
                oldPacketLocation = null;
                newPacketLocation = new InterpolationData(this.trackedPosition, interpolationSteps, maySkipTick);
            } else {
                trackedPosition = pos;
                oldPacketLocation = newPacketLocation;
                newPacketLocation = new InterpolationData(
                        oldPacketLocation.possiblePositions(), trackedPosition, interpolationSteps, maySkipTick);
                PostManager.INSTANCE.addNextPreTickAction((ch) -> oldPacketLocation = null);
            }
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

        private Box expand(Box box, Entity entity) {
            return box.expand(entity.dimensions.width() / 2, 0, entity.dimensions.width() / 2)
                    .stretch(0, entity.dimensions.height(), 0);
        }
    }

    public static final class InterpolationData {
        private final int steps;
        private final Vec3d target;
        private Box startRange;
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
            if (maySkipTick) this.highStep = steps;
            if (increase) {
                lowStep = Math.min(lowStep + 1, steps);
            }
            this.highStep = Math.min(highStep + 1, steps);
        }

        public void includePossibleStartingRange(Box previousRange) {
            startRange = startRange.union(previousRange);
        }

        private static double lerp(double from, double to, double t) {
            return from + (to - from) * t;
        }
    }
}
