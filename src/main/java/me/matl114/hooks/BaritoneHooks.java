package me.matl114.hooks;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.ChatEvent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.MinecraftClient;

public abstract class BaritoneHooks implements IHooks {

    private static BaritoneHooks instance;

    public static BaritoneHooks getInstance() {
        if (instance == null) {
            try {
                instance = new Impl();
            } catch (Throwable e) {
                instance = new Default();
            }
        }
        return instance;
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public abstract boolean handleCommand(String command);

    public abstract <T> ValueAccessor<T> getSetting(String name);

    public abstract boolean isElytraProcessing();

    public static class Impl extends BaritoneHooks {
        Settings settings;
        Map<String, ValueAccessor<?>> settingsMap = new LinkedHashMap<>();

        public Impl() {
            settings = BaritoneAPI.getSettings();
            buildMap();
        }

        private void buildMap() {
            for (Settings.Setting re : settings.allSettings) {
                String nameLowerCase = re.getName().toLowerCase();
                ValueAccessor accessor = ValueAccessor.of(() -> re.value, (va) -> re.value = va);
                settingsMap.put(nameLowerCase, accessor);
            }
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean handleCommand(String command) {
            ChatEvent var4 = new ChatEvent(command);
            IBaritone var3;
            if ((var3 = BaritoneAPI.getProvider().getBaritoneForPlayer(mc.player)) != null) {
                var3.getGameEventHandler().onSendChatMessage(var4);
                if (var4.isCancelled()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public <T> ValueAccessor<T> getSetting(String name) {
            return (ValueAccessor<T>) settingsMap.get(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public boolean isElytraProcessing() {
            return BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getElytraProcess()
                    .isActive();
        }
    }

    public static class Default extends BaritoneHooks {

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean handleCommand(String command) {
            return false;
        }

        @Override
        public <T> ValueAccessor<T> getSetting(String name) {
            return null;
        }

        @Override
        public boolean isElytraProcessing() {
            return false;
        }
    }
}
