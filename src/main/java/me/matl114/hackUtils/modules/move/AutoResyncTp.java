package me.matl114.hackUtils.modules.move;

import me.matl114.hackUtils.MovTasks;
import me.matl114.hackUtils.Tasks;
import me.matl114.hackUtils.modules.BaseModule;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.Debug;
import me.matl114.utils.MathUtils;
import me.matl114.utils.UtilClass.Event;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class AutoResyncTp extends BaseModule {
    public AutoResyncTp() {

    }
    public Vec3d pos;
    public int ticksTilExpire;
    public static final String[] MOVE_LOG_AUTO_RESYNC = {"move-safety", "log-auto-resync"};

    public final Config.FlagRef logAutoResync = Configs.MOV_CONFIG
        .builder(Boolean.class)
        .path(MOVE_LOG_AUTO_RESYNC)
        .defaultValue(true)
        .build();
    public void setAutoResyncSchedule(Vec3d pos, int ticksExpire){
        this.pos = pos;
        this.ticksTilExpire = ticksExpire + Tasks.getTick();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketListenerPoint(PlayerPositionLookS2CPacket.class), this::onSetBack);
    }

    public void onSetBack(Event<PlayerPositionLookS2CPacket> event){
        if(event.isCancelled())return;
        if(ticksTilExpire > Tasks.getTick() && pos != null){
            //auto resync
            PlayerPositionLookS2CPacket packet1 = event.context;
            Vec3d resyncPos = new Vec3d(packet1.getX(), packet1.getY(), packet1.getZ());
            double sqdistance = resyncPos.squaredDistanceTo(mc.player.getPos());
            double sqdistance2 = resyncPos.squaredDistanceTo(pos);
            if(sqdistance > 1E-4 && sqdistance < MathUtils.s2(128) && sqdistance2 > 1E-4 && sqdistance2 < MathUtils.s2(128)){
                //don't so far, it may be a real teleport
                if(logAutoResync.get()){
                    Debug.chat("Auto Resync triggered!");
                }
                mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(packet1.getTeleportId()));
                mc.player.setPosition(resyncPos);
//                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
                MovTasks.executeTp(pos, 200, false, true);
                ticksTilExpire = -1;
                pos = null;
                event.cancel();
            }
        }
    }
}
