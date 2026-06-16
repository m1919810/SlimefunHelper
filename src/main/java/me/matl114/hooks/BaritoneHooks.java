package me.matl114.hooks;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.ChatEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

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

    public abstract void setBaritoneNetherPathSupplier(Supplier<List<BlockPos>> blockPos);

    public abstract void updateBaritoneNetherPath();

    public abstract void setBaritoneCurrentElytraDestination(BlockPos pos);

    public abstract void cancelBaritone();

    public static class Impl extends BaritoneHooks {
        Settings settings;
        Map<String, ValueAccessor<?>> settingsMap = new LinkedHashMap<>();

        public static Supplier<List<BlockPos>> netherPathSupplier;

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
            String pfx = BaritoneAPI.getSettings().prefix.value;
            command = command.startsWith(pfx) ? command : (pfx + command);
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

        @Override
        public void setBaritoneNetherPathSupplier(Supplier<List<BlockPos>> blockPos) {
            netherPathSupplier = blockPos;
        }

        @Override
        public void updateBaritoneNetherPath() {
            BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().resetState();
        }

        @Override
        public void setBaritoneCurrentElytraDestination(BlockPos pos) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(pos);
        }

        @Override
        public void cancelBaritone() {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
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

        @Override
        public void setBaritoneNetherPathSupplier(Supplier<List<BlockPos>> blockPos) {}

        @Override
        public void updateBaritoneNetherPath() {}

        @Override
        public void setBaritoneCurrentElytraDestination(BlockPos pos) {}

        @Override
        public void cancelBaritone() {}
    }
}
