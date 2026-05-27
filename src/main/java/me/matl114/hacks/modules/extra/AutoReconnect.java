package me.matl114.hacks.modules.extra;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public class AutoReconnect extends BaseModule {
    public final ModulePath autoReconnect = makePath(Configs.TEST_CONFIG, "other.auto-reconnect");

    public final FlagRef enable = flagBuilder(autoReconnect.add("enable")).build();

    public final FlagRef enableB =
            flagBuilder(autoReconnect.add("enable-buttons")).build();

    public final IntRef delay = intBuilder(autoReconnect.add("delay"))
            .defaultValue(5)
            .validator(Configs.INT_POSITIVE)
            .build();

    public AutoReconnect() {
        bindFlag(enable);
    }

    int counter = 0;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPostInitializeScreen().getChannel(DisconnectedScreen.class), this::onScreenInitialize);
        registerListener(
                Listener.getPostSetScreen().getChannel(DisconnectedScreen.class), this::onServerDisconnectScreenSetup);
        registerListener(Listener.getServerPreConnectPoint(), this::onServerConnect);
    }

    public void onServerConnect(Event<ServerAddress> event) {
        lastServer = event.getArgs(0);
        // reset all reconnect tasks
        counter = -999;
    }

    ServerInfo lastServer;

    public void onScreenInitialize(Event<DisconnectedScreen> event) {
        if (enableB.get()) {
            var disconnected = event.context;
            SubScreenWidget widget = new SubScreenWidget(0, 0, 200, 50);
            widget.addDrawableChild(ExecutableWidget.instance(0, 25, 200, 20)
                    .setElementHandler(new ButtonElement(
                            (el -> enable.get()
                                    ? Text.literal(
                                            "Toggle Auto reconnect off (%d sec)".formatted(Math.max(counter, 0) / 20))
                                    : Text.literal("Toggle Auto reconnect on")),
                            ButtonAction.run(enable::toggle))));
            widget.addDrawableChild(ExecutableWidget.instance(0, 0, 200, 20)
                    .setElementHandler(
                            new ButtonElement(TextProvider.of(Text.literal("Reconnect")), ButtonAction.run(() -> {
                                if (enable.get() && counter > 0) {
                                    counter = 0;
                                } else {
                                    Tasks.scheduleDelayed(() -> this.reconect((disconnected.parent)), 0);
                                }
                            }))));
            disconnected.grid.add(widget);
            widget.addTo(disconnected);
            disconnected.grid.refreshPositions();
        }
    }

    public void onServerDisconnectScreenSetup(Event<DisconnectedScreen> event) {
        if (enable.get()) {
            var disconnected = event.context;
            counter = delay.get() * 20;
            Tasks.scheduleRepeated(
                    () -> {
                        if (counter > 0) {
                            counter--;
                            return false;
                        }
                        if (enable.get() && mc.currentScreen instanceof DisconnectedScreen) {
                            reconect(disconnected.parent);
                            return true;
                        }
                        return mc.currentScreen != null;
                    },
                    1,
                    1);
        }
    }

    public void reconect(Screen screen) {
        if (lastServer != null) {
            ConnectScreen.connect(screen, mc, ServerAddress.parse(lastServer.address), lastServer, false, null);
        }
    }
}
