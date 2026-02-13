package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class TotemLog extends BaseModule {
    public static final String[] TOTEM_LOG = {"totem", "log-totem"};

    public static final String[] TOTEM_LOG_FORMAT = {"totem", "log-totem-format"};

    public TotemLog() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.COMBAT_CONFIG, TOTEM_LOG).build();

    public final StringRef logFormat = builder(Configs.COMBAT_CONFIG, TOTEM_LOG_FORMAT, StringRef.TYPE)
            .defaultValue("&c[Totem]&f %s trigger totem")
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EntityStatusS2CPacket.class), this::onTotemUsage);
    }

    public void onTotemUsage(Event<EntityStatusS2CPacket> statusPacket) {
        EntityStatusS2CPacket statusS2CPacket = statusPacket.context();
        if (enable.get() && statusS2CPacket.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING && mc.world != null) {
            Entity entity = statusS2CPacket.getEntity(mc.world);
            if (entity instanceof PlayerEntity player) {
                String name = player.getNameForScoreboard();
                try {
                    var text = ChatUtils.stringToText(logFormat.get().formatted(name));
                    Debug.chat(text);
                } catch (Throwable e) {
                    Debug.chat(ChatUtils.stringToText("&cInvalid format string: " + e.getMessage()));
                }
            }
        }
    }
}
