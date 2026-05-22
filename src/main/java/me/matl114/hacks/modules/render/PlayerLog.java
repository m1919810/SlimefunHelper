package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.client.network.PlayerListEntry;

public class PlayerLog extends BaseModule {
    public final ModulePath playerIo = makePath(Configs.RENDER_CONFIG, "player-io");

    public PlayerLog() {}

    public final FlagRef enable =
            flagBuilder(playerIo.add("log-player-io")).build();

    public final StringRef logFormatIn = builder(playerIo.add("log-player-in-format"), StringRef.TYPE)
            .defaultValue("&7&l[&a&l+&7&l] &f%s")
            .build();

    public final StringRef logFormatOut = builder(playerIo.add("log-player-out-format"), StringRef.TYPE)
            .defaultValue("&7&l[&c&l-&7&l] &f%s")
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getOtherPlayerJoinPoint(), this::onPlayerJoin);
        registerListener(Listener.getOtherPlayerExitPoint(), this::onPlayerExit);
    }

    public void onPlayerJoin(Event<PlayerListEntry> entry) {
        if (enable.get()) {
            Debug.chat(ChatUtils.stringToText(String.format(
                    logFormatIn.get(), entry.context().getProfile().getName())));
        }
    }

    public void onPlayerExit(Event<PlayerListEntry> entry) {
        if (enable.get()) {
            Debug.chat(ChatUtils.stringToText(String.format(
                    logFormatOut.get(), entry.context().getProfile().getName())));
        }
    }
}
