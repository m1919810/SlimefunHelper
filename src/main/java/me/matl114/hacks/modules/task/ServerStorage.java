package me.matl114.hacks.modules.task;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.With;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.annotations.ExtraArgs;
import me.matl114.events.channels.EventChannel;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.PrimitivePairList;
import me.matl114.hacks.utils.world.BlockStorage;
import me.matl114.hacks.utils.world.ChunkStorage;
import me.matl114.hacks.utils.world.EntityStorage;
import me.matl114.hacks.utils.world.IStorage;
import me.matl114.hacks.utils.world.WorldStorage;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.ScheduleService;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.CollectionUtils;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.Debug;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class ServerStorage extends BaseModule {
    public static ServerStorage INSTANCE;

    public ServerStorage() {
        super("ServerStorage");
        INSTANCE = this;
    }

    public ModulePath path = makePath(Configs.MISC_CONFIG, "world-storage");
    public final FlagRef enable = builder(path.add("enable-persistent-storage"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableProxyXaeroMap =
            flagBuilder(path.add("enable-proxy-xaeromap-storage")).build();

    public final FlagRef enableProxyBaritone =
            flagBuilder(path.add("enable-proxy-baritone-storage")).build();

    FileStorage fileStorage = FileManager.getInstance().getInternalStorage("server-storage.nbt");

    static final String NAME_MAPPER_KEY = "persistent-storage-name-mapper";
    static final String PROXY_KEY = "ip-proxies";
    ServerFolder serverFolder;

    {
        serverFolder = fileStorage.read(ServerFolder.CODEC, () -> new ServerFolder(Map.of(), Map.of()));
        updateServerFolder(serverFolder);
    }

    public void updateServerFolder(ServerFolder folder) {
        this.serverFolder = folder;
        fileStorage.write(ServerFolder.CODEC, folder);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        var pth = path.add("persistent-storage-name-mapper");
        NBTRef<PrimitivePairList<String, String>> ref = new NBTRef<>(new PrimitivePairList<>(
                "widget.server-storage.ip",
                "widget.server-storage.name",
                NBTTypes.STRING_TYPE,
                NBTTypes.STRING_TYPE,
                CollectionUtils.mapToPairList(serverFolder.ipToFolder())));
        ref.addUpdateListener(s -> {
            updateServerFolder(serverFolder.withIpToFolder(CollectionUtils.pairListToMap(s.list())));
        });
        acceptor.accept(createRefEditor(pth.asString(), ref, 0, dblank, dx, dy));
        var pth2 = path.add("ip-proxies");
        NBTRef<PrimitivePairList<String, String>> ref2 = new NBTRef<>(new PrimitivePairList<>(
                "widget.server-storage.proxy",
                "widget.server-storage.ip",
                NBTTypes.STRING_TYPE,
                NBTTypes.STRING_TYPE,
                CollectionUtils.mapToPairList(serverFolder.ipProxy())));
        ref2.addUpdateListener(
                s -> updateServerFolder(serverFolder.withIpProxy(CollectionUtils.pairListToMap(s.list()))));
        acceptor.accept(createRefEditor(pth2.asString(), ref2, 0, dblank, dx, dy));
    }

    public String getSaveId(String ip) {
        if (enableProxyXaeroMap.get()) {
            return serverFolder.getProxiedIp(ip);
        }
        return ip;
    }

    public static final File SAVE_FILE = FileManager.getInstance().getAndCreateFile("server_storage");

    static String currentServerName;
    static Meta serverStorage;
    static Map<BlockPos, BlockStorage> snapshotMap1;
    static Map<ChunkPos, ChunkStorage> snapshotMap2;

    public static Meta getStorage() {
        return serverStorage;
    }

    public static BlockStorage getBlockStorage(BlockPos pos) {
        return getBlockStorage(pos, (Function<BlockPos, BlockStorage>) null);
    }

    public static BlockStorage getOrCreateBlockStorage(BlockPos pos) {
        return getBlockStorage(pos, () -> new BlockStorage(mc.world.getRegistryKey(), pos));
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

    public static ChunkStorage getOrCreateChunkStorage(ChunkPos pos) {
        return getChunkStorage(pos, () -> new ChunkStorage(mc.world.getRegistryKey(), pos));
    }

    public static ChunkStorage getChunkStorage(ChunkPos pos) {
        return getChunkStorage(pos, (Function<ChunkPos, ChunkStorage>) null);
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
            return block;
        }
    }

    public static WorldStorage getWorldStorage(RegistryKey<World> key, Supplier<WorldStorage> supplier) {
        return serverStorage.worldStorageMap.computeIfAbsent(key, s -> supplier.get());
    }

    @Getter
    @Broadcast
    @ExtraArgs({String.class, DynamicRegistryManager.class})
    private static final EventChannel<Meta> serverStorageLoad = new EventChannel<>();

    @Getter
    @Broadcast
    @ExtraArgs({String.class, DynamicRegistryManager.class})
    private static final EventChannel<Meta> serverStorageSave = new EventChannel<>();

    private static <W, T> void _putToSSSSMap(W key, T val, Map<W, T> mmm) {
        if (val != null) {
            mmm.put(key, val);
        } else {
            T oldValue = mmm.get(key);
            if (oldValue instanceof IStorage storage) {
                storage.storage.clear();
                storage.setDirty(true);
            } else {
                mmm.remove(key);
            }
        }
    }

    public static void update(BlockStorage storage, boolean autoRemoval) {}

    public static String getCurrentServerName() {
        return INSTANCE.mappedServerName();
    }

    private String mappedServerName() {
        String serverName = CommonUtils.getServerName();
        Preconditions.checkNotNull(serverName);
        return serverFolder.getPersistentFolder(serverName);
    }

    public static String PREFIX = "ws_";

    private String normalizedFileName(String path) {
        path = path.trim().replace(" ", "_").replaceAll("[\\\\/:*?\"<>|]|\\p{Cntrl}", "_");
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
        DynamicRegistryManager registry = mc.getNetworkHandler().getRegistryManager();

        if (enable.get()) {
            CompletableFuture.runAsync(() -> {
                        File folder = getStorageFolder(serverName);
                        File metaFile = getMetaFile(serverName);
                        FileManager.getInstance().checkFolder(folder);
                        synchronized (loadingStorage) {
                            try (FileStorage storage = FileManager.getInstance().getStorage(metaFile)) {
                                var serverMeta = storage.read(Meta.CODEC);
                                Meta mt;
                                if (serverMeta.isSuccess()
                                        && (mt = serverMeta.getOrThrow()).serverName.equals(serverName)) {
                                    if (mt.version < Meta.DATA_VERSION) {
                                        Meta upgraded = processUpdate(mt, storage, mt.version, Meta.DATA_VERSION);
                                        if (upgraded != mt) {
                                            mt = upgraded;
                                            saveStorage(serverName, mt);
                                        }
                                    }
                                } else {
                                    storage.write(Meta.CODEC, loadingStorage);
                                }
                                loadStorageFolders(serverName, loadingStorage);
                            } catch (Throwable e) {
                                Debug.info("Error while loading server storage:");
                                Debug.info(e);
                            }
                        }
                    })
                    .thenRunAsync(
                            () -> {
                                serverStorageLoad.broadcast(loadingStorage, serverName, registry);
                            },
                            mc);
        } else {
            serverStorageLoad.broadcast(loadingStorage, serverName, registry);
        }
        processAsyncUpdateMapSnapshot(null);
    }

    public static void processAsyncUpdateMapSnapshot(RegistryKey<World> world) {
        if (serverStorage != null && world != null) {
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
        saveTask = ScheduleService.launchAsyncRepeatTask(
                () -> {
                    if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getRegistryManager() != null) {
                        onSave(mc.getNetworkHandler().getRegistryManager());
                    }
                },
                15 * 1000,
                15 * 1000);
        // hot load
        if (!checkNull()) {
            if (updateServerName()) {
                onLoadStorage();
            }
        }
    }

    @Override
    public <W> void unregisterAll() {
        super.unregisterAll();
        if (saveTask != null) {
            ScheduleService.stopAsyncTask(saveTask);
        }
        // hot unload
        if (!checkNull()) {
            onSave(mc.getNetworkHandler().getRegistryManager());
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
        DynamicRegistryManager registry = mc.getNetworkHandler().getRegistryManager();
        CompletableFuture.runAsync(() -> onSave(registry));
    }

    private void onSave(DynamicRegistryManager registryReference) {
        if (currentServerName != null && serverStorage != null) {
            Meta currentSaveStorage = serverStorage;
            String serverName = currentServerName;
            serverStorageSave.broadcast(currentSaveStorage, currentServerName, registryReference);
            onSave(serverName, currentSaveStorage);
        }
    }

    public void onSave(String serverName, Meta currentSaveStorage) {
        if (enable.get()) {
            synchronized (currentSaveStorage) {
                if (currentSaveStorage.isDirty()) {
                    saveStorage(serverName, currentSaveStorage);
                }
            }
        }
    }

    private File getStorageFolder(String serverName) {
        return new File(SAVE_FILE, normalizedFileName(serverName));
    }

    private File getMetaFile(String serverName) {
        return new File(getStorageFolder(serverName), "meta.nbt");
    }

    private File getTypedFolder(String serverName, String folderName) {
        return new File(getStorageFolder(serverName), folderName);
    }

    private static String sanitizeWorldKey(RegistryKey<World> worldKey) {
        return worldKey.getValue().toString().replace(":", "_");
    }

    private static String blockFileName(BlockStorage storage) {
        return sanitizeWorldKey(storage.getDimension()) + "_" + storage.getPos().asLong();
    }

    private static String chunkFileName(ChunkStorage storage) {
        return sanitizeWorldKey(storage.getDimension()) + "_" + storage.getChunkPos().x + "_" + storage.getChunkPos().z;
    }

    private static String worldFileName(WorldStorage storage) {
        return sanitizeWorldKey(storage.getDimension());
    }

    private static String entityFileName(EntityStorage storage) {
        return storage.getUuid().toString();
    }

    private static File dataFile(File folder, String fileName) {
        return new File(folder, fileName + ".nbt");
    }

    private static IStorage storageFromNbt(NbtCompound compound) {
        Map<String, NbtElement> storage = new HashMap<>();
        for (String key : compound.getKeys()) {
            NbtElement element = compound.get(key);
            if (element != null) {
                storage.put(key, element.copy());
            }
        }
        return new IStorage(World.OVERWORLD, storage);
    }

    private static NbtCompound storageToNbt(IStorage storage) {
        NbtCompound compound = new NbtCompound();
        for (var entry : storage.storage.entrySet()) {
            compound.put(entry.getKey(), entry.getValue().copy());
        }
        return compound;
    }

    private <T extends IStorage> void saveStorageValue(
            File folder, T storageValue, Function<T, String> fileNameGetter, Codec<T> codec) {
        if (!storageValue.isDirty()) {
            return;
        }
        File target = dataFile(folder, fileNameGetter.apply(storageValue));
        if (storageValue.isEmpty()) {
            if (target.exists()) {
                target.delete();
            }
            storageValue.setDirty(false);
            return;
        }
        try (FileStorage storage = FileManager.getInstance().getStorage(target).asAutoSave()) {
            storage.write(codec, storageValue);
            storageValue.setDirty(false);
        }
    }

    private <K, T extends IStorage> void saveStorageIterator(
            File folder, Iterator<Map.Entry<K, T>> iterator, Function<T, String> fileNameGetter, Codec<T> codec) {
        FileManager.getInstance().checkFolder(folder);
        while (iterator.hasNext()) {
            T storageValue = iterator.next().getValue();
            saveStorageValue(folder, storageValue, fileNameGetter, codec);
            if (storageValue.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private <K, T extends IStorage> void saveNestedStorageIterator(
            File folder,
            Iterator<? extends Map.Entry<K, ? extends Map<?, T>>> outerIterator,
            Function<T, String> fileNameGetter,
            Codec<T> codec) {
        FileManager.getInstance().checkFolder(folder);
        while (outerIterator.hasNext()) {
            Map<?, T> storageMap = outerIterator.next().getValue();
            var iterator = storageMap.entrySet().iterator();
            while (iterator.hasNext()) {
                T storageValue = iterator.next().getValue();
                saveStorageValue(folder, storageValue, fileNameGetter, codec);
                if (storageValue.isEmpty()) {
                    iterator.remove();
                }
            }
            if (storageMap.isEmpty()) {
                outerIterator.remove();
            }
        }
    }

    private void saveStorage(String serverName, Meta currentSaveStorage) {
        File folder = getStorageFolder(serverName);
        FileManager.getInstance().checkFolder(folder);
        try (FileStorage storage =
                FileManager.getInstance().getStorage(getMetaFile(serverName)).asAutoSave()) {
            storage.write(Meta.CODEC, currentSaveStorage);
        }
        saveNestedStorageIterator(
                getTypedFolder(serverName, Meta.BLOCK_STORAGE_FOLDER),
                currentSaveStorage.blockStorageMap.entrySet().iterator(),
                ServerStorage::blockFileName,
                BlockStorage.CODEC);
        saveNestedStorageIterator(
                getTypedFolder(serverName, Meta.CHUNK_STORAGE_FOLDER),
                currentSaveStorage.chunkStorageMap.entrySet().iterator(),
                ServerStorage::chunkFileName,
                ChunkStorage.CODEC);
        saveStorageIterator(
                getTypedFolder(serverName, Meta.WORLD_STORAGE_FOLDER),
                currentSaveStorage.worldStorageMap.entrySet().iterator(),
                ServerStorage::worldFileName,
                WorldStorage.CODEC);
        saveStorageIterator(
                getTypedFolder(serverName, Meta.ENTITY_STORAGE_FOLDER),
                currentSaveStorage.entityStorageMap.entrySet().iterator(),
                ServerStorage::entityFileName,
                EntityStorage.CODEC);
    }

    private <T extends IStorage> void loadStorageFolder(File folder, Codec<T> codec, Consumer<T> consumer) {
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles(file -> file.isFile() && file.getName().endsWith(".nbt"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try (FileStorage storage = FileManager.getInstance().getStorage(file, false, false)) {
                if (storage == null) {
                    continue;
                }
                var result = storage.read(codec, null);
                if (result != null) {
                    result.setDirty(false);
                    consumer.accept(result);
                } else {
                    storage.delete();
                }
            } catch (UnsupportedOperationException invalidFile) {
                file.delete();
            } catch (Throwable e) {
            }
        }
    }

    private void loadStorageFolders(String serverName, Meta loadingStorage) {
        File folder = getStorageFolder(serverName);
        FileManager.getInstance().checkFolder(folder);
        loadStorageFolder(
                getTypedFolder(serverName, Meta.BLOCK_STORAGE_FOLDER),
                BlockStorage.CODEC,
                loadingStorage::putBlockStorage);
        loadStorageFolder(
                getTypedFolder(serverName, Meta.CHUNK_STORAGE_FOLDER),
                ChunkStorage.CODEC,
                loadingStorage::putChunkStorage);
        loadStorageFolder(
                getTypedFolder(serverName, Meta.WORLD_STORAGE_FOLDER),
                WorldStorage.CODEC,
                loadingStorage::putWorldStorage);
        loadStorageFolder(
                getTypedFolder(serverName, Meta.ENTITY_STORAGE_FOLDER),
                EntityStorage.CODEC,
                loadingStorage::putEntityStorage);
    }

    public Meta processUpdate(Meta meta, FileStorage oldStorage, int oldVersion, int newVersion) {
        Meta current = meta;
        int version = oldVersion;
        while (version < newVersion) {
            if (version == 0) {
                current = processUpdateV0ToV1(current, oldStorage);
            } else {
                break;
            }
            version = current.version;
        }
        return current;
    }

    private Meta processUpdateV0ToV1(Meta meta, FileStorage oldStorage) {
        Meta legacy = oldStorage.read(Meta.LEGACY_CODEC).result().orElse(meta);
        Meta updated = new Meta(legacy.serverName, Meta.V1_DATA_VERSION);
        legacy.allBlockStorages().forEach(storage -> {
            storage.setDirty(true);
            updated.putBlockStorage(storage);
        });
        legacy.allChunkStorages().forEach(storage -> {
            storage.setDirty(true);
            updated.putChunkStorage(storage);
        });
        legacy.allWorldStorages().forEach(storage -> {
            storage.setDirty(true);
            updated.putWorldStorage(storage);
        });
        return updated;
    }

    public static class Meta {
        public static final int V1_DATA_VERSION = 1;
        public static final int DATA_VERSION = V1_DATA_VERSION;
        public static final String BLOCK_STORAGE_FOLDER = "block-storage";
        public static final String CHUNK_STORAGE_FOLDER = "chunk-storage";
        public static final String WORLD_STORAGE_FOLDER = "world-storage";
        public static final String ENTITY_STORAGE_FOLDER = "entity-storage";

        public static final Codec<Meta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("server-name").forGetter(Meta::getServerName),
                        Codec.INT.fieldOf("data-version").forGetter(meta -> meta.version))
                .apply(instance, Meta::new));

        public static final Codec<Meta> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("server-name").forGetter(Meta::getServerName),
                        Codec.INT.fieldOf("data-version").forGetter(meta -> meta.version),
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
        public final Map<UUID, EntityStorage> entityStorageMap;

        public final int version;

        @Getter
        public final String serverName;

        public Meta(String serverName) {
            this(serverName, DATA_VERSION);
        }

        public Meta(String serverName, int version) {
            this.serverName = serverName;
            this.version = version;
            this.blockStorageMap = new ConcurrentHashMap<>();
            this.chunkStorageMap = new ConcurrentHashMap<>();
            this.worldStorageMap = new ConcurrentHashMap<>();
            this.entityStorageMap = new ConcurrentHashMap<>();
        }

        public Meta(
                String serverName,
                int version,
                List<BlockStorage> blockStorageList,
                List<ChunkStorage> chunkStorage,
                List<WorldStorage> worldStorageList) {
            this(serverName, version);
            loadLegacy(blockStorageList, chunkStorage, worldStorageList);
        }

        public void loadLegacy(
                List<BlockStorage> blockStorageList,
                List<ChunkStorage> chunkStorage,
                List<WorldStorage> worldStorageList) {
            blockStorageMap.clear();
            chunkStorageMap.clear();
            worldStorageMap.clear();
            entityStorageMap.clear();
            for (var re : blockStorageList) {
                putBlockStorage(re);
            }
            for (var re : chunkStorage) {
                putChunkStorage(re);
            }
            for (var re : worldStorageList) {
                putWorldStorage(re);
            }
        }

        private static <T, W, R> ConcurrentHashMap<W, R> newMap(T k) {
            return new ConcurrentHashMap<>();
        }

        public void putBlockStorage(BlockStorage storage) {
            this.blockStorageMap
                    .computeIfAbsent(storage.getDimension(), Meta::newMap)
                    .put(storage.getPos(), storage);
        }

        public void putChunkStorage(ChunkStorage storage) {
            this.chunkStorageMap
                    .computeIfAbsent(storage.getDimension(), Meta::newMap)
                    .put(storage.getChunkPos(), storage);
        }

        public void putWorldStorage(WorldStorage storage) {
            this.worldStorageMap.put(storage.getDimension(), storage);
        }

        public void putEntityStorage(EntityStorage storage) {
            this.entityStorageMap.put(storage.getUuid(), storage);
        }

        public List<BlockStorage> toBlockList() {
            return allBlockStorages().stream().filter(BlockStorage::nonEmpty).toList();
        }

        public List<ChunkStorage> toChunkList() {
            return allChunkStorages().stream().filter(ChunkStorage::nonEmpty).toList();
        }

        public List<WorldStorage> toWorldList() {
            return allWorldStorages().stream().filter(WorldStorage::nonEmpty).toList();
        }

        public Collection<BlockStorage> allBlockStorages() {
            return blockStorageMap.values().stream()
                    .flatMap(s -> s.values().stream())
                    .toList();
        }

        public Collection<ChunkStorage> allChunkStorages() {
            return chunkStorageMap.values().stream()
                    .flatMap(s -> s.values().stream())
                    .toList();
        }

        public Collection<WorldStorage> allWorldStorages() {
            return worldStorageMap.values().stream().toList();
        }

        public Collection<EntityStorage> allEntityStorages() {
            return entityStorageMap.values().stream().toList();
        }

        public BlockStorage getBlockStorage(RegistryKey<World> world, BlockPos pos, boolean createIfAbsent) {
            if (createIfAbsent) {
                return blockStorageMap
                        .computeIfAbsent(world, Meta::newMap)
                        .computeIfAbsent(pos, k -> new BlockStorage(world, k));
            } else {
                var map = blockStorageMap.get(world);
                if (map != null) {
                    return map.get(pos);
                } else {
                    return null;
                }
            }
        }

        public ChunkStorage getChunkStorage(RegistryKey<World> world, ChunkPos pos, boolean createIfAbsent) {
            if (createIfAbsent) {
                return chunkStorageMap
                        .computeIfAbsent(world, Meta::newMap)
                        .computeIfAbsent(pos, k -> new ChunkStorage(world, k));
            } else {
                var map = chunkStorageMap.get(world);
                if (map != null) {
                    return map.get(pos);
                } else {
                    return null;
                }
            }
        }

        public WorldStorage getWorldStorage(RegistryKey<World> world, boolean createIfAbsent) {
            if (createIfAbsent) {
                return worldStorageMap.computeIfAbsent(world, WorldStorage::new);
            } else {
                return worldStorageMap.get(world);
            }
        }

        public EntityStorage getEntityStorage(UUID uuid, boolean createIfAbsent) {
            if (createIfAbsent) {
                return entityStorageMap.computeIfAbsent(uuid, EntityStorage::new);
            }
            return entityStorageMap.get(uuid);
        }

        public void markDirty() {}

        public boolean isDirty() {
            return Streams.concat(
                            allBlockStorages().stream(),
                            allChunkStorages().stream(),
                            allWorldStorages().stream(),
                            allEntityStorages().stream())
                    .anyMatch(IStorage::isDirty);
        }
    }

    @With
    public static record ServerFolder(Map<String, String> ipToFolder, Map<String, String> ipProxy) {
        public static final Codec<ServerFolder> CODEC = RecordCodecBuilder.create(oinstance -> oinstance
                .group(
                        Codec.unboundedMap(Codec.STRING, Codec.STRING)
                                .optionalFieldOf(NAME_MAPPER_KEY, Map.of())
                                .forGetter(ServerFolder::ipToFolder),
                        Codec.unboundedMap(Codec.STRING, Codec.STRING)
                                .optionalFieldOf(PROXY_KEY, Map.of())
                                .forGetter(ServerFolder::ipProxy))
                .apply(oinstance, ServerFolder::new));

        public String getPersistentFolder(String ip) {
            String ip2 = getProxiedIp(ip);
            for (var re : ipToFolder.entrySet()) {
                if (re.getKey().equalsIgnoreCase(ip2)) {
                    return re.getValue();
                }
            }
            return ip2;
        }

        public String getProxiedIp(String ip) {
            return ipProxy.getOrDefault(ip, ip);
        }
    }
}
