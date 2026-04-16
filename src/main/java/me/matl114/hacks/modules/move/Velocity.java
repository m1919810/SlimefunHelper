package me.matl114.hacks.modules.move;

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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
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
    }

    public int lastVelocityTick = 0;
    public boolean canCancel = false;

    public void onEntityDamage(Event<EntityDamageS2CPacket> damage) {
        if (enable.get() && mc.player != null && damage.context.entityId() == mc.player.getId()) {
            canCancel = true;
        }
    }

    public void onPlayerVelocity(Event<Vec3d> event) {
        if (enable.get() && canCancel && mc.player != null && event.getArgs(0) == mc.player) {
            lastVelocityTick = Tasks.getTick();
            if (mode.get() == Configs.BypassMode.NO_BYPASS) {
                event.cancel();
            } else {

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

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case AC_GRIM, AC_MATRIX -> mode.set(Configs.BypassMode.BYPASS_GRIM);
            default -> mode.set(Configs.BypassMode.NO_BYPASS);
        }
    }
}
