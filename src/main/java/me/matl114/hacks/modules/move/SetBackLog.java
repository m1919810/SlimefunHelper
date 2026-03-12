package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class SetBackLog extends BaseModule {
    public SetBackLog() {}

    public static final String[] MOVE_LOG_RESYNC_PACKETS = {"move-safety", "log-resync-packets"};
    public static final String[] MOVE_LOG_RESYNC_FORMAT = {"move-safety", "log-resync-format"};
    public static final String[] MOVE_CHECK_SETBACK = {"move-safety", "check-setback-packets"};
    public static final String[] MOVE_LOG_AC_FORMAT = {"move-safety", "log-ac-format"};
    public final FlagRef logResync = builder(Configs.MOV_CONFIG, Boolean.class)
            .path(MOVE_LOG_RESYNC_PACKETS)
            .defaultValue(false)
            .build();

    public final StringRef logResyncFormat = builder(Configs.MOV_CONFIG, String.class)
            .path(MOVE_LOG_RESYNC_FORMAT)
            .defaultValue("&fPos Resync &a[%.2f,%.2f,%.2f]")
            .build();

    public final FlagRef logAc = builder(Configs.MOV_CONFIG, Boolean.class)
            .path(MOVE_CHECK_SETBACK)
            .defaultValue(false)
            .build();

    public final StringRef logAcFormat = builder(Configs.MOV_CONFIG, String.class)
            .path(MOVE_LOG_AC_FORMAT)
            .defaultValue("&c[AC] 反作弊回弹! tp号:%d")
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketListenerPoint(PlayerPositionLookS2CPacket.class), this::onSetBack);
    }

    public int maxTpId = -1;
    public Vec3d lastDesyncPos = Vec3d.ZERO;

    public void onSetBack(Event<PlayerPositionLookS2CPacket> event) {
        PlayerPositionLookS2CPacket packet0 = event.context;
        int tpId = packet0.teleportId();
        maxTpId = Math.max(maxTpId, tpId);
        if (mc.player != null) {
            lastDesyncPos = mc.player.getPos();
        }
        var packet = packet0.change().position();

        if (logResync.get()) {
            String logFormat = logResyncFormat.get();
            try {
                var text = ChatUtils.stringToText(logFormat.formatted(packet.getX(), packet.getY(), packet.getZ()));
                text.setStyle(text.getStyle()
                        .withClickEvent(new ClickEvent.CopyToClipboard(
                                "%.2f %.2f %.2f".formatted(packet.getX(), packet.getY(), packet.getZ())))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("click to copy coord"))));
                Debug.chat(text);

            } catch (Throwable e) {
                Debug.chat(ChatUtils.stringToText("&cInvalid format string: " + e.getMessage()));
            }
        }
        if (logAc.get()) {

            if (tpId < 0) {
                if (mc.player != null) {
                    String logFormat = logAcFormat.get();
                    try {
                        Debug.chat(ChatUtils.stringToText(
                                logFormat.formatted(tpId, packet.getX(), packet.getY(), packet.getZ())));
                    } catch (Throwable e) {
                        Debug.chat(ChatUtils.stringToText("&cInvalid format string: " + e.getMessage()));
                    }
                }
            }
        }
    }
}
