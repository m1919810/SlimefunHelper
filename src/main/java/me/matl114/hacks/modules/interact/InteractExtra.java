package me.matl114.hacks.modules.interact;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;

public class InteractExtra extends BaseModule {
    public final ModulePath interactFix = makePath(Configs.INTERACT_CONFIG, "interact-fix");
    public static InteractExtra INSTANCE;
    public InteractExtra() {
        INSTANCE = this;
    }

    public final FlagRef noCooldown =
            flagBuilder(interactFix.add("no-cool-down")).build();

    public final IntRef noCooldownValue =
            intBuilder(interactFix.add("cool-down-rewrite")).defaultValue(4).build();

    public final FlagRef rideUse = builder(interactFix.add("allow-ride-interact"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef holdUse = builder(interactFix.add("hold-use"), FlagRef.TYPE)
            .defaultValue(false)
            .build();

    public final IntRef holdUseStartTick =
            intBuilder(interactFix.add("hold-use-start-tick")).defaultValue(4).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getUseItemCooldownReset(), this::onCooldown);
    }

    public void onCooldown(Event<Integer> event) {
        if (noCooldown.get() && noCooldownValue.get() >= 0) {
            event.context(noCooldownValue.get());
        }
    }
}
