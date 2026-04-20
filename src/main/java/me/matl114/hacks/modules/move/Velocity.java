package me.matl114.hacks.modules.move;

import java.util.ArrayDeque;
import java.util.Deque;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.entity.EntityType;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class Velocity extends BaseModule implements LegalMovementManager.MovementModifier {
    // 还没想好 先新建文件夹

    public static final String[] ENABLE = BaseModule.makePath("velocity-management.antikb.enable");
    public static final String[] MODE = BaseModule.makePath("velocity-management.antikb.mode");

    public final FlagRef enable = flagBuilder(Configs.MOV_CONFIG, ENABLE).build();
    public final EnumRef<Configs.BypassMode> mode = builder(Configs.MOV_CONFIG, MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
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
    }

    public int lastHurtTick = 0;
    public boolean canCancel = false;
    int lastGroundTick = 0;

    public void onEntityDamage(Event<EntityDamageS2CPacket> damage) {
        if (enable.get() && mc.player != null && damage.context.entityId() == mc.player.getId()) {
            canCancel = true;
            lastHurtTick = Tasks.getTick();
        }
    }

    public int lastFakeGroundTick = 0;
    boolean shouldDelay = false;

    public void onPlayerVelocity(Event<Vec3d> event) {
        if (enable.get() && mc.player != null && event.getArgs(0) == mc.player) {

            if (mode.get() == Configs.BypassMode.NO_BYPASS) {
                if (canCancel) {
                    event.cancel();
                }
            } else {

                if (false && (canCancel || shouldDelay)) {
                    shouldDelay = true;
                    lastGroundTick = Tasks.getTick();
                    event.cancel();
                }

                //                if(canCancel){
                //                    event.cancel();
                //                }else if(lastHurtTick +20> Tasks.getTick()){
                //                    event.cancel();
                //                    var entity = mc.player;
                //                    mc.getNetworkHandler()
                //                        .sendPacket(VPacket.newFull(
                //                            entity.getX(),
                //                            entity.getY() + 9E-8,
                //                            entity.getZ(),
                //                            entity.getYaw(),
                //                            entity.getPitch(),
                //                            !entity.isOnGround(),
                //                            entity.horizontalCollision));
                //                }
                //                event.cancel();
                //                mc.getNetworkHandler()
                //                    .sendPacket(
                //                        VPacket.newOnGroundOnly(true, mc.player.horizontalCollision)
                //                    );
                //                mc.getNetworkHandler().sendPacket(new
                // PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.mc.player.getBlockPos(),
                // Direction.UP));
            }
            canCancel = false;
        }
    }

    Deque<Packet> packets = new ArrayDeque<>();

    public void onPing(Event<CommonPingS2CPacket> pingEvent) {
        if (shouldDelay) {
            packets.add(pingEvent.context);
            pingEvent.cancel();
        }
    }

    public void onSetPosition(Event<PlayerPositionLookS2CPacket> event) {
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

    boolean dealWithSetbackVelocity2 = false;

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
            case AC_GRIM, AC_MATRIX -> mode.set(Configs.BypassMode.BYPASS_GRIM);
            default -> mode.set(Configs.BypassMode.NO_BYPASS);
        }
    }
}
