package me.matl114.hacks.modules.render;

import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.StringFormat;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VRecord;
import net.minecraft.client.network.PlayerListEntry;

public class PlayerLog extends BaseModule {
    public final ModulePath playerIo = makePath(Configs.RENDER_CONFIG, "player-io");

    public PlayerLog() {
        super("PlayerLog");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(playerIo.add("log-player-io")).build();

    public final NBTRef<StringFormat> logFormatIn = builder(playerIo.add("log-player-in-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("name"), "&7&l[&a&l+&7&l] &f{name}", true))
            .build();

    public final NBTRef<StringFormat> logFormatOut = builder(playerIo.add("log-player-out-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("name"), "&7&l[&c&l-&7&l] &f{name}", true))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getOtherPlayerJoinPoint(), this::onPlayerJoin);
        registerListener(Listener.getOtherPlayerExitPoint(), this::onPlayerExit);
    }

    public void onPlayerJoin(Event<PlayerListEntry> entry) {
        if (enable.get()) {
            Debug.chat(
                    logFormatIn.get().formatText(VRecord.getName(entry.context().getProfile())));
        }
    }

    public void onPlayerExit(Event<PlayerListEntry> entry) {
        if (enable.get()) {
            Debug.chat(logFormatOut
                    .get()
                    .formatText(VRecord.getName(entry.context().getProfile())));
        }
    }
}
