package me.matl114.hacks.modules.survival;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannel;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.chat.InGuiChatBox;
import me.matl114.hacks.modules.move.TravellingControl;
import me.matl114.hacks.utils.config.*;
import me.matl114.hooks.XaeroHooks;
import me.matl114.hooks.impl.xaeroplus.IMapDrawFeature;
import me.matl114.hooks.impl.xaeroplus.wrapper.LineWrapper;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypoint;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointAccess;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointFactory;
import me.matl114.hooks.impl.xaeroworldmap.MapClickContext;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class XaeroHelper extends BaseModule {
    public static XaeroHelper INSTANCE;

    @Getter
    @Broadcast
    private static final EventChannel<World> worldSwitchPoint = new EventChannel<>();

    public XaeroHelper() {
        super("XaeroHelper");
        INSTANCE = this;
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "xaero-map-extra.xaero-helper");

    public final FlagRef loadedChunkRender = flagBuilder(root.add("loaded-chunk-render"))
            .updateListener(this::toggleLoadedChunk)
            .build();

    public final NBTRef<WrapColor> loadedChunkColor = builder(root.add("loaded-chunk-render-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.RED)))
            .build();

    public final FlagRef xplusBaritonePathFix =
            flagBuilder(root.add("xplus-baritone-elytra-path-fix")).build();

    public final FlagRef xaeroCommandInsert =
            flagBuilder(root.add("enable-xaero-right-click-command")).build();
    private static final List<String> LIST_FORMATS = List.of("world", "pos", "pos_str", "x", "y", "z");
    public final NBTRef<PrimitiveList<StringFormat>> xaeroRightClickCommand = builder(
                    root.add("xaero-right-click-command-list"), PrimitiveList.type(StringFormat.class))
            .defaultValue(new PrimitiveList<>(
                    NBTTypes.STRING_FORMAT_TYPE,
                    List.of(
                            new StringFormat(LIST_FORMATS, "/tp {pos}"),
                            new StringFormat(LIST_FORMATS, "/!!travel to {pos}")),
                    new StringFormat(LIST_FORMATS, "")))
            .build();

    public final NBTRef<PrimitiveList<StringFormat>> xaeroRightClickSuggest = builder(
                    root.add("xaero-right-click-suggest-list"), PrimitiveList.type(StringFormat.class))
            .defaultValue(
                    new PrimitiveList<>(NBTTypes.STRING_FORMAT_TYPE, List.of(), new StringFormat(LIST_FORMATS, "")))
            .build();

    public final FlagRef travelGoalSync =
            flagBuilder(root.add("travel-goal-sync")).build();

    public final FlagRef transparentGuiMapFix =
            flagBuilder(root.add("transparent-gui-map-fix")).build();

    public final FlagRef addChatInGuiMap =
            flagBuilder(root.add("add-chat-input-in-gui-map")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch, Integer.MIN_VALUE);
        registerListener(Listener.getPostGameTick(), this::onTickMapRender);
        registerListener(XaeroHooks.getWorldMapRightClickOption(), this::onXaeroWorldMapClick);
        registerListener(Listener.getPostGameTick(), this::onXaeroTempWaypointSync);
        registerListener(Listener.getPostInitializeScreen(), this::onGuiSetup);
    }

    @Override
    public <W> void unregisterAll() {
        super.unregisterAll();
        toggleLoadedChunk(false);
        clearWaypoints();
    }

    public static final String LOADED_CHUNK_RENDER_ID = "slimefun_xaerohelper_loaded_chunk_render";
    public IMapDrawFeature loadedChunkFeature;

    public void toggleLoadedChunk(boolean bl) {
        if (XaeroHooks.getInstance().isXaeroPlusEnable()) {
            if (bl) {
                loadedChunkFeature = XaeroHooks.getInstance()
                        .getMapDrawFactory()
                        .lines(
                                LOADED_CHUNK_RENDER_ID,
                                this::supplyLoadedChunkLines,
                                () -> this.loadedChunkColor.get().withAlpha(255),
                                () -> 0.1F,
                                100);
                loadedChunkFeature.register();
            } else {
                if (loadedChunkFeature != null) {
                    loadedChunkFeature.unregister();
                    loadedChunkFeature = null;
                } else {
                    XaeroHooks.getInstance().getMapDrawFactory().unregisterId(LOADED_CHUNK_RENDER_ID);
                }
            }
        }
    }

    final List<LineWrapper<?>> loadedChunkLines = new ArrayList<>();

    public List<LineWrapper<?>> supplyLoadedChunkLines(int x, int y, int w, RegistryKey<World> dimension) {
        if (mc.world != null && Objects.equals(mc.world.getRegistryKey(), dimension)) {
            return loadedChunkLines;
        } else {
            return List.of();
        }
    }

    Set<ChunkPos> lastLoadedChunks = new HashSet<>();
    public static final int[] dx = {0, -1, 0, 1};
    public static final int[] dz = {1, 0, -1, 0};

    public void updateLoadedChunks(Set<ChunkPos> chunkPos) {
        if (!Objects.equals(chunkPos, lastLoadedChunks)) {
            lastLoadedChunks = chunkPos;
            LongSet longs = new LongOpenHashSet(chunkPos.size());
            for (var re : chunkPos) {
                for (var i = 0; i < 4; ++i) {
                    long lv = packEdge(re.x, re.z, dx[i], dz[i]);
                    if (longs.contains(lv)) {
                        longs.remove(lv);
                    } else {
                        longs.add(lv);
                    }
                }
            }
            loadedChunkLines.clear();
            longs.longStream().mapToObj(this::unpackEdge).forEach(loadedChunkLines::add);
        }
    }

    private long packEdge(int chunkX, int chunkZ, int dx, int dz) {
        int packChunkX = 2 * chunkX + dx;
        int packChunkZ = 2 * chunkZ + dz;
        return MathUtils.packInt(packChunkX, packChunkZ);
    }

    public LineWrapper<?> unpackEdge(long offset) {
        int unpackChunk2X = MathUtils.unpackFirst(offset);
        int unpackChunk2Z = MathUtils.unpackSecond(offset);
        int chunkX = unpackChunk2X >> 1;
        int chunkZ = unpackChunk2Z >> 1;
        int nextChunkX = unpackChunk2X - chunkX;
        int nextChunkZ = unpackChunk2Z - chunkZ;
        if (chunkX == nextChunkX) {
            int lowZ = Math.min(nextChunkZ, chunkZ);
            return new LineWrapper<>(chunkX << 4, (lowZ + 1) << 4, (chunkX + 1) << 4, (lowZ + 1) << 4);
        } else {
            int lowX = Math.min(nextChunkX, chunkX);
            return new LineWrapper<>((lowX + 1) << 4, chunkZ << 4, (lowX + 1) << 4, (chunkZ + 1) << 4);
        }
    }

    public void onTickMapRender(Event<ClientPlayerEntity> event) {
        if (loadedChunkRender.get() && loadedChunkFeature != null) {
            Set<ChunkPos> chunkPoses = new HashSet<>(100);
            for (var chunk : CommonUtils.chunks(false)) {
                chunkPoses.add(chunk.getPos());
            }
            updateLoadedChunks(chunkPoses);
        }
    }

    private static final Map<String, Object> formatMap = ImmutableMap.<String, Object>builder()
            .put("pos", Text.translatable("message.module.xaero-helper.right-click-command.pos"))
            .put("pos_str", Text.translatable("message.module.xaero-helper.right-click-command.pos_str"))
            .put("x", Text.translatable("message.module.xaero-helper.right-click-command.pos_x"))
            .put("y", Text.translatable("message.module.xaero-helper.right-click-command.pos_y"))
            .put("z", Text.translatable("message.module.xaero-helper.right-click-command.pos_z"))
            .build();

    public void onXaeroWorldMapClick(Event<ArrayList<MapClickContext>> event) {
        if (xaeroCommandInsert.get()) {
            RegistryKey<World> worldKey = event.getArgs(0);
            BlockPos pos = event.getArgs(1);
            Map<String, String> map = ImmutableMap.<String, String>builder()
                    .put("world", worldKey.getValue().getPath())
                    .put("pos", "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ()))
                    .put("pos_str", "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ()))
                    .put("x", String.valueOf(pos.getX()))
                    .put("y", String.valueOf(pos.getY()))
                    .put("z", String.valueOf(pos.getZ()))
                    .build();
            for (var format : xaeroRightClickCommand.get().list()) {
                String name = ChatUtils.textToPlainString(Text.translatable(
                        "message.module.xaero-helper.right-click-command.command", format.formatText(formatMap)));
                event.context.add(new MapClickContext(name, (world, position) -> {
                    String formatted = format.format(map);
                    ChatTasks.sayMessage(formatted, false);
                }));
            }
            for (var format : xaeroRightClickSuggest.get().list()) {
                String name = ChatUtils.textToPlainString(Text.translatable(
                        "message.module.xaero-helper.right-click-command.suggest", format.formatText(formatMap)));
                event.context.add(new MapClickContext(name, (world, position) -> {
                    String formatted = format.format(map);
                    ScreenUtils.openChatScreen(formatted);
                }));
            }
        }
    }

    public record WaypointBinding(WeakReference<Object> owner, IXWaypoint waypoint) {}

    private IXWaypointAccess access;
    private final List<WaypointBinding> submittedWaypoints = new ArrayList<>();
    IXWaypoint travelPoint;

    private void clearWaypoints() {
        detachWaypoints();
        submittedWaypoints.clear();
        travelPoint = null;
    }

    private void detachWaypoints() {
        if (access != null && !submittedWaypoints.isEmpty()) {
            access.removeAll(
                    submittedWaypoints.stream().map(WaypointBinding::waypoint).toList());
            access.requestRefresh();
        }
        access = null;
    }

    private void onWorldSwitch(Event<World> event) {
        clearWaypoints();
        worldSwitchPoint.broadcast(event.context);
    }

    private void attachWaypoints() {
        if (access != null && !submittedWaypoints.isEmpty()) {
            access.addAll(
                    submittedWaypoints.stream().map(WaypointBinding::waypoint).toList());
            access.requestRefresh();
        }
    }

    private void cleanupCollectedWaypoints() {
        if (submittedWaypoints.isEmpty()) {
            return;
        }
        List<IXWaypoint> removed = new ArrayList<>();
        submittedWaypoints.removeIf(binding -> {
            if (binding.owner().get() == null) {
                removed.add(binding.waypoint());
                return true;
            }
            return false;
        });
        if (access != null && !removed.isEmpty()) {
            access.removeAll(removed);
            access.requestRefresh();
        }
    }

    private void syncWaypointAccess() {
        IXWaypointFactory factory = XaeroHooks.getInstance().getWaypointFactory();
        RegistryKey<World> currentWorld = factory == null ? null : factory.getCurrentWorld();
        IXWaypointAccess currentAccess = factory == null || !Objects.equals(currentWorld, mc.world.getRegistryKey())
                ? null
                : factory.getCurrentWaypointSet();
        if (!Objects.equals(currentAccess, access)) {
            detachWaypoints();
            access = currentAccess;
            attachWaypoints();
        }
    }

    public IXWaypoint createWaypoint(
            int x, int y, int z, String name, String initials, int color, int type, boolean temp, boolean yIncluded) {
        IXWaypointFactory factory = XaeroHooks.getInstance().getWaypointFactory();
        if (factory == null) {
            return null;
        }
        return factory.createWaypoint(x, y, z, name, initials, color, type, temp, yIncluded);
    }

    public IXWaypoint submitWaypoint(
            Object owner,
            int x,
            int y,
            int z,
            String name,
            String initials,
            int color,
            int type,
            boolean temp,
            boolean yIncluded) {
        IXWaypoint waypoint = createWaypoint(x, y, z, name, initials, color, type, temp, yIncluded);
        return waypoint == null ? null : submitWaypoint(owner, waypoint);
    }

    public IXWaypoint submitWaypoint(Object owner, IXWaypoint waypoint) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(waypoint, "waypoint");
        cleanupCollectedWaypoints();
        if (submittedWaypoints.stream().anyMatch(binding -> Objects.equals(binding.waypoint(), waypoint))) {
            return waypoint;
        }
        submittedWaypoints.add(new WaypointBinding(new WeakReference<>(owner), waypoint));
        if (access != null) {
            access.add(waypoint);
            access.requestRefresh();
        }
        return waypoint;
    }

    public void updateWaypoint(IXWaypoint waypoint, Consumer<IXWaypoint> updater) {
        cleanupCollectedWaypoints();
        if (submittedWaypoints.stream().noneMatch(binding -> Objects.equals(binding.waypoint(), waypoint))) {
            return;
        }
        if (access == null) {
            updater.accept(waypoint);
        } else {
            access.update(waypoint, updater);
            access.requestRefresh();
        }
    }

    public void removeWaypoint(IXWaypoint waypoint) {
        if (waypoint == null) {
            return;
        }
        cleanupCollectedWaypoints();
        boolean removed = submittedWaypoints.removeIf(binding -> Objects.equals(binding.waypoint(), waypoint));
        if (access != null && removed) {
            access.remove(waypoint);
            access.requestRefresh();
        }
    }

    public void removeWaypoint(Object owner, BlockPos pos) {
        cleanupCollectedWaypoints();
        double scale = mc.world == null ? 1 : mc.world.getDimension().coordinateScale();
        int x = (int) (pos.getX() * scale);
        int y = pos.getY();
        int z = (int) (pos.getZ() * scale);
        List<IXWaypoint> removed = new ArrayList<>();
        submittedWaypoints.removeIf(binding -> {
            IXWaypoint waypoint = binding.waypoint();
            if (binding.owner().get() == owner
                    && waypoint.getX() == x
                    && waypoint.getY() == y
                    && waypoint.getZ() == z) {
                removed.add(waypoint);
                return true;
            }
            return false;
        });
        if (access != null && !removed.isEmpty()) {
            access.removeAll(removed);
            access.requestRefresh();
        }
    }

    public void removeSub(Object owner) {
        Objects.requireNonNull(owner, "owner");
        List<IXWaypoint> removed = new ArrayList<>();
        submittedWaypoints.removeIf(binding -> {
            if (binding.owner().get() == owner) {
                removed.add(binding.waypoint());
                return true;
            }
            return false;
        });
        if (access != null && !removed.isEmpty()) {
            access.removeAll(removed);
            access.requestRefresh();
        }
    }

    public void onXaeroTempWaypointSync(Event<ClientPlayerEntity> eventVoid) {
        if (checkNull()) return;
        cleanupCollectedWaypoints();
        if (!XaeroHooks.getInstance().isXaeroMiniMapEnable()) {
            return;
        }
        syncWaypointAccess();
        if (travelGoalSync.get()) {
            if (TravellingControl.travelTask == null) {
                removeWaypoint(travelPoint);
                travelPoint = null;
            } else {
                Vec3d target = TravellingControl.travelTask.getCurrentFlyingTarget();
                BlockPos pos = new BlockPos((int) target.x, (int) Math.clamp(target.y, -512, 512), (int) target.z);
                if (travelPoint == null) {
                    travelPoint = submitWaypoint(
                            this,
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            "[SFH] Travel",
                            "T",
                            Formatting.GREEN.ordinal(),
                            0,
                            true,
                            true);
                }
                if (travelPoint != null
                        && (travelPoint.getX() != pos.getX()
                                || travelPoint.getY() != pos.getY()
                                || travelPoint.getZ() != pos.getZ())) {
                    updateWaypoint(travelPoint, acc -> {
                        acc.setX(pos.getX());
                        acc.setY(pos.getY());
                        acc.setZ(pos.getZ());
                    });
                }
            }
        } else {
            removeWaypoint(travelPoint);
            travelPoint = null;
        }
    }

    public void onGuiSetup(Event<Screen> screenEvent) {
        if (XaeroHooks.getInstance().isGuiMap(screenEvent.context)
                && addChatInGuiMap.get()
                && !InGuiChatBox.INSTANCE.enableOther.get()) {
            ScreenAccess.of(screenEvent.context).addDrawableChildTo(InGuiChatBox.INSTANCE.createDefaultInputWidget());
        }
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitle(
                XaeroHooks.getInstance().isXaeroWorldMapEnable()
                        ? "widget.xaero-helper.xaero-worldmap-enable"
                        : "widget.xaero-helper.xaero-worldmap-not-support",
                0,
                dblank,
                dx,
                dy));
        acceptor.accept(createTitle(
                XaeroHooks.getInstance().isXaeroMiniMapEnable()
                        ? "widget.xaero-helper.xaero-minimap-enable"
                        : "widget.xaero-helper.xaero-minimap-not-support",
                0,
                dblank,
                dx,
                dy));
        acceptor.accept(createTitle(
                XaeroHooks.getInstance().isXaeroPlusEnable()
                        ? "widget.xaero-helper.xaero-plus-enable"
                        : "widget.xaero-helper.xaero-plus-not-support",
                0,
                dblank,
                dx,
                dy));
    }
}
