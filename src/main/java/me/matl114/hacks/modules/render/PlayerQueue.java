package me.matl114.hacks.modules.render;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.*;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.IntPrimitiveList;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.Debug;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.versioned.api.VRecord;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.world.GameMode;

public class PlayerQueue extends BaseModule {
    public final ModulePath playerIo = makePath(Configs.RENDER_CONFIG, "player-io.player-queue");

    public PlayerQueue() {
        super("PlayerQueue");
    }

    public final FlagRef enable = flagBuilder(playerIo.addEnable()).build();

    public final FlagRef certainOrder =
            flagBuilder(playerIo.add("certain-order")).build();

    public final NBTRef<IntPrimitiveList> mentionOrderList = builder(
                    playerIo.add("tracked-queue-orders"), IntPrimitiveList.class)
            .defaultValue(new IntPrimitiveList(List.of(1, 2, 3, 4, 5, 10, 20)))
            .show(certainOrder::get)
            .build();

    public final FlagRef mentionSkipQueue = builder(playerIo.add("tracked-skip-queue"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef leaveClearCache =
            flagBuilder(playerIo.add("leave-clear-cache")).build();

    public final FlagRef removeTrackAfterJoin =
            flagBuilder(playerIo.add("remove-track-after-join")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getGameJoinPoint(), this::onServerInitialize);
        registerListener(Listener.getServerDisconnectPoint(), this::onServerLeave);
        registerListener(Listener.getServerLeavePoint(), this::onServerChange);
        registerListener(Listener.getOtherPlayerJoinPoint(), this::onPlayerListAdd);
        registerListener(Listener.getOtherPlayerExitPoint(), this::onPlayerListRemove);
        registerListener(Listener.getOtherPlayerEntryUpdate(), this::onPlayerListModify);
        registerCommandBootstrap(this::bootstrapQueueCommand);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitleLabel("widget.player-queue.command", 0, dblank, dx, dy));
    }

    public Set<String> trackedPlayers = new LinkedHashSet<>();

    public Deque<Entry> playerQueue = new ArrayDeque<>();
    public Set<UUID> uniqueSetPlayer = new HashSet<>();

    public void onServerLeave(Event<Void> serverLeave) {
        if (leaveClearCache.get()) {
            trackedPlayers.clear();
        }
    }

    public void onServerChange(Event<Void> serverChange) {
        playerQueue.clear();
        uniqueSetPlayer.clear();
    }

    public void onServerInitialize(Event<ClientPlayerEntity> onGameJoin) {
        playerQueue.clear();
        uniqueSetPlayer.clear();
        Tasks.scheduleDelayed(this::onInitializeQueue, 20);
    }

    public void onInitializeQueue() {
        playerQueue.clear();
        uniqueSetPlayer.clear();
        if (checkNull()) return;
        int currentQueuePosition = 0;
        for (var re : mc.getNetworkHandler().getPlayerList()) {
            if (re.getGameMode() == GameMode.SPECTATOR) {
                currentQueuePosition++;
                playerQueue.addLast(new Entry(
                        VRecord.getId(re.getProfile()),
                        re,
                        currentQueuePosition,
                        currentQueuePosition,
                        true,
                        Tasks.getTick()));
                uniqueSetPlayer.add(VRecord.getId(re.getProfile()));
            }
        }
    }

    public void onJoinPositionChange() {
        IntSet intSet = null;
        int order = 0;
        for (var re : playerQueue) {
            order += 1;
            if (re.initialize) continue;
            re.lastOrder = re.order;
            re.order = order;
            if (enable.get() && re.lastOrder != order) {
                if (intSet == null) {
                    intSet = new IntOpenHashSet(mentionOrderList.get().list());
                }
                String name = VRecord.getName(re.entry.getProfile());
                if (trackedPlayers.contains(name) && (!certainOrder.get() || intSet.contains(order))) {
                    logI18N("message.module.player-queue.current-order", name, order);
                }
            }
        }
    }

    public void onTrackedLeave(Entry entry, boolean leaveServer) {
        if (!trackedPlayers.isEmpty()) {
            String name = VRecord.getName(entry.entry.getProfile());
            if (trackedPlayers.contains(name)) {
                if (enable.get()) {
                    if (leaveServer) {
                        logI18N("message.module.player-queue.leave-server", name);
                    } else {
                        boolean maySkipQueue = mentionSkipQueue.get() && !entry.initialize && entry.order > 3;
                        logI18N("message.module.player-queue.finish-queue", name, (maySkipQueue ? "(skip?)" : ""));
                    }
                }
                if (!leaveServer && removeTrackAfterJoin.get()) {
                    trackedPlayers.remove(name);
                    if (enable.get()) {
                        logI18N("message.module.player-queue.auto-untrack", name);
                    }
                }
            }
        }
    }

    public void playerJoinQueue(PlayerListEntry entry) {
        UUID uid = VRecord.getId(entry.getProfile());
        if (!uniqueSetPlayer.contains(uid)) {
            uniqueSetPlayer.add(uid);
            playerQueue.addLast(new Entry(uid, entry, playerQueue.size(), 0, false, Tasks.getTick()));
        }
    }

