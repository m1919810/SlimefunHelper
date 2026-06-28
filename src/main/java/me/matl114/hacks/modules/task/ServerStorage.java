package me.matl114.hacks.modules.task;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Getter;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannel;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.PrimitivePairList;
import me.matl114.hacks.utils.world.BlockStorage;
import me.matl114.hacks.utils.world.ChunkStorage;
import me.matl114.hacks.utils.world.IStorage;
import me.matl114.hacks.utils.world.WorldStorage;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.ScheduleService;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.Debug;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class ServerStorage extends BaseModule {
    public static ServerStorage INSTANCE;

    public ServerStorage() {
        INSTANCE = this;
    }

    public ModulePath path = makePath(Configs.MISC_CONFIG, "world-storage");
    public final FlagRef enable = builder(path.add("enable-persistent-storage"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<PrimitivePairList<String, String>> serverNameMapper = builder(
                    path.add("persistent-storage-name-mapper"),
                    NBTType.<PrimitivePairList<String, String>>parameter(PrimitivePairList.class))
            .defaultValue(new PrimitivePairList<>(
                    NBTTypes.STRING_TYPE, NBTTypes.STRING_TYPE, List.of(Pair.of("3c3u.org", "3c3u"))))
            .build();

    public static final File SAVE_FILE = FileManager.getInstance().getAndCreateFile("server_storage");

    static String currentServerName;
    static Meta serverStorage;
    static Map<BlockPos, BlockStorage> snapshotMap1;
    static Map<ChunkPos, ChunkStorage> snapshotMap2;

    public static BlockStorage getBlockStorage(BlockPos pos) {
        return getBlockStorage(pos, (Function<BlockPos, BlockStorage>) null);
    }

    public static BlockStorage getBlockStorage(BlockPos pos, Supplier<BlockStorage> supplier) {
        return getBlockStorage(pos, supplier == null ? null : (v) -> supplier.get());
    }

    public static BlockStorage getBlockStorage(BlockPos pos, Function<BlockPos, BlockStorage> supplier) {
        if (serverStorage == null) {
            return null;
        }
        var cacheMap = snapshotMap1;
        if (cacheMap == null && mc.world != null) {
            processAsyncUpdateMapSnapshot(mc.world.getRegistryKey());
        }
        if (cacheMap != null) {
            return _getFromSSSSMap(pos, supplier, cacheMap);
        } else {
            var blockMap = serverStorage.blockStorageMap.computeIfAbsent(
                    mc.world.getRegistryKey(), k -> new ConcurrentHashMap<>());
            return _getFromSSSSMap(pos, supplier, blockMap);
        }
    }

    public static void setBlockStorage(BlockPos pos, BlockStorage blockStorage) {
        if (serverStorage == null) return;
        var cacheMap = snapshotMap1;
        if (cacheMap == null && mc.world != null) {
            processAsyncUpdateMapSnapshot(mc.world.getRegistryKey());
        }
        if (cacheMap != null) {
            _putToSSSSMap(pos, blockStorage, cacheMap);
        } else {
            var blockMap = serverStorage.blockStorageMap.computeIfAbsent(
                    mc.world.getRegistryKey(), k -> new ConcurrentHashMap<>());
            _putToSSSSMap(pos, blockStorage, blockMap);
        }
    }

    public static ChunkStorage getChunkStorage(ChunkPos pos, Supplier<ChunkStorage> supplier) {
        return getChunkStorage(pos, (v) -> supplier.get());
    }

    public static ChunkStorage getChunkStorage(ChunkPos pos, Function<ChunkPos, ChunkStorage> supplier) {
        if (serverStorage == null) {
            return null;
        }
        var cacheMap = snapshotMap2;
        if (cacheMap == null && mc.world != null) {
            processAsyncUpdateMapSnapshot(mc.world.getRegistryKey());
        }
        if (cacheMap != null) {
            return _getFromSSSSMap(pos, supplier, cacheMap);
        } else {
            var blockMap = serverStorage.chunkStorageMap.computeIfAbsent(
                    mc.world.getRegistryKey(), k -> new ConcurrentHashMap<>());
            return _getFromSSSSMap(pos, supplier, blockMap);
        }
    }

    public static void setChunkStorage(ChunkPos pos, ChunkStorage blockStorage) {
        if (serverStorage == null) return;
        var cacheMap = snapshotMap2;
        if (cacheMap == null && mc.world != null) {
            processAsyncUpdateMapSnapshot(mc.world.getRegistryKey());
        }
        if (cacheMap != null) {
            _putToSSSSMap(pos, blockStorage, cacheMap);
        } else {
            var blockMap = serverStorage.chunkStorageMap.computeIfAbsent(
                    mc.world.getRegistryKey(), k -> new ConcurrentHashMap<>());
            _putToSSSSMap(pos, blockStorage, blockMap);
        }
    }

    private static <W, T> T _getFromSSSSMap(W key, Function<W, T> supplier, Map<W, T> mmm) {
        var block = mmm.get(key);
        if (block != null) {
            return block;
        } else {
            if (supplier == null) {
                return null;
            }
            block = supplier.apply(key);
            mmm.put(key, block);
            serverStorage.dirty = true;
            return block;
        }
    }

    public static WorldStorage getWorldStorage(RegistryKey<World> key, Supplier<WorldStorage> supplier) {
        return serverStorage.worldStorageMap.computeIfAbsent(key, s -> supplier.get());
    }

    @Getter
    @Broadcast
    private static final EventChannel<Meta> serverStorageLoad = new EventChannel<>();

    @Getter
    @Broadcast
    private static final EventChannel<Meta> serverStorageSave = new EventChannel<>();

    private static <W, T> void _putToSSSSMap(W key, T val, Map<W, T> mmm) {
        if (val != null) {
            mmm.put(key, val);
            serverStorage.dirty = true;
        } else {
            if (mmm.remove(key) != null) {
                serverStorage.dirty = true;
            }
        }
    }

    public static void update(BlockStorage storage, boolean autoRemoval) {
        if (serverStorage != null) {
            if (storage.dirty) {
                serverStorage.dirty = true;
            }
            if (autoRemoval && storage.isEmpty()) {
                setBlockStorage(storage.getPos(), null);
                serverStorage.dirty = true;
            }
        }
    }

    public static String getCurrentServerName() {
        return INSTANCE.mappedServerName();
    }

    private String mappedServerName() {
        String serverName = CommonUtils.getServerName();
        Preconditions.checkNotNull(serverName);
        String replace = serverName;
        for (var re : serverNameMapper.get().list()) {
            if (serverName.equalsIgnoreCase(re.getFirst())) {
                replace = re.getSecond();
            }
        }
        return replace;
    }

    public static String PREFIX = "ws_";

    private String normalizedFileName(String path) {
        path = path.trim().replace(" ", "_").replace("/", "_").replace("\\", "_");
        return PREFIX + path;
    }

    public boolean updateServerName() {
        String currentServerName = mappedServerName();
        if (!Objects.equals(ServerStorage.currentServerName, currentServerName)) {
            ServerStorage.currentServerName = currentServerName;
            return true;
        }
        return serverStorage == null;
    }

    public void onLoadStorage() {
        String serverName = ServerStorage.currentServerName;
        serverStorage = new Meta(serverName);
        Meta loadingStorage = serverStorage;

        if (enable.get()) {
            // load persistent data
            CompletableFuture.runAsync(() -> {
                        String normalized = normalizedFileName(serverName);
                        File folder = new File(SAVE_FILE, normalized);
                        File file = new File(folder, "meta.nbt");
                        FileManager.getInstance().checkFile(folder);
                        synchronized (loadingStorage) {
                            try (FileStorage storage = FileManager.getInstance().getStorage(file)) {
                                var serverMeta = storage.read(Meta.CODEC);
                                Meta mt;
                                if (serverMeta.isSuccess()
                                        && (mt = serverMeta.getOrThrow()).serverName.equals(serverName)) {
                                    if (mt.version < Meta.DATA_VERSION) {
                                        processUpdate(mt, storage);
                                    }
                                    loadingStorage.load(mt.toBlockList(), mt.toChunkList(), mt.toWorldList());
                                } else {
                                    // writing default
                                    storage.write(Meta.CODEC, loadingStorage);
                                }
                            } catch (Throwable e) {
                                Debug.info("Error while loading server storage:");
                                Debug.info(e);
                            }
                        }
                    })
                    .thenRunAsync(
                            () -> {
                                serverStorageLoad.broadcast(loadingStorage);
                            },
                            mc);
        } else {
            serverStorageLoad.broadcast(loadingStorage);
        }
        // clear old snapshot
        processAsyncUpdateMapSnapshot(null);
    }

    public static void processAsyncUpdateMapSnapshot(RegistryKey<World> world) {
        if (serverStorage != null) {
            Meta storage = serverStorage;
            CompletableFuture.runAsync(() -> {
                synchronized (storage) {
                    snapshotMap1 = storage.blockStorageMap.computeIfAbsent(world, k -> new ConcurrentHashMap<>());
                    snapshotMap2 = storage.chunkStorageMap.computeIfAbsent(world, k -> new ConcurrentHashMap<>());
                }
            });

        } else {
            snapshotMap1 = null;
            snapshotMap2 = null;
        }
    }

    String saveTask;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getGameJoinPoint(), this::onGameJoin, Integer.MAX_VALUE);
        registerListener(Listener.getWorldSwitchPoint(), this::onGameSwitchWorld, Integer.MIN_VALUE);
        registerListener(Listener.getServerLeavePoint(), this::onGameLeave, Integer.MIN_VALUE);
        saveTask = ScheduleService.launchAsyncRepeatTask(this::onSave, 15 * 1000, 15 * 1000);
    }

    @Override
    public <W> void unregisterAll() {
        super.unregisterAll();
        if (saveTask != null) {
            ScheduleService.stopAsyncTask(saveTask);
        }
    }

    public void onGameJoin(Event<ClientPlayerEntity> eventPlayerEntity) {
        if (updateServerName()) {
            onLoadStorage();
        }
    }

    public void onGameSwitchWorld(Event<World> event) {
        processAsyncUpdateMapSnapshot(null);
    }

    public void onGameLeave(Event<Void> eventVoid) {
        processAsyncUpdateMapSnapshot(null);
        CompletableFuture.runAsync(this::onSave);
    }

    private void onSave() {
        if (currentServerName != null && serverStorage != null) {
            Meta currentSaveStorage = serverStorage;
            String serverName = currentServerName;
            serverStorageSave.broadcast(currentSaveStorage);
            onSave(serverName, currentSaveStorage);
        }
    }

    public void onSave(String serverName, Meta currentSaveStorage) {
        if (enable.get()) {
            synchronized (currentSaveStorage) {
                if (currentSaveStorage.isDirty()) {
                    String normalized = normalizedFileName(serverName);
                    File folder = new File(SAVE_FILE, normalized);
                    File file = new File(folder, "meta.nbt");
                    FileManager.getInstance().checkFile(file);
                    try (FileStorage storage = FileManager.getInstance().getStorage(file)) {
                        storage.write(Meta.CODEC, currentSaveStorage);
                    } finally {
                        currentSaveStorage.onSave();
                    }
                }
            }
        }
    }

    public void processUpdate(Meta meta, FileStorage oldStorage) {}

    public static class Meta {
        public static final int DATA_VERSION = 0;
        // todo: may optimize to seperate storage.
        public static final Codec<Meta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("server-name").forGetter(Meta::getServerName),
                        Codec.INT.fieldOf("data-version").forGetter((meta) -> meta.version),
                        Codec.list(BlockStorage.CODEC)
                                .optionalFieldOf("block-storage", List.of())
                                .forGetter(Meta::toBlockList),
                        Codec.list(ChunkStorage.CODEC)
                                .optionalFieldOf("chunk-storage", List.of())
                                .forGetter(Meta::toChunkList),
                        Codec.list(WorldStorage.CODEC)
                                .optionalFieldOf("world-storage", List.of())
                                .forGetter(Meta::toWorldList))
                .apply(instance, Meta::new));

        public final Map<RegistryKey<World>, Map<BlockPos, BlockStorage>> blockStorageMap;
        public final Map<RegistryKey<World>, Map<ChunkPos, ChunkStorage>> chunkStorageMap;
        public final Map<RegistryKey<World>, WorldStorage> worldStorageMap;
        public final int version;

        @Getter
        public final String serverName;

        boolean dirty = false;

        public Meta(String serverName) {
            this(serverName, DATA_VERSION);
        }

        public Meta(String serverName, int version) {
            this.serverName = serverName;
            this.version = version;
            this.blockStorageMap = new ConcurrentHashMap<>();
            this.chunkStorageMap = new ConcurrentHashMap<>();
            this.worldStorageMap = new ConcurrentHashMap<>();
        }

        public Meta(
                String serverName,
                int version,
                List<BlockStorage> blockStorageList,
                List<ChunkStorage> chunkStorage,
                List<WorldStorage> worldStorageList) {
            this(serverName, version);
            load(blockStorageList, chunkStorage, worldStorageList);
        }

        public void load(
                List<BlockStorage> blockStorageList,
                List<ChunkStorage> chunkStorage,
                List<WorldStorage> worldStorageList) {
            blockStorageMap.clear();
            chunkStorageMap.clear();
            worldStorageMap.clear();
            for (var re : blockStorageList) {
                this.blockStorageMap
                        .computeIfAbsent(re.getDimension(), k -> new ConcurrentHashMap<>())
                        .put(re.getPos(), re);
            }
            for (var re : chunkStorage) {
                this.chunkStorageMap
                        .computeIfAbsent(re.getDimension(), k -> new ConcurrentHashMap<>())
                        .put(re.getChunkPos(), re);
            }
            for (var re : worldStorageList) {
                this.worldStorageMap.put(re.getDimension(), re);
            }
            dirty = false;
        }

        public List<BlockStorage> toBlockList() {
            return blockStorageMap.values().stream()
                    .flatMap(s -> s.values().stream())
                    .filter(BlockStorage::nonEmpty)
                    .toList();
        }

        public List<ChunkStorage> toChunkList() {
            return chunkStorageMap.values().stream()
                    .flatMap(s -> s.values().stream())
                    .filter(ChunkStorage::nonEmpty)
                    .toList();
        }

        public List<WorldStorage> toWorldList() {
            return worldStorageMap.values().stream()
                    .filter(WorldStorage::nonEmpty)
                    .toList();
        }

        public boolean isDirty() {
            return dirty
                    || Streams.concat(
                                    blockStorageMap.values().stream().flatMap(s -> s.values().stream()),
                                    chunkStorageMap.values().stream().flatMap(s -> s.values().stream()),
                                    worldStorageMap.values().stream())
                            .anyMatch(IStorage::isDirty);
        }

        public void onSave() {
            dirty = false;
            Streams.concat(
                            blockStorageMap.values().stream().flatMap(s -> s.values().stream()),
                            chunkStorageMap.values().stream().flatMap(s -> s.values().stream()),
                            worldStorageMap.values().stream())
                    .forEach(s -> s.setDirty(false));
        }
    }
}
