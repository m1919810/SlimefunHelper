package me.matl114.hacks.modules.combat;

import java.util.List;
import me.matl114.events.Event;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.StringFormat;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import net.minecraft.entity.player.PlayerEntity;

public class TotemLog extends BaseModule {
    public final ModulePath totem = makePath(Configs.COMBAT_CONFIG, "totem");

    public TotemLog() {
        super("TotemLog");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(totem.add("log-totem")).build();

    public final FlagRef logTotalCount = builder(totem.add("log-total-count"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef logDeath =
            builder(totem.add("log-death"), Boolean.class).defaultValue(true).build();

    public final NBTRef<StringFormat> logPopCountFormat = builder(totem.add("log-pop-count-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("name", "count"), "{name} has triggered {count} totems", true))
            .build();

    public final NBTRef<StringFormat> logDeathFormat = builder(totem.add("log-death-format"), StringFormat.class)
            .defaultValue(new StringFormat(List.of("name", "count"), "{name} died after trigger {count} totems", true))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(PlayerStateManager.getPlayerPopTotem(), this::onTotemPop);

        registerListener(PlayerStateManager.getPlayerDeathInfo(), this::onDeath);
    }

    public void onTotemPop(Event<PlayerEntity> event) {
        if (enable.get() && logTotalCount.get()) {
            PlayerEntity pl = event.context;
            int popCount = event.getArgs(0);
            logSub("Totem", logPopCountFormat.get().formatText(pl.getNameForScoreboard(), popCount));
        }
    }

    public void onDeath(Event<PlayerEntity> event) {
        if (enable.get() && logDeath.get()) {
            Integer popCount = event.getArgs(1);
            if (popCount != null) {
                int val = popCount;
                logSub("Totem", logDeathFormat.get().formatText(event.context.getNameForScoreboard(), val));
            }
        }
    }
}
