package me.matl114.hacks.modules.move;

import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.StringFormat;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class SetBackLog extends BaseModule {
    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");

    public SetBackLog() {}

    public final FlagRef logResync =
            flagBuilder(moveSafety.add("log-resync-packets")).build();

    public final NBTRef<StringFormat> logResyncFormat = builder(moveSafety.add("log-resync-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("position"), "&fPos Resync {position}", true))
            .build();

    public final FlagRef logAc =
            flagBuilder(moveSafety.add("check-setback-packets")).build();

    public final NBTRef<StringFormat> logAcFormat = builder(moveSafety.add("log-ac-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("teleportId", "position"), "&c[AC] 反作弊回弹! tp号:{teleportId}, 位置: {position}", true))
            .build();

    //    public final StringRef logAcFormat = builder(moveSafety.add("log-ac-format"), String.class)
    //            .defaultValue("&c[AC] 反作弊回弹! tp号:%d")
    //            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketListenerPoint(PlayerPositionLookS2CPacket.class), this::onSetBack);
    }

    public int maxTpId = -1;
    public Vec3d lastDesyncPos = Vec3d.ZERO;

    public void onSetBack(Event<PlayerPositionLookS2CPacket> event) {
        PlayerPositionLookS2CPacket packet = event.context;
        int tpId = packet.getTeleportId();
        maxTpId = Math.max(maxTpId, tpId);
        if (mc.player != null) {
            lastDesyncPos = mc.player.getPos();
        }
        if (logResync.get()) {
            StringFormat logFormat = logResyncFormat.get();
            Debug.chat(logFormat.formatText(
                    ChatUtils.getDisplayedLocationDouble(packet.getX(), packet.getY(), packet.getZ())));
        }
        if (logAc.get()) {

            if (tpId < 0) {
                if (mc.player != null) {
                    StringFormat logFormat = logAcFormat.get();
                    try {
                        Debug.chat(logFormat.formatText(
                                tpId,
                                ChatUtils.getDisplayedLocationDouble(packet.getX(), packet.getY(), packet.getZ())));
                    } catch (Throwable e) {
                        Debug.chat(ChatUtils.stringToText("&cInvalid format string: " + e.getMessage()));
                    }
                }
            }
        }
    }
}
