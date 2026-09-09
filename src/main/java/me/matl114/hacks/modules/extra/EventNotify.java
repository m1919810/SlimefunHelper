package me.matl114.hacks.modules.extra;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.IntPrimitiveList;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.render.NotifyType;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.hooks.impl.baritone.BaritoneFuture;
import me.matl114.hooks.impl.baritone.BaritoneLanding;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.WindowUtils;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;

public class EventNotify extends BaseModule {
    public final ModulePath path = makePath(Configs.EXTRA_CONFIG, "other.queue-notify");
    public static EventNotify INSTANCE;

    public EventNotify() {
        super("EventNotify");
        INSTANCE = this;
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(path.addHotkey(), new MultiKeyBind(), path.addEnable()).build();

    public final EnumRef<NotifyType> mode = builder(path.add("mode"), NotifyType.class)
            .defaultValue(NotifyType.TRAY)
            .build();

    public final FlagRef onlyMin = flagBuilder(path.add("min-only")).build();

    public final DoubleRef minScreenWidth =
            doubleBuilder(path.add("min-width")).defaultValue(0.0D).build();

    public final FlagRef enableQueue = flagBuilder(path.add("enable-queue")).build();

    public final NBTRef<Regex> queueRegex = builder(path.add("queue-3c-title-regex"), Regex.class)
            .defaultValue(new Regex(".*(正在游玩.*队列位置|Position.*queue)[：:]\\s*(\\d+)"))
            .build();

    public final NBTRef<IntPrimitiveList> order = builder(path.add("order"), IntPrimitiveList.class)
            .defaultValue(new IntPrimitiveList(List.of(5, 10)))
            .build();

    public final FlagRef leave =
            flagBuilder(path.add("enable-disconnect-server")).build();

    public final FlagRef enablePop = flagBuilder(path.add("enable-pop-totem")).build();

    public final FlagRef enableBaritoneEnd =
            flagBuilder(path.add("enable-baritone-end")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPostHandlePoint().getChannel(SubtitleS2CPacket.class), this::onTitle);
        registerListener(Listener.getPacketPostHandlePoint().getChannel(EntityStatusS2CPacket.class), this::onTotemPop);
        registerListener(Listener.getServerLeavePoint(), this::onReconfiguration);
        registerListener(Listener.getServerDisconnectPoint(), this::onLeaveServer);
        registerListener(BaritoneHooks.getLandingEvent(), this::onBaritoneEnd);
    }

    int lastOrder = 0;

    public void onTitle(Event<SubtitleS2CPacket> eventTitle) {
        if (enable.get() && enableQueue.get()) {
            String text = ChatUtils.textToPlainString(eventTitle.context.text());
            if (queueRegex.get().test(text)) {
                Matcher matcher = Pattern.compile("\\d+").matcher(text);
                while (matcher.find()) {
                    if (matcher.start() == 0) continue;
                    try {
                        int val = Integer.parseInt(matcher.group());
                        if (lastOrder != val) {
                            lastOrder = val;
                            if (order.get().list().contains(val)) {
                                if (checkMin()) {
                                    return;
                                }
                                notify("[SlimefunHelper]排队提醒", "你已经抵达队列位置: " + val);
                                return;
                            }
                        }

                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
    }

    public void onReconfiguration(Event<Void> eventReconfiguration) {
        if (enable.get() && enableQueue.get() && !eventReconfiguration.<Boolean>getArgs(0)) {
            if (lastOrder != 0) {
                lastOrder = 0;
                if (checkMin()) {
                    return;
                }
                notify("[SlimefunHelper]排队提醒", "你已经完成排队进入服务器!");
            }
        }
    }

    public void onTotemPop(Event<EntityStatusS2CPacket> event) {
        if (checkNull()) return;
        if (enable.get()
                && enablePop.get()
                && event.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
                && event.context.getEntity(mc.world) == mc.player) {
            if (checkMin()) {
                return;
            }
            notify("[SlimefunHelper]图腾提醒", "你触发了不死图腾");
        }
    }

    public void onLeaveServer(Event<Void> eventLeave) {
        if (enable.get() && leave.get()) {
            if (checkMin()) {
                return;
            }
            notify("[SlimefunHelper]离线提醒", "你离开了服务器");
        }
    }

    public void onBaritoneEnd(Event<BaritoneFuture> event) {
        if (enable.get() && enableBaritoneEnd.get()) {
            if (checkMin()) {
                return;
            }
            notify(
                    "[SlimefunHelper]Baritone提醒",
                    "Baritone落地: " + event.<BaritoneLanding>getArgs(0).name());
        }
    }

    public void notify(String title, String message) {
        switch (mode.get()) {
            case TRAY -> {
                WindowUtils.createNotificationTrayWindow(title, message);
            }
            case PS_WINDOW -> {
                WindowUtils.createScriptNotificationWindow(title, message);
            }
        }
    }

    public boolean checkMin() {
        return onlyMin.get() && mc.getWindow().getWidth() > minScreenWidth.get();
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createExecuteButton(
                "widget.event-notify.test-usage",
                ButtonAction.run(() -> {
                    notify("[SlimefunHelper]测试", "HelloWorld");
                }),
                0,
                dblank,
                dx,
                dy));
    }
}
