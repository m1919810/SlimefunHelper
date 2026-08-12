package me.matl114.hacks.modules.move;

import java.util.ArrayDeque;
import java.util.Deque;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Velocity extends BaseModule implements LegalMovementManager.MovementModifier {
    // 还没想好 先新建文件夹
    public final ModulePath velocityManagement = makePath(Configs.MOV_CONFIG, "velocity-management");
    public final ModulePath antiKb = velocityManagement.add("antikb");

    public final FlagRef enable = flagBuilder(antiKb.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(
                    antiKb.addHotkey(), new MultiKeyBind(), antiKb.addEnable(), moduleMeta(() -> this.mode))
            .build();

    public final FlagRef hurtOnly = builder(antiKb.add("ground-hurt-only"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef hurtElytraOnly = builder(antiKb.add("elytra-hurt-only"), Boolean.class)
            .defaultValue(true)
            .build();

    public final DoubleRef minHorizontalVelocity = builder(antiKb.add("horizontal-threshold"), DoubleRef.TYPE)
            .defaultValue(0.00)
            .build();

    public final DoubleRef minVerticalVelocity = builder(antiKb.add("vertical-threshold"), DoubleRef.TYPE)
            .defaultValue(0.00)
            .build();

    public final EnumRef<Mode> mode =
            builder(antiKb.add("mode"), Mode.class).defaultValue(Mode.NONE).build();

    public final FlagRef explosions =
            flagBuilder(antiKb.add("bypass-explosions")).build();

    public final FlagRef freezeIfWalk = flagBuilder(antiKb.add("grim-freeze-if-walk"))
            .show(() -> mode.get().isIn(Mode.GRIM_LEGACY_GROUND))
            .build();

    public final IntRef resetKBTick = intBuilder(antiKb.add("grim-reset-kb-tick"))
            .defaultValue(3)
            .show(() -> mode.get().isIn(Mode.GRIM_LEGACY_GROUND))
            .build();

    public final FlagRef pauseWhenWASD = flagBuilder(antiKb.add("pause-when-wasd"))
            .show(() -> mode.get().isIn(Mode.FREEZE))
            .build();

    public final DoubleRef freezeTime = doubleBuilder(antiKb.add("freeze-time"))
            .show(() -> mode.get().isIn(Mode.FREEZE))
            .defaultValue(5.0D)
            .build();

    // all
    public final FlagRef onGroundOnly = flagBuilder(antiKb.add("on-ground-only"))
            .show(() -> mode.get().isNotIn(Mode.NONE))
            .build();
    public final FlagRef notInWater = flagBuilder(antiKb.add("not-in-water"))
            .show(() -> mode.get().isNotIn(Mode.NONE))
            .build();

    public final FlagRef inFirework = builder(antiKb.add("execute-during-fireworks"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef inWall = builder(antiKb.add("execute-in-wall"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef noBlock = flagBuilder(antiKb.add("no-block-push")).build();

    public final FlagRef noEntityPush =
            flagBuilder(antiKb.add("no-entity-push")).build();

    public final FlagRef noWaterPush =
            flagBuilder(antiKb.add("no-liquid-flow-push")).build();

    public final FlagRef noClimbing = flagBuilder(antiKb.add("no-climbing")).build();

    public static LegalMovementManager.DelegateMovementModifier instance;

    public Velocity() {
        super("Velocity");
        bindFlag(enable);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        // 在此处注册事件监听器（当前为空）
        registerListener(Listener.getEntityClientVelocityUpdate().getChannel(EntityType.PLAYER), this::onVelocity);
        registerListener(Listener.getPlayerExplosionVelocity(), this::onExplosion);
        registerListener(Listener.getTeleportConfirmResponsePoint(), this::onPlayerSetBack);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityDamage);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onSendMove);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetPosition);
        registerListener(Listener.getPacketPoint().getChannel(CommonPingS2CPacket.class), this::onPing);
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(BlockUpdateS2CPacket.class), this::onBlockUpdate);
        registerListener(Listener.getPacketPreHandlePoint().getChannel(ExplosionS2CPacket.class), this::onExplosionPre);
        registerListener(Listener.getPlayerFluidVelocityPoint(), this::onElytraLiquidPush);
    }

    public void onElytraLiquidPush(Event<Vec3d> velocity) {
        if (noWaterPush.get()) {
            velocity.cancel();
        }
    }

    public int lastHurtTick = 0;
    public int canCancel = 0;
    int lastGroundTick = 0;

    public void onEntityDamage(Event<EntityDamageS2CPacket> damage) {
        if (enable.get() && mc.player != null && damage.context.entityId() == mc.player.getId()) {
            var type = damage.context.sourceType();
            // ignore no knockback types
            if (!type.isIn(DamageTypeTags.NO_KNOCKBACK)) {
                canCancel += 1;
                lastHurtTick = Tasks.getTick();
            }
        }
    }

    boolean shouldDelay = false;
    long lastSetBackNS = 0;
    long lastVelocityNS = 0;
    long lastCancelVelocityNS = 0;
    int lastCancelVelocityTick = 0;
    Vec3d lastVelocity = Vec3d.ZERO;
    Vec3d lastCancelledVelocity = Vec3d.ZERO;

    public void markForCancelVelocity() {
        lastCancelVelocityNS = System.nanoTime();
        lastCancelVelocityTick = Tasks.getTick();
        lastCancelledVelocity = lastVelocity;
        // Debug.chat("Cancel vc", lastCancelledVelocity.length());
    }

    public void onExplosionPre(Event<ExplosionS2CPacket> eventExplosion) {
        if (enable.get()
                && explosions.get()
                && mc.player != null
                && (eventExplosion.context.playerKnockback().isPresent())) {
            canCancel += 1;
        }
    }

    public void onVelocity(Event<Vec3d> event) {
        if (checkNull()) return;
        if (event.getArgs(0) == mc.player) {
            lastVelocityNS = System.nanoTime();
            lastVelocity = event.context;
            onPlayerVelocity(event);
        }
    }

    public void onExplosion(Event<Vec3d> event) {
        if (checkNull()) return;
        lastVelocityNS = System.nanoTime();
        lastVelocity = mc.player.getVelocity().add(event.context());
        onPlayerVelocity(event);
    }

    public void onPlayerVelocity(Event<Vec3d> event) {
        if (enable.get() && mc.player != null) {
            if (inFirework.get()
                    && mc.player.isFallFlying()
                    && MovTasks.getElytraExtra().canFireworkControlMotion()) {
                markForCancelVelocity();
                event.cancel();
                return;
            }
            // todo: make this inside wall
            if (inWall.get() && mc.player.isInsideWall()) {
                // handle In
                markForCancelVelocity();
                event.cancel();
                return;
            }
            boolean shouldApply;
            if (mc.player.isFallFlying()) {
                shouldApply = !hurtElytraOnly.get() || canCancel > 0;
            } else {
                shouldApply = !hurtOnly.get() || canCancel > 0;
            }
            if (shouldApply) {
                canCancel = Math.max(canCancel - 1, 0);
                if (event.isCancelled()) {
                    return;
                }
                if (mode.get() == Mode.NONE) {
                    markForCancelVelocity();
                    event.cancel();
                    return;
                } else if (lastVelocity.horizontalLength() >= minHorizontalVelocity.get()
                        || Math.abs(lastVelocity.y) >= minVerticalVelocity.get()) {
                    if ((mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava())
                            && notInWater.get()) {
                        return;
                    }

                    if (!mc.player.isFallFlying()
                            && (!onGroundOnly.get() || mc.player.isOnGround())
                            && mode.get() == Mode.GRIM_LEGACY_GROUND) {
                        handleVelocityGrimLegacy(event);
                        return;
                    }
                    if (!mc.player.isFallFlying()
                            && (!onGroundOnly.get() || mc.player.isOnGround())
                            && mode.get() == Mode.GRIM_NEW_GROUND) {
                        handleVelocityGrimNew(event);
                        return;
                    }
                    if (mode.get() == Mode.FREEZE) {
                        handleVelocityFreeze(event);
                        return;
                    }
                    if (!mc.player.isOnGround() && onGroundOnly.get()) {
                        // todo ?
                    }
                    // todo: copy from what
                }
            }
            if (mode.get() == Mode.FREEZE) {
                handleVelocityExtraFreeze(event);
            }
        }
    }

    boolean flagLegacy = false;

    public void handleVelocityGrimLegacy(Event<Vec3d> eventVc) {
        //        if (lastSetBackNS > System.nanoTime() - 100 * 1_000_000) {
        //            return;
        //        }
        eventVc.cancel();
        markForCancelVelocity();
        flagLegacy = true;
        // who 'd fuck write this shit?
        //        mc.getNetworkHandler()
        //                .sendPacket(VPacket.newLookAndOnGround(
        //                        mc.player.getYaw(),
        //                        mc.player.getPitch(),
        //                        mc.player.isOnGround(),
        //                        mc.player.horizontalCollision));
    }

    int lastFreezeTick = 0;

    public void handleVelocityFreeze(Event<Vec3d> eventVc) {
        lastFreezeTick = Tasks.getTick();
        eventVc.cancel();
        markForCancelVelocity();
    }

    public void handleVelocityExtraFreeze(Event<Vec3d> eventVc) {
        if (lastFreezeTick + freezeTime.get() > Tasks.getTick()) {
            eventVc.cancel();
        }
    }

    public void handleVelocityGrimNew(Event<Vec3d> eventVc) {}

    public void onPreTick(Event<ClientPlayerEntity> eventPreTick) {
        if (!enable.get()) {
            return;
        }
        if (flagLegacy) {
            mc.interactionManager.sendSequencedPacket(
                    mc.world,
                    (seq) -> new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                            mc.player.isCrawling()
                                    ? mc.player.getBlockPos()
                                    : mc.player.getBlockPos().up(),
                            Direction.DOWN,
                            seq));
            //            BlockPos pos = (mc.player.isCrawling() ? mc.player.getBlockPos() :
            // mc.player.getBlockPos().up()).down();
            //            mc.interactionManager.sendSequencedPacket(mc.world, (seq)-> new
            // PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(pos.toBottomCenterPos(), Direction.DOWN,
            // pos, false ), seq));
            if (lastCancelVelocityTick + resetKBTick.get() <= Tasks.getTick()) {
                flagLegacy = false;
                // mc.player.setVelocity(Vec3d.ZERO);
            } else {
                if (freezeIfWalk.get() || !PlayerInputUtils.of(mc.options).hasMovementControl()) {
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                    mc.player.setOnGround(true);
                } else {
                    ClientPlayerAccess.of(mc.player).resyncPos();
                    mc.player.setOnGround(true);
                }
                // FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                // mc.player.setOnGround(true);
            }
        }
        // hyw
        if (mode.get() == Mode.FREEZE && lastFreezeTick + freezeTime.get() > Tasks.getTick()) {
            if (!pauseWhenWASD.get() || !PlayerInputUtils.of(mc.options).hasMovementControl()) {
                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
            }
        }
    }

    public void onBlockUpdate(Event<BlockUpdateS2CPacket> eventBlockUpdate) {
        //        if(enable.get()){
        //            BlockPos pos = eventBlockUpdate.context.getPos();
        //            if(pos.getSquaredDistance(mc.player.getPos()) < 10){
        //                Debug.chat("Update blockstate", pos);
        //            }
        //
        //        }
    }

    Deque<Packet> packets = new ArrayDeque<>();

    public void onPing(Event<CommonPingS2CPacket> pingEvent) {
        if (shouldDelay) {
            packets.add(pingEvent.context);
            pingEvent.cancel();
        }
    }

    public void onSetPosition(Event<PlayerPositionLookS2CPacket> event) {
        lastSetBackNS = System.nanoTime();
        if (shouldDelay) {
            for (Packet packet : packets) {
                try {
                    packet.apply(mc.getNetworkHandler());
                } catch (OffThreadException ex) {
                    // ignore
                }
            }
            packets.clear();
        }
    }
    //    public void onPlayerSetBackPacket(Event<PlayerPositionLookS2CPacket> event){
    //        if(mode.get() == Configs.BypassMode.BYPASS_GRIM){
    //            skipCount = 3;
    //        }
    //    }

    public void onSendMove(Event<PlayerMoveC2SPacket> event) {}

    public void onPlayerSetBack(Event<MovTasks.MovInfo> event) {}

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    boolean skipTick = false;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {}

    @Override
    public void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        if (skipTick) {
            sendFallFlying();
        }
        if (shouldDelay) {
            skipTick = true;
            sendFallFlying();
        }
    }

    private void sendFallFlying() {
        var packet = new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING);

        mc.getNetworkHandler().sendPacket(packet);
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        //        if(skipTick){
        //            lastFakeGroundTick = Tasks.getTick();
        //            mc.player.setPosition(movementManagerEvent.context.playerStatus.pos.withAxis(Direction.Axis.Y,
        // movementManagerEvent.context.playerStatus.pos.y + 8E-8));
        //            skipTick = false;
        //        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (lastGroundTick + 5 < Tasks.getTick()) {
            shouldDelay = false;
            skipTick = false;
        }
        return true;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case AC_GRIM_LEGACY, AC_GRIM -> mode.set(Mode.GRIM_LEGACY_GROUND);
            case AC_MATRIX -> mode.set(Mode.GRIM_NEW_GROUND);
            default -> mode.set(Mode.NONE);
        }
    }

    public enum Mode implements ConfigEnum {
        NONE,
        GRIM_LEGACY_GROUND,
        FREEZE,
        GRIM_NEW_GROUND;

        @Override
        public String getConfigEnumType() {
            return "velocity_bypass_mode";
        }
    }
}
