package me.matl114.hacks.modules.interact;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;

public class InteractExtra extends BaseModule {
    public static final String[] NO_COOLDOWN_ENABLE = {"interact-fix", "no-cool-down"};
    public static final String[] NO_COOLDOWN_VALUE = {"interact-fix", "cool-down-rewrite"};
    public static final String[] INTERACT_WHEN_RIDING = {"interact-fix", "allow-ride-interact"};

    public InteractExtra() {}

    public final FlagRef noCooldown =
            flagBuilder(Configs.INTERACT_CONFIG, NO_COOLDOWN_ENABLE).build();

    public final IntRef noCooldownValue = builder(Configs.INTERACT_CONFIG, NO_COOLDOWN_VALUE, IntRef.TYPE)
            .defaultValue(4)
            .build();

    public final FlagRef rideUse = builder(Configs.INTERACT_CONFIG, INTERACT_WHEN_RIDING, FlagRef.TYPE)
            .defaultValue(true)
            .build();

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
