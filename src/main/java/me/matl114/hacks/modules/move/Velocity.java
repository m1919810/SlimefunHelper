package me.matl114.hacks.modules.move;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Velocity extends BaseModule implements LegalMovementManager.MovementModifier {
    // 还没想好 先新建文件夹

    public static final String[] ENABLE = BaseModule.makePath("velocity-management.antikb.enable");
    public static final String[] MODE = BaseModule.makePath("velocity-management.antikb.mode");

    public final FlagRef enable = flagBuilder(Configs.MOV_CONFIG, ENABLE).build();

    public final DoubleRef minHorizontalVelocity = builder(Configs.MOV_CONFIG, makePath("velocity-management.antikb.horizontal-threshold"), DoubleRef.TYPE)
        .defaultValue(0.01)
        .build();

    public final DoubleRef minVerticalVelocity = builder(Configs.MOV_CONFIG, makePath("velocity-management.antikb.vertical-threshold"), DoubleRef.TYPE)
        .defaultValue(0.05)
        .build();

    public final EnumRef<Mode> mode = builder(Configs.MOV_CONFIG, MODE, Mode.class)
            .defaultValue(Mode.NONE)
            .build();

    public final FlagRef explosions = flagBuilder(
                    Configs.MOV_CONFIG, makePath("velocity-management.antikb.bypass-explosions"))
            .build();

    public final FlagRef onGroundOnly = flagBuilder(
                    Configs.MOV_CONFIG, makePath("velocity-management.antikb.on-ground-only"))
            .build();

    public final FlagRef notInWater = flagBuilder(Configs.MOV_CONFIG, makePath("velocity-management.antikb.not-in-water"))
        .build();

    public final FlagRef inWall = flagBuilder(
                    Configs.MOV_CONFIG, makePath("velocity-management.antikb.execute-in-wall"))
            .build();

    public final FlagRef noBlock = flagBuilder(Configs.MOV_CONFIG, makePath("velocity-management.antikb.no-block-push"))
            .build();

    public final FlagRef noEntityPush = flagBuilder(
                    Configs.MOV_CONFIG, makePath("velocity-management.antikb.no-entity-push"))
            .build();

    public static LegalMovementManager.DelegateMovementModifier instance;

    public Velocity() {
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
        registerListener(
                Listener.getEntityClientVelocityUpdate().getChannel(EntityType.PLAYER), this::onPlayerVelocity);
        registerListener(Listener.getTeleportConfirmResponsePoint(), this::onPlayerSetBack);
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityDamage);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onSendMove);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetPosition);
        registerListener(Listener.getPacketPoint().getChannel(CommonPingS2CPacket.class), this::onPing);
        registerListener(
                PacketManager.getPacketQueueEvent().getChannel(EntityDamageS2CPacket.class), this::onEntityDamageQueue);
        registerListener(
                PacketManager.getPacketQueueEvent().getChannel(EntityVelocityUpdateS2CPacket.class),
                this::onPlayerVelocityQueue);
        registerListener(PacketManager.getPacketQueueEvent().getPacketReceiveChannel(), this::onPacketQueue);
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(Listener.getPacketPostHandlePoint().getChannel(BlockUpdateS2CPacket.class), this::onBlockUpdate);
    }

    public int lastHurtTick = 0;
    public boolean canCancel = false;
    public boolean canQueue = false;
    int lastGroundTick = 0;

    public void onEntityDamageQueue(Event<EntityDamageS2CPacket> damage) {
        if (enable.get() && mc.player != null && damage.context.entityId() == mc.player.getId()) {
            canQueue = true;
        }
    }

    long startQueuePacket = 0;

    public void onPlayerVelocityQueue(Event<EntityVelocityUpdateS2CPacket> entityVelocity) {
        if (enable.get() && mc.player != null && entityVelocity.context.getEntityId() == mc.player.getId()) {
            //            if(mode.get() == Configs.BypassMode.BYPASS_GRIM && canQueue && mc.player.isOnGround()){
            //                startQueuePacket = System.currentTimeMillis();
            //                entityVelocity.cancel();
            //                onMineSchedule();
            //            }
            canQueue = false;
        }
    }
    // todo: get from LiquidBounce
    // todo: try use Freeze

    public void onMineSchedule() {
        BlockPos pos = mc.player.getVelocityAffectingPos();
        if (pos != null) {
            PlayerInteractionAccess.of(mc.interactionManager).sendStartBreakPacket(pos, Direction.UP);
            long endQueue = startQueuePacket;
            Tasks.scheduleRepeatedPre(
                    () -> {
                        //                if(endQueue > 0 && System.currentTimeMillis() - endQueue < 200 &&
                        // !checkNull()){
                        BlockPos breakPos = PlayerInteractionAccess.of(mc.interactionManager)
                                .getCurrentMiningPos();
                        if (Objects.equals(breakPos, pos)) {
                            PlayerInteractionAccess.of(mc.interactionManager)
                                    .sendStopBreakPacket(breakPos, Direction.UP);
                            mc.world.setBlockState(breakPos, Blocks.AIR.getDefaultState());
                            return true;
                        }
                        return true;
                        // }return true;
                    },
                    0,
                    1);
        }
    }

    public void flush() {
        PacketManager.flushInBound((pkts) -> {
            if (pkts.packet() instanceof EntityVelocityUpdateS2CPacket vc) {
                return PacketManager.FlushAction.DROP;
            } else {
                return PacketManager.FlushAction.FLUSH;
            }
        });
        startQueuePacket = 0;
    }

    public void onPacketQueue(Event<Packet<?>> packet) {
        if (startQueuePacket > 0) {
            if (PacketManager.isAsyncOrNotTransactionS2CPacket(packet.context)) return;
            long systemMs = System.currentTimeMillis();
            if (systemMs > startQueuePacket + 50) {
                startQueuePacket = 0;
                flush();
            } else {
                packet.cancel();
            }
        }
    }

    public void onEntityDamage(Event<EntityDamageS2CPacket> damage) {
        if (enable.get() && mc.player != null && damage.context.entityId() == mc.player.getId()) {
            canCancel = true;
            lastHurtTick = Tasks.getTick();
        }
    }

    boolean shouldDelay = false;
    long lastSetBackNS = 0;
    long lastVelocityNS = 0;
    long lastCancelVelocityNS = 0;
    int lastCancelVelocityTick =0;
    Vec3d lastVelocity = Vec3d.ZERO;
    Vec3d lastCancelledVelocity = Vec3d.ZERO;
    public void markForCancelVelocity(){
        lastCancelVelocityNS = System.nanoTime();
        lastCancelVelocityTick = Tasks.getTick();
        lastCancelledVelocity = lastVelocity;
       // Debug.chat("Cancel vc", lastCancelledVelocity.length());
    }
    public void onPlayerVelocity(Event<Vec3d> event) {
        lastVelocityNS = System.nanoTime();
        lastVelocity = event.context;
        if (enable.get() && mc.player != null && event.getArgs(0) == mc.player) {

            if (mode.get() == Mode.NONE) {
                if (canCancel) {
                    markForCancelVelocity();
                    event.cancel();
                }
            } else if(lastVelocity.horizontalLength() >= minHorizontalVelocity.get() || Math.abs(lastVelocity.y) >= minVerticalVelocity.get()){
                if (canCancel) {
                    if (mc.player.isFallFlying() && MovTasks.getElytraExtra().canFireworkControlMotion()) {
                        canCancel = false;
                        markForCancelVelocity();
                        event.cancel();
                        return;
                    }
//                    if (MovTasks.getFloatingUtils().workGrimFloatingThisTick()) {
//                        canCancel = false;
//                        markForCancelVelocity();
//                        event.cancel();
//                        return;
//                    }
                }
                if((mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava()) && notInWater.get()){
                    return;
                }
                //todo: make this inside wall
                if(inWall.get() && mc.player.isInsideWall()){
                    return;
                }
                if (canCancel && !mc.player.isFallFlying() && mc.player.isOnGround() && mode.get() == Mode.GRIM_LEGACY_GROUND) {
                    canCancel = false;
                    handleVelocityGrimLegacy(event);
                    return;
                }
                if (canCancel && !mc.player.isFallFlying() && mc.player.isOnGround() && mode.get() == Mode.GRIM_NEW_GROUND) {
                    canCancel = false;
                    handleVelocityGrimNew(event);
                    return;
                }
                if(!mc.player.isOnGround() && onGroundOnly.get()){
                    return;
                }
                // todo: copy from what
            }
            canCancel = false;
        }
    }
    boolean flagLegacy = false;
    public void handleVelocityGrimLegacy(Event<Vec3d> eventVc) {
        if(lastSetBackNS > System.nanoTime() - 100 * 1_000_000){
            return;
        }
        eventVc.cancel();
        markForCancelVelocity();
        flagLegacy = true;
        mc.getNetworkHandler().sendPacket(VPacket.newLookAndOnGround(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    public void handleVelocityGrimNew(Event<Vec3d> eventVc) {

    }

    public void onPreTick(Event<ClientPlayerEntity> eventPreTick){
        if(!enable.get()){
            return;
        }
        if(flagLegacy){
            mc.interactionManager.sendSequencedPacket(mc.world, (seq)->new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, mc.player.isCrawling() ? mc.player.getBlockPos() : mc.player.getBlockPos().up(), Direction.DOWN, seq
            ));
//            BlockPos pos = (mc.player.isCrawling() ? mc.player.getBlockPos() : mc.player.getBlockPos().up()).down();
//            mc.interactionManager.sendSequencedPacket(mc.world, (seq)-> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(pos.toBottomCenterPos(), Direction.DOWN, pos, false ), seq));
            if(lastCancelVelocityTick + 3 <= Tasks.getTick()){
                flagLegacy = false;
                //mc.player.setVelocity(Vec3d.ZERO);
            }else{
               MovTasks.getFloatingUtils().setGrimFloatingTick(true);
            }
        }
    }

    public void onBlockUpdate(Event<BlockUpdateS2CPacket> eventBlockUpdate){
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
            case AC_GRIM_LEGACY -> mode.set(Mode.GRIM_LEGACY_GROUND);
            case AC_GRIM, AC_MATRIX -> mode.set(Mode.GRIM_NEW_GROUND);
            default -> mode.set(Mode.NONE);
        }
    }

    public enum Mode implements ConfigEnum {
        NONE,
        GRIM_LEGACY_GROUND,
        GRIM_NEW_GROUND;

        @Override
        public Text getDisplay() {
            return Text.literal(name());
        }

        @Override
        public String getConfigEnumType() {
            return "velocity_bypass_mode";
        }
    }
}
