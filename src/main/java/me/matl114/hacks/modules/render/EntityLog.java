package me.matl114.hacks.modules.render;

import java.util.*;
import lombok.AllArgsConstructor;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.render.RenderCollector;
import me.matl114.versioned.api.VDrawContext;
import me.matl114.versioned.api.VRecord;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public class EntityLog extends BaseModule {
    public final ModulePath entityLog = makePath(Configs.RENDER_CONFIG, "detect-entity.entity-log");

    public EntityLog() {
        super("EntityLog");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(entityLog.add("enable")).build();
    public final KeyBindRef hotkeyToggle = toggleHotkey(
                    entityLog.add("hotkey"), new MultiKeyBind(), entityLog.add("enable"))
            .build();

    public final NBTRef<EntrySet<EntityType<?>>> whiteList = builder(
                    entityLog.add("whitelist"), EntrySet.<EntityType<?>>parameter())
            .defaultValue(new EntrySet<>(new Regex("player"), Registries.ENTITY_TYPE))
            .build();

    public final FlagRef chatLog = builder(entityLog.add("log-entity-to-chat"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef logLog =
            flagBuilder(entityLog.add("log-log-player-to-chat")).build();

    public final FlagRef renderLogPosition =
            flagBuilder(entityLog.add("render-log-players")).build();

    public final NBTRef<TracingOption> option = builder(entityLog.add("render-options"), TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();

    public final NBTRef<WrapColor> color = builder(entityLog.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.LIGHT_PURPLE))
            .build();

    public final FlagRef renderLog = builder(entityLog.add("render-reason-log"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef renderTp = builder(entityLog.add("render-reason-teleport"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef renderFaraway =
            flagBuilder(entityLog.add("render-reason-faraway")).build();

    public final FlagRef logLogReconnect =
            flagBuilder(entityLog.add("log-log-reconnect")).build();

    public final NBTRef<StringFormat> logSpawnFormat = builder(entityLog.add("log-spawn-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("type", "name", "position", "distance"),
                    "{type} {name} spawn at position {position}, distance: {distance}",
                    true))
            .build();

    public final NBTRef<StringFormat> logDisappearFormat = builder(
                    entityLog.add("log-disappear-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("type", "name", "position", "distance"),
                    "{type} {name} disappear at position {position}, distance: {distance}",
                    true))
            .build();

    public final NBTRef<StringFormat> logPlayerLogoutFormat = builder(
                    entityLog.add("log-player-logout-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("name", "action", "position"), "Player {name} {action} at {position}", true))
            .build();

    public final NBTRef<StringFormat> logPlayerJoinQueueFormat = builder(
                    entityLog.add("log-player-join-queue-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("name", "position", "queue_count"),
                    "Player {name} join queue, last position: {position}, current queue: {queue_count}",
                    true))
            .build();

    public final NBTRef<StringFormat> logPlayerReloginFormat = builder(
                    entityLog.add("log-player-relogin-format"), StringFormat.class)
            .defaultValue(new StringFormat(
                    List.of("name", "position", "track_button"),
                    "Player {name} reLogin, last position: {position} {track_button}",
                    true))
            .build();

    public Map<UUID, Entry> offLinePos = new LinkedHashMap<>();

    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntitySpawnS2CPacket.class), this::onEntitySpawn);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EntitiesDestroyS2CPacket.class), this::onEntityRemove);
        registerListener(Listener.getServerDisconnectPoint(), this::onServerExit);
        registerListener(Listener.getPostTick(), this::onUpdate);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(Listener.getOtherPlayerJoinPoint(), this::onPlayerListEntryAdd);
        registerListener(Listener.getOtherPlayerEntryUpdate(), this::onPlayerListEntryModify);
        registerListener(Listener.getOtherPlayerExitPoint(), this::onPlayerListEntryRemove);
        registerListener(RenderListener.getRender2DEvent(), this::onRender2D);
    }

    public void onEntitySpawn(Event<EntitySpawnS2CPacket> packetEvent) {
        // Debug.info("check entity", packet.getEntityType());
        if (checkNull()) return;
        if (loginServerCheck()) return;
        var packet = packetEvent.context();
        if (enable.get()) {
            if (whiteList.get().test(packet.getEntityType())) {
                EntityType<?> type = packet.getEntityType();
                if (type == EntityType.PLAYER) {
                    if (chatLog.get()) {
                        String name = null;
                        if (MinecraftClient.getInstance().world != null) {
                            PlayerListEntry entry = MinecraftClient.getInstance()
                                    .getNetworkHandler()
                                    .getPlayerListEntry(packet.getUuid());
                            if (entry != null) {
                                name = VRecord.getName(entry.getProfile());
                            }
                        }
                        logSub(
                                "Entity",
                                logSpawnFormat
                                        .get()
                                        .formatText(
                                                "Player",
                                                name == null ? "" : name,
                                                ChatUtils.getDisplayedLocation(
                                                        packet.getX(), packet.getY(), packet.getZ()),
                                                formatDistance(packet.getX(), packet.getY(), packet.getZ())));
                    }
                    onPlayerAppear(packet.getUuid());
                } else {
                    // if(LivingEntity.class.isAssignableFrom( packet.getEntityType().getBaseClass())){
                    // only log the living Entity; the common Entities are mostly functional and are noisy
                    if (chatLog.get()) {
                        logSub(
                                "Entity",
                                logSpawnFormat
                                        .get()
                                        .formatText(
                                                "Entity",
                                                packet.getEntityType().getName(),
                                                ChatUtils.getDisplayedLocation(
                                                        packet.getX(), packet.getY(), packet.getZ()),
                                                formatDistance(packet.getX(), packet.getY(), packet.getZ())));
                    }
                }
            }
        }
    }

    private static double calculateDistance(double x1, double y1, double z1) {
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            return Math.sqrt(player.getPos().squaredDistanceTo(x1, y1, z1));
        }
        return -1.0f;
    }

    private static String formatDistance(double x1, double y1, double z1) {
        return "%.2f".formatted(calculateDistance(x1, y1, z1));
    }

    public void onEntityRemove(Event<EntitiesDestroyS2CPacket> packetEvent) {
        if (checkNull()) return;
        if (loginServerCheck()) return;
        if (enable.get()) {
            var packet = packetEvent.context();
            if (mc.world != null) {
                Set<Entity> removing = new LinkedHashSet<>();
                for (int i : packet.getEntityIds()) {
                    Entity entity = mc.world.getEntityById(i);
                    if (entity == null) continue;
                    if (whiteList.get().test(entity.getType())) {
                        removing.add(entity);
                    }
                }

                for (var entity : removing) {
                    if (entity instanceof PlayerEntity pl) {
                        if (chatLog.get()) {
                            logSub(
                                    "Entity",
                                    logDisappearFormat
                                            .get()
                                            .formatText(
                                                    "Player",
                                                    pl.getDisplayName(),
                                                    ChatUtils.getDisplayedLocation(
                                                            entity.getX(), entity.getY(), entity.getZ()),
                                                    formatDistance(entity.getX(), entity.getY(), entity.getZ())));
                        }
                        onPlayerDisappear(pl);
                    } else {
                        if (chatLog.get()) {
                            logSub(
                                    "Entity",
                                    logDisappearFormat
                                            .get()
                                            .formatText(
                                                    "Entity",
                                                    createEntityDisplayName(entity),
                                                    ChatUtils.getDisplayedLocation(
                                                            entity.getX(), entity.getY(), entity.getZ()),
                                                    formatDistance(entity.getX(), entity.getY(), entity.getZ())));
                        }
                    }
                }
            }
        }
    }

    public void onPlayerAppear(UUID playerUUID) {
        offLinePos.remove(playerUUID);
    }

    public void onPlayerDisappear(PlayerEntity player) {
        UUID playerUUID = player.getUuid();
        if (player.isDead() || player.getHealth() <= 1E-6) {
            offLinePos.remove(playerUUID);
            return;
        }
        ChunkPos playerLeaveChunk = player.getChunkPos();
        Entry entry = new Entry(
                playerUUID,
                player.getBoundingBox(),
                player.getPos(),
                playerLeaveChunk,
                player.getPose(),
                player.getDisplayName(),
                player.getNameForScoreboard(),
                mc.world.getRegistryKey(),
                0);
        if (mc.player.getPos().subtract(player.getPos()).horizontalLengthSquared() > MathUtils.s2(48)) {
            entry.exitCode = 1;
        } else {
            entry.exitCode = 2;
            Tasks.scheduleRepeated(
                    () -> {
                        if (checkNull()) return true;
                        var pe = mc.getNetworkHandler().getPlayerListEntry(playerUUID);
                        if (pe == null) {
                            entry.exitCode = 0;
                            if (logLog.get()) {
                                logSub(
                                        "Entity",
                                        logPlayerLogoutFormat
                                                .get()
                                                .formatText(
                                                        entry.displayName,
                                                        "logout",
                                                        ChatUtils.getDisplayedLocation(
                                                                entry.leavePos.x, entry.leavePos.y, entry.leavePos.z),
                                                        createTracking(entry.scoreboardName)));
                            }
                            return true;
                        } else if (pe.getGameMode() == GameMode.SPECTATOR) {
                            entry.exitCode = 0;
                            if (logLog.get()) {
                                logSub(
                                        "Entity",
                                        logPlayerLogoutFormat
                                                .get()
                                                .formatText(
                                                        entry.displayName,
                                                        "got kicked",
                                                        ChatUtils.getDisplayedLocation(
                                                                entry.leavePos.x, entry.leavePos.y, entry.leavePos.z),
                                                        createTracking(entry.scoreboardName)));
                            }
                            handleJoinServer(VRecord.getId(pe.getProfile()));
                            return true;
                        } else {
                            return false;
                        }
                    },
                    1,
                    1,
                    20);
        }
        offLinePos.put(playerUUID, entry);
    }

    public void onServerExit(Event<Void> eventLeave) {
        offLinePos.clear();
    }

    static final String[] LEAVE_REASON = {"Log", "Faraway", "Teleport", "Queuing", "ReLogin"};

    RenderCollector<Box> boxing = RenderCollectors.createBoxCollector(false, true, false);
    RenderCollector<Box> boxingFrame = RenderCollectors.createBoxCollector(true, false, false);
    RenderCollector<Vec3d> tracing = RenderCollectors.createTracerCollector();
    RenderCollector<RenderElements.Text> texting = RenderCollectors.createTextCollector();

    public void onUpdate(Event<Void> eventUpdate) {
        boxing.clear();
        tracing.clear();
        texting.clear();
        if (checkNull()) return;
        if (enable.get() && renderLogPosition.get()) {
            var worldKey = mc.world.getRegistryKey();
            var color = this.color.get();
            for (var re : offLinePos.values()) {
                switch (re.exitCode) {
                    case 0, 3 -> {
                        if (!renderLog.get()) continue;
                    }
                    case 1 -> {
                        if (!renderFaraway.get()) continue;
                    }
                    case 2 -> {
                        if (!renderTp.get()) continue;
                    }
                }
                if (!Objects.equals(re.leaveWorld, worldKey)) {
                    continue;
                }
                ChunkPos leaveChunk = re.leaveChunk;
                if (mc.world.getChunkManager().isChunkLoaded(leaveChunk.x, leaveChunk.z)) {
                    Box box = re.leaveBox;
                    TracingOption op = option.get();
                    if (op.line()) {
                        tracing.submit(box.getCenter(), color.withAlpha(255));
                    }
                    if (op.box()) {
                        boxing.submit(box, color.withAlpha(64));
                        boxingFrame.submit(box, color.withAlpha(255));
                    }
                    var builder = ChatUtils.builder();
                    builder.withText(re.displayName, Style.EMPTY.withBold(true));
                    builder.withColorString("&l %s at ".formatted(LEAVE_REASON[re.exitCode]));
                    builder.withText(ChatUtils.getDisplayedLocation(re.leavePos), Style.EMPTY.withBold(true));
                    builder.end();
                    texting.submit(
                            new RenderElements.Text(builder.build(), re.leavePos.add(0, 2, 0), 0.66F),
                            color.withAlpha(255));
                }
            }
        }
    }

    public void onRender(Event<MatrixStack> eventMatrixStack) {
        if (enable.get() && renderLogPosition.get()) {
            RenderUtils.startDrawVirtual(eventMatrixStack.context);
            try {
                boxing.render3D(eventMatrixStack.context);
                tracing.render3D(eventMatrixStack.context);
            } finally {
                RenderUtils.stopDrawVirtual(eventMatrixStack.context);
            }
        }
    }

    public void onRender2D(Event<VDrawContext> eventVDraw) {
        if (enable.get() && renderLogPosition.get()) {
            texting.render2D(eventVDraw.context);
        }
    }

    public void onPlayerListEntryAdd(Event<PlayerListEntry> event) {
        UUID uid = VRecord.getId(event.context.getProfile());
        GameMode gameMode = event.context.getGameMode();
        if (gameMode != GameMode.SPECTATOR) {
            Tasks.scheduleDelayed(
                    () -> {
                        if (checkNull()) return;
                        var entry = mc.getNetworkHandler().getPlayerListEntry(uid);
                        if (entry == null) {
                            return;
                        } else if (entry.getGameMode() == GameMode.SPECTATOR) {
                            handleJoinServer(uid);
                        } else {
                            handleReLogin(uid);
                        }
                    },
                    5);
        }
    }

    public void onPlayerListEntryModify(Event<PlayerListEntry> event) {
        if (event.getArgs(0) == PlayerListS2CPacket.Action.UPDATE_GAME_MODE) {
            UUID uid = VRecord.getId(event.context.getProfile());
            GameMode gameMode = event.context.getGameMode();
            if (gameMode != GameMode.SPECTATOR) {
                handleReLogin(uid);
            } else {
                handleKickToQueue(uid);
            }
        }
    }

    public void onPlayerListEntryRemove(Event<PlayerListEntry> event) {
        UUID uid = VRecord.getId(event.context.getProfile());
        handleExit(uid);
    }

    public void handleKickToQueue(UUID uid) {}

    public void handleJoinServer(UUID uuid) {
        var entry = offLinePos.get(uuid);
        if (entry != null && entry.exitCode == 0) {
            entry.exitCode = 3;
            if (logLogReconnect.get()) {
                int count = (int) mc.getNetworkHandler().getPlayerList().stream()
                        .filter(s -> s.getGameMode() == GameMode.SPECTATOR)
                        .count();
                logSub(
                        "Entity",
                        logPlayerJoinQueueFormat
                                .get()
                                .formatText(
                                        entry.displayName,
                                        ChatUtils.getDisplayedLocation(
                                                entry.leavePos.x, entry.leavePos.y, entry.leavePos.z),
                                        count,
                                        createTracking(entry.scoreboardName)));
            }
        }
    }

    private Text createTracking(String name) {
        return ChatUtils.stringToText("&a&l[&aTrack&a&l]").styled(s -> s.withClickEvent(
                        ChatUtils.getSuggestCommand(MainCommand.getMainCommandPrefix() + "pqueue add " + name))
                .withHoverEvent(ChatUtils.getHoverShowText(List.of(Text.literal("Click to track player in queue")))));
    }

    public void handleReLogin(UUID uuid) {
        var entry = offLinePos.remove(uuid);
        if (entry != null && (entry.exitCode == 0 || entry.exitCode == 3)) {
            entry.exitCode = 4;
            if (logLogReconnect.get()) {
                logSub(
                        "Entity",
                        logPlayerReloginFormat
                                .get()
                                .formatText(
                                        entry.displayName,
                                        ChatUtils.getDisplayedLocation(
                                                entry.leavePos.x, entry.leavePos.y, entry.leavePos.z)));
            }
        }
    }

    public void handleExit(UUID uuid) {
        var entry = offLinePos.get(uuid);
        if (entry != null) {
            if (entry.exitCode == 3) {
                entry.exitCode = 0;
            }
        }
    }

    public boolean loginServerCheck() {
        return mc.world.getWorldBorder().getSize() < 100;
    }

    private Text createEntityDisplayName(Entity entity) {
        var text = Text.empty().append(entity.getType().getName().copy());
        if (entity.hasCustomName() && entity.getCustomName() != null) {
            text.append(Text.literal(" ")).append(entity.getCustomName().copy());
        }
        return text;
    }

    @AllArgsConstructor
    public static class Entry {
        UUID uuid;
        Box leaveBox;
        Vec3d leavePos;
        ChunkPos leaveChunk;
        EntityPose leavePose;
        Text displayName;
        String scoreboardName;
        RegistryKey<World> leaveWorld;
        int exitCode;
    }
}
