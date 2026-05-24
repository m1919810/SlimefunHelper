package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
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
    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");

    public SetBackLog() {}

    public final FlagRef logResync =
            flagBuilder(moveSafety.add("log-resync-packets")).build();

    public final StringRef logResyncFormat = builder(moveSafety.add("log-resync-format"), String.class)
            .defaultValue("&fPos Resync &a[%.2f,%.2f,%.2f]")
            .build();

    public final FlagRef logAc =
            flagBuilder(moveSafety.add("check-setback-packets")).build();

    public final StringRef logAcFormat = builder(moveSafety.add("log-ac-format"), String.class)
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
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                "%.2f %.2f %.2f".formatted(packet.getX(), packet.getY(), packet.getZ())))
                        .withHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("click to copy coord"))));
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