    public Entry leaveQueue(UUID uid) {
        if (uniqueSetPlayer.contains(uid)) {
            uniqueSetPlayer.remove(uid);
            var iter = playerQueue.iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                if (Objects.equals(entry.uuid, uid)) {
                    iter.remove();
                    return entry;
                }
            }
            return null;
        } else {
            return null;
        }
    }

    public void playerFinishQueue(PlayerListEntry entry) {
        UUID uid = VRecord.getId(entry.getProfile());
        var val = leaveQueue(uid);
        if (val != null) {
            onJoinPositionChange();
            onTrackedLeave(val, false);
        }
    }

    public void playerFinishServer(PlayerListEntry entry) {
        UUID uid = VRecord.getId(entry.getProfile());
        var val = leaveQueue(uid);
        if (val != null) {
            onJoinPositionChange();
            onTrackedLeave(val, true);
        }
    }

    public void onPlayerListAdd(Event<PlayerListEntry> entry) {
        String name = VRecord.getName(entry.context.getProfile());
        if (trackedPlayers.contains(name)) {
            if (enable.get()) {
                logI18N("message.module.player-queue.join-server", name);
            }
        }
        if (entry.context.getGameMode() == GameMode.SPECTATOR) {
            playerJoinQueue(entry.context);
        } else {
            // player skip queue, nothing to do with the queue
        }
    }

    public void onPlayerListModify(Event<PlayerListEntry> entry) {
        if (entry.getArgs(0) == PlayerListS2CPacket.Action.UPDATE_GAME_MODE) {
            if (entry.context.getGameMode() == GameMode.SPECTATOR) {
                playerJoinQueue(entry.context);
            } else {
                playerFinishQueue(entry.context);
            }
        }
    }

    public void onPlayerListRemove(Event<PlayerListEntry> entry) {
        if (entry.context.getGameMode() == GameMode.SPECTATOR) {
            playerFinishServer(entry.context);
        }
    }

    public void addTrackPlayer(String string) {
        if (trackedPlayers.contains(string)) {
            logI18N("message.module.player-queue.already-tracked");
        } else {
            trackedPlayers.add(string);
            logI18N("message.module.player-queue.start-tracking", string);
        }
    }

    public void removeTrackPlayer(String string) {
        if (trackedPlayers.contains(string)) {
            trackedPlayers.remove(string);
            logI18N("message.module.player-queue.stop-tracking", string);
        } else {
            logI18N("message.module.player-queue.not-tracked");
        }
    }

    public void clearTrack() {
        trackedPlayers.clear();
        logI18N("message.module.player-queue.clear-all");
    }

    public void listTrackedDetails() {
        logI18N("message.module.player-queue.current-tracked");
        Map<String, Entry> maps = new LinkedHashMap<>();
        for (var re : playerQueue) {
            String string = VRecord.getName(re.entry.getProfile());
            if (trackedPlayers.contains(string)) {
                maps.put(string, re);
            }
        }
        for (var re : trackedPlayers) {
            var entry = maps.get(re);
            if (entry != null) {
                Debug.chat("-", re, "(queuing,", (entry.initialize ? "order=unknown)" : "order=" + entry.order + ")"));
            } else {
                var pentry = mc.getNetworkHandler().getPlayerListEntry(re);
                if (pentry != null) {
                    Debug.chat("-", re, pentry.getGameMode() == GameMode.SURVIVAL ? "(online)" : "(queuing)");

                } else {
                    Debug.chat("-", re, "(offline)");
                }
            }
        }
    }

    public void checkTrackedInfo(String name) {
        logI18N("message.module.player-queue.tracked-info", name);
        for (var entry : playerQueue) {
            String string = VRecord.getName(entry.entry.getProfile());
            if (Objects.equals(string, name)) {
                Debug.chat(
                        "-", name, "(queuing,", (entry.initialize ? "order=unknown)" : "order=" + entry.order + ")"));
                return;
            }
        }
        var pentry = mc.getNetworkHandler().getPlayerListEntry(name);
        if (pentry != null) {
            Debug.chat("-", name, pentry.getGameMode() == GameMode.SURVIVAL ? "(online)" : "(queuing)");

        } else {
            Debug.chat("-", name, "(offline)");
        }
    }

    public void bootstrapQueueCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.subMainBuilder().name("pqueue").build();
        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("add")
                    .helper("message.command.pqueue.add.help")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("name")
                            .tabSupplier(WorldUtils::getPlayerListNames)
                            .build())
                    .post(s -> s.executor(CommandContext.run((ar) -> addTrackPlayer(ar.nextNonnullString()))))
                    .complete()
                    .subBuilder(SubCommand.taskBuilder())
                    .name("remove")
                    .helper("message.command.pqueue.remove.help")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("name")
                            .tabSupplier(WorldUtils::getPlayerListNames)
                            .build())
                    .post(s -> s.executor(CommandContext.run((ar) -> removeTrackPlayer(ar.nextNonnullString()))))
                    .complete()
                    .subBuilder(SubCommand.taskBuilder())
                    .name("list")
                    .helper("message.command.pqueue.list.help")
                    .post(s -> s.executor(CommandContext.run(this::listTrackedDetails)))
                    .complete()
                    .subBuilder(SubCommand.taskBuilder())
                    .name("clear")
                    .helper("message.command.pqueue.clear.help")
                    .post(s -> s.executor(CommandContext.run(this::clearTrack)))
                    .complete()
                    .subBuilder(SubCommand.taskBuilder())
                    .name("check")
                    .helper("message.command.pqueue.check.help")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("name")
                            .tabSupplier(WorldUtils::getPlayerListNames)
                            .build())
                    .post(s -> s.executor(CommandContext.run((ar) -> this.checkTrackedInfo(ar.nextNonnullString()))))
                    .complete();
        }
    }

    @AllArgsConstructor
    public static class Entry {
        final UUID uuid;
        final PlayerListEntry entry;
        int order;
        int lastOrder;
        final boolean initialize;
        final int startTick;
    }
}
