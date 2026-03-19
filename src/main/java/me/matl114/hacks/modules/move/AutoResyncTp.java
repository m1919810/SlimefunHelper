package me.matl114.hacks.modules.move;

import java.util.Optional;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.Debug;
import me.matl114.utils.MathUtils;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;

@SuppressWarnings("all")
public class AutoResyncTp extends BaseModule {
    public AutoResyncTp() {}

    public Optional<Vec3d> pos;
    public int ticksTilExpire;
    public static final String[] MOVE_LOG_AUTO_RESYNC = {"move-safety", "log-auto-resync"};

    public static final String[] MOVE_AUTO_RESYNC_EXPIRE = {"move-safety", "auto-resync-expire-tick"};

    public static final String[] MOVE_AUTO_RESYNC_RECURSIVE = {"move-safety", "auto-resync-recursively"};

    public final FlagRef logAutoResync = builder(Configs.MOV_CONFIG, Boolean.class)
            .path(MOVE_LOG_AUTO_RESYNC)
            .defaultValue(true)
            .build();

    public final IntRef expireTick = builder(Configs.MOV_CONFIG, MOVE_AUTO_RESYNC_EXPIRE, IntRef.TYPE)
            .defaultValue(10)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef recursive =
            flagBuilder(Configs.MOV_CONFIG, MOVE_AUTO_RESYNC_RECURSIVE).build();

    public void setAutoResyncSchedule(Optional<Vec3d> pos) {
        this.setAutoResyncSchedule(pos, expireTick.get());
    }

    public void setAutoResyncSchedule(Optional<Vec3d> pos, int ticksExpire) {
        this.pos = pos;
        this.ticksTilExpire = ticksExpire + Tasks.getTick();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
    }

    public void onSetBack(Event<PlayerPositionLookS2CPacket> event) {
        if (event.isCancelled()) return;
        if (ticksTilExpire > Tasks.getTick() && pos != null && mc.player != null) {
            // auto resync
            Vec3d resyncToPos = pos.orElseGet(mc.player::getPos);
            PlayerPositionLookS2CPacket packet1 = event.context;
            Vec3d resyncPos = getPosition(packet1);
            double sqdistance = resyncPos.squaredDistanceTo(mc.player.getPos());
            double sqdistance2 = resyncPos.squaredDistanceTo(resyncToPos);
            boolean currentOnGround = mc.player.isOnGround();
            if (sqdistance > 1E-4
                    && sqdistance < MathUtils.s2(128)
                    && sqdistance2 > 1E-4
                    && sqdistance2 < MathUtils.s2(128)) {
                // don't so far, it may be a real teleport
                if (logAutoResync.get()) {
                    Debug.chat("Auto Resync triggered!");
                }
                mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(packet1.getTeleportId()));
                mc.player.setPosition(resyncPos);
                mc.player.setOnGround(false);
                //                    mc.getNetworkHandler().sendPacket(new
                // PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                // false));
                if (currentOnGround) {
                    resyncToPos = resyncToPos.add(0, 1e-6, 0);
                }
                MovTasks.scheduleTpInternal(MovTasks.createPlayerMovContext(), resyncToPos, 200, false, true, true);
                ticksTilExpire = -1;
                pos = null;
                event.cancel();
                if (recursive.get()) {
                    mc.player.setPosition(resyncToPos);
                    setAutoResyncSchedule(Optional.empty());
                }
            }
        }
    }

    public Vec3d getPosition(PlayerPositionLookS2CPacket packet) {
        boolean bl = packet.getFlags().contains(PositionFlag.X);
        var playerEntity = mc.player;
        ;
        boolean bl2 = packet.getFlags().contains(PositionFlag.Y);
        boolean bl3 = packet.getFlags().contains(PositionFlag.Z);
        double e;
        if (bl) {
            e = playerEntity.getX() + packet.getX();
        } else {
            e = packet.getX();
        }

        double g;
        if (bl2) {
            g = playerEntity.getY() + packet.getY();
        } else {
            g = packet.getY();
        }

        double i;
        if (bl3) {
            i = playerEntity.getZ() + packet.getZ();
        } else {
            i = packet.getZ();
        }
        return new Vec3d(e, g, i);
    }
}
