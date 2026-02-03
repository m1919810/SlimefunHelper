package me.matl114.hacks.modules.move;

import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.events.Event;

public class NoSlowDown extends BaseModule {
    public static final String[] NO_SLOW_DOWN_SNEAK = {"move-speed","no-slowdown", "when-sneak"};
    public static final String[] NO_SLOW_DOWN_USEITEM = {"move-speed","no-slowdown", "when-use-item"};
    //    public static final String[] NO_SLOW_DOWN_BLOCK_FRAC = {"move-speed","no-slowdown", "when-walk-on-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_SLOW = {"move-speed","no-slowdown", "when-with-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_FRAC = {"move-speed","no-slowdown", "when-on-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_IN = {"move-speed","no-slowdown", "when-in-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_SPECIAL = {"move-speed","no-slowdown", "when-special-block"};

    public NoSlowDown() {

    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public final FlagRef sneak = flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_SNEAK)
        .build();

    public final FlagRef useItem = flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_USEITEM)
        .build();

    public final FlagRef blockSlow = flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_SLOW)
        .build();

    public final FlagRef blockFrac = flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_FRAC)
        .build();

    public final FlagRef blockIn = flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_IN)
        .build();

    public final FlagRef blockSpecial = flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_SPECIAL)
        .build();


    public void onModulePreset(Event<EventContainer<ModulePreset>> event){
        ModulePreset preset = event.context().getValue();
        switch (preset){
            case HACKING -> {
                sneak.set(true);
                useItem.set(true);
                blockSlow.set(true);
                blockFrac.set(true);
                blockIn.set(true);
                blockSpecial.set(true);
            }
            default -> {
                sneak.set(false);
                useItem.set(false);
                blockSlow.set(false);
                blockFrac.set(false);
                blockIn.set(false);
                blockSpecial.set(false);
            }
        }
    }
}
