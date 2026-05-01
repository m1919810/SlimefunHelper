package me.matl114.hacks.modules.ac;

import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;

public class DisablerManager extends BaseModule {
    public final FlagRef grimSelfCheck = builder(
                    Configs.TEST_CONFIG, makePath("disablers.grim-self-check"), Boolean.class)
            .defaultValue(true)
            .build();

    public DisablerManager() {}

    boolean grimSelfCheckDisabler;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getServerLeavePoint(), this::onDisconnect);
        registerListener(Listener.getPacketPoint().getChannel(PlayerRespawnS2CPacket.class), this::onRespawn);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetReload);
    }

    public void onRespawn(Event<PlayerRespawnS2CPacket> respawn) {
        if (!grimSelfCheckDisabler) {
            grimSelfCheckDisabler = true;
        }
    }

    public boolean isGrimSelfCheckDisabled() {
        return grimSelfCheck.get() && grimSelfCheckDisabler;
    }

    public void onDisconnect(Event<Void> eventDisconnect) {
        grimSelfCheckDisabler = false;
    }

    public void onPresetReload(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case AC_GRIM -> {
                grimSelfCheck.set(true);
            }
            default -> {
                grimSelfCheck.set(false);
            }
        }
    }
}
