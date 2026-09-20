package me.matl114.hacks.modules.extra;

import me.matl114.events.Event;
import me.matl114.events.PacketManager;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.text.Text;

public class FakeLag extends BaseModule {
    public FakeLag() {
        super("FakeLag");
        bindFlag(enable);
    }

    final ModulePath root = makePath(Configs.EXTRA_CONFIG, "other.fake-lag");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(
                    root.addHotkey(),
                    new MultiKeyBind(),
                    root.addEnable(),
                    () -> Text.literal("%dMS".formatted(this.delayMs.get())))
            .build();

    public final IntRef delayMs =
            intBuilder(root.add("delay-ms")).defaultValue(25).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(PacketManager.getPacketQueueInEvent(), this::onPacketInBound, Integer.MIN_VALUE);
    }

    @Override
    public <W> void unregisterAll() {
        super.unregisterAll();
    }

    int joinServerTick;

    public void onPacketInBound(Event<PacketStorage> event) {
        if (checkNull()) {
            joinServerTick = Tasks.getTick();
            return;
        }
        if (enable.get()) {
            var storage = event.context;
            var type = storage.packetType();
            if (PacketManager.isAsyncOrNotTransactionS2CPacket(type)) {
                return;
            }
            long systemMs = System.currentTimeMillis();
            if (systemMs > event.context.timestampMS() + delayMs.get() - 1) {
                return;
            }
            event.cancel();
        }
    }
}
