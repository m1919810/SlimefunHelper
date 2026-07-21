package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.entity.player.PlayerEntity;

public class TotemLog extends BaseModule {
    public final ModulePath totem = makePath(Configs.COMBAT_CONFIG, "totem");

    public TotemLog() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(totem.add("log-totem")).build();

    public final FlagRef logTotalCount = builder(totem.add("log-total-count"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef logDeath =
            builder(totem.add("log-death"), Boolean.class).defaultValue(true).build();

    //    public final StringRef logFormat = builder(totem.add("log-totem-format"), StringRef.TYPE)
    //            .defaultValue("&c[Totem]&f %s trigger totem")
    //            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(PlayerStateManager.getPlayerPopTotem(), this::onTotemPop);

        registerListener(PlayerStateManager.getPlayerDeathInfo(), this::onDeath);
    }

    public void onTotemPop(Event<PlayerEntity> event) {
        if (enable.get()) {
            PlayerEntity pl = event.context;
            int popCount = event.getArgs(0);
            if (logTotalCount.get()) {
                Debug.chat(ChatUtils.stringToText(
                        "&c[Totem]&f %s has triggered %d totems".formatted(pl.getNameForScoreboard(), popCount)));
            } else {
                Debug.chat(ChatUtils.stringToText("&c[Totem]&f %s trigger totem"));
            }
        }
    }

    public void onDeath(Event<PlayerEntity> event) {
        if (enable.get() && logDeath.get()) {
            Integer popCount = event.getArgs(1);
            if (popCount != null) {
                int val = popCount;
                Debug.chat(ChatUtils.stringToText("&c[Totem]&f %s died after trigger %d totems"
                        .formatted(event.context.getNameForScoreboard(), val)));
            }
        }
    }
}
