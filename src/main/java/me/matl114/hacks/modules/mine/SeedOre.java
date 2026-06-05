package me.matl114.hacks.modules.mine;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JavaOps;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.*;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.*;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;

public class SeedOre extends BaseModule {
    public static SeedOre INSTANCE;
    public static final String[] SEED_MAP = new String[] {"seed", "seed-cache"};
    public final Object2LongMap<String> seedMap = new Object2LongOpenHashMap<>();
    // how to do cache: chunkUnload
    private final Map<Long, Map<Ore, Set<Vec3d>>> chunkSeedCache = new ConcurrentHashMap<>();
    private final Map<Long, Map<BlockPos, BlockState>> fakeOres = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public void saveSeedMap() {
        seedMapSave.write((Map<String, Long>) seedMap, JavaOps.INSTANCE);
    }

    public SeedOre() {
        super();
        bindFlag(enable);
        INSTANCE = this;
    }

    public final ModulePath seed = makePath(Configs.MINE_CONFIG, "aaxray.seed-ore");

    public final FlagRef enable = flagBuilder(seed.addEnable()).build();

    public final IntRef chunkRadius = builder(seed.add("chunk-radius"), IntRef.TYPE)
            .defaultValue(6)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef enableRender = flagBuilder(seed.add("render-ore")).build();

    boolean fakeOre = false;
    public final FlagRef enableFakeOres = flagBuilder(seed.add("enable-fake-ore"))
            .defaultValue(false)
            .updateListener(this::onFakeOreToggle)
            .build();

    public final StringRef oreWhiteList = builder(seed.add("ore-white-list"), String.class)
            .defaultValue("^(diamond)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener(Ore::reloadOreSettings)
            .build();

    public final FileStorage seedMapSave = FileManager.getInstance().getInternalStorage("seed-storage.nbt");

    {
        NbtCompound nbt = seedMapSave.asReadOnly(NbtOps.INSTANCE);
        seedMap.clear();
        for (var entry : nbt.entrySet()) {
            String key = entry.getKey();
            NbtElement value = entry.getValue();
            if (value instanceof NbtLong ll) {
                seedMap.put(key, ll.value());
            }
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onDimensionChange);
        // this needs run on main thread to ensure the chunk is accessible
        registerListener(Listener.getPacketPostHandlePoint().getChannel(ChunkDataS2CPacket.class), this::onChunkUpdate);
        registerListener(Listener.getPacketPoint().getChannel(BlockUpdateS2CPacket.class), this::onBlockUpdate);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRenderOreSimulation);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        if (mc.world != null) {
            onReloadSeedOre();
        }
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        oreConfig = null;
        if (mc.player != null && mc.world != null) {
            Debug.chat(Text.literal("[种子矿透] 禁用该功能").formatted(Formatting.RED));
        }
        onRemoveFakeOreVisibleChunks();
    }

    public void onReloadSeedOre() {
        try {
            onClearCachedResults();
            if (mc.world != null) {
                // remove Fake ores existing
                onRemoveFakeOreVisibleChunks();
            }
            oreConfig = Ore.getRegistry();
            if (mc.player != null && mc.world != null) {
                Debug.chat(Text.literal("[种子矿透] 启用该功能, 范围 %d".formatted(chunkRadius.get()))
                        .formatted(Formatting.GREEN));
                onLoadCurrentVisibleChunks();
            }
            // load fake ores are in onLoadCurrentVisibleChunks

        } catch (Throwable e) {
            Debug.info(e);
            if (mc.player != null) Debug.chat(Text.literal("[种子矿透] 启用时出现报错, 已关闭..."));
            enable.set(false);
        }
    }

    public void onFakeOreToggle(boolean va) {
        if (va != fakeOre) {
            fakeOre = va;
            if (enable.get()) {
                if (fakeOre) {
                    onReloadFakeOre();
                } else {
                    onRemoveFakeOreVisibleChunks();
                }
            }
        }
    }

    public void onReloadFakeOre() {
        if (mc.world != null) {
            onRemoveFakeOreVisibleChunks();
            onReloadFakeOreVisibleChunks();
        }
    }

    // -26225.23 67.00 -23482.37
    public void onDimensionChange(Event<World> v) {
        onClearCachedResults();
        if (isActive()) {
            // reload config
            onReloadSeedOre();
        }
    }

    public void putSeed(String key, long seed) {
        if (seedMap.containsKey(key)) {
            long old = seedMap.getLong(key);
            if (old != seed) {
                seedMap.put(key, seed);
                saveSeedMap();
                onSeedChange(key);
            }
        } else {
            seedMap.put(key, seed);
            saveSeedMap();
            onSeedChange(key);
        }
    }

    public void removeSeed(String key) {
        if (seedMap.containsKey(key)) {
            seedMap.removeLong(key);
            onSeedChange(key);
        }
    }

    public void onSeedChange(String key) {
        if (!checkCurrentSeedExistence()) return;
        if (Objects.equals(CommonUtils.getWorldName(), key) && enable.get()) {
            Debug.chat("[种子矿透] 重载Seed Ore Simulation功能");
            onReloadSeedOre();
        }
    }

    public boolean hasCurrentSeed() {
        return seedMap.containsKey(CommonUtils.getWorldName());
    }

    public long getCurrentSeed() {
        return seedMap.getLong(CommonUtils.getWorldName());
    }

    @ApiMethod
    public void setWorldSeed(long seed) {
        putSeed(CommonUtils.getWorldName(), seed);
    }

    @ApiMethod
    public void removeWorldSeed() {
        removeSeed(CommonUtils.getWorldName());
    }

    public boolean checkCurrentSeedExistence() {
        if (hasCurrentSeed()) {
            return true;
        } else {
            Debug.chat(Text.literal("[世界种子] 暂时没有设置 %s 世界的种子".formatted(CommonUtils.getWorldName()))
                    .formatted(Formatting.RED));
            enable.set(false);
            return false;
        }
    }

    public static boolean isSeedValid(long seed) {
        long hashed = mc.world.getBiomeAccess().seed;
        return BiomeAccess.hashSeed(seed) == hashed;
    }

    public void validateCurrentSeed() {
        if (!checkCurrentSeedExistence()) return;
        long value = seedMap.getLong(CommonUtils.getWorldName());
        Debug.chat(Text.literal("[世界种子] 核验当前世界种子中:").formatted(Formatting.GREEN));
        Debug.chat(
                Text.literal("[世界种子] 输入的种子: ").formatted(Formatting.GREEN).append(ChatUtils.getDisplayedLong(value)));
        long hashed = mc.world.getBiomeAccess().seed;
        Debug.chat(Text.literal("[世界种子] 服务器加密种子: ").append(ChatUtils.getDisplayedLong(hashed)));
        if (isSeedValid(value)) {
            Debug.chat(Text.literal("[世界种子] 验证通过").formatted(Formatting.GREEN));
        } else {
            Debug.chat(Text.literal("[世界种子] 验证失败").formatted(Formatting.RED));
        }
    }

    // 重载可视区块的种子计算 包含了假矿计算
    public void onLoadCurrentVisibleChunks() {
        if (mc.world == null) return;
        for (Chunk chunk : CommonUtils.chunks(false)) {
            updateChunk(chunk);
        }
    }
    // 重载假矿
    public void onReloadFakeOreVisibleChunks() {
        if (mc.world == null) return;
        for (Chunk chunk : CommonUtils.chunks(false)) {
            long key = chunk.getPos().toLong();
            var map = chunkSeedCache.get(key);
            if (map != null && !map.isEmpty()) {
                updateOreClientSide(key, map);
            }
        }
    }
    // 移除假矿
    public void onRemoveFakeOreVisibleChunks() {
        if (mc.world == null) return;
        for (Chunk chunk : CommonUtils.chunks(false)) {
            long key = chunk.getPos().toLong();
            var map = fakeOres.remove(key);
            if (map != null && !map.isEmpty()) {
                removeChunkFakeOres(key, map);
            }
        }
    }
    // 移除内部实现
    private void removeChunkFakeOres(long key, Map<BlockPos, BlockState> originDatas) {
        for (var re : originDatas.entrySet()) {
            BlockPos pos0 = re.getKey();
            BlockState state0 = re.getValue();
            Tasks.scheduleDelayed(
                    () -> {
                        if (mc.world != null) {
                            mc.world.setBlockState(pos0, state0);
                        }
                    },
                    1);
        }
    }

    private void onClearCachedResults() {
        chunkSeedCache.clear();
        fakeOres.clear();
    }

    // events that updates the chunk
    public void onChunkUpdate(Event<ChunkDataS2CPacket> packet) {
        // update data as scheduled after the handle
        Packet<?> packet1 = packet.context;
        if (enable.get()) {
            var dataS2CPacket = packet.context;
            int x = dataS2CPacket.getChunkX();
            int z = dataS2CPacket.getChunkZ();
            Tasks.scheduleDelayed(
                    () -> {
                        updateChunk(mc.world.getChunk(x, z));
                    },
                    2);
        }
    }

    public void onBlockUpdate(Event<BlockUpdateS2CPacket> event) {
        // remove cache whenever
        // remove async
        if (!chunkSeedCache.isEmpty() || !fakeOres.isEmpty()) {
            var packet = event.context;
            long chunkKey = ChunkPos.toLong((packet).getPos());
            var map = chunkSeedCache.get(chunkKey);
            Vec3d pos = Vec3d.of(packet.getPos());
            if (map != null && !map.isEmpty()) {
                for (var ore : map.values()) {
                    ore.remove(pos);
                }
            }
            var map2 = fakeOres.get(chunkKey);
            if (map2 != null && !map2.isEmpty()) {
                map2.remove(packet.getPos());
            }
        }
    }
    // render issues

    public void onRenderOreSimulation(Event<MatrixStack> event) {
        var stack = event.context;
        if (mc.player == null || oreConfig == null) return;
        if (!enable.get()) return;
        if (!enableRender.get()) return;
        if (!checkCurrentSeedExistence()) return;
        RenderUtils.startDrawVirtual(stack);
        try {
            int chunkX = mc.player.getChunkPos().x;
            int chunkZ = mc.player.getChunkPos().z;

            int rangeVal = chunkRadius.get();
            for (int range = 0; range <= rangeVal; range++) {
                for (int x = -range + chunkX; x <= range + chunkX; x++) {
                    renderChunk(x, chunkZ + range - rangeVal, stack);
                }
                for (int x = (-range) + 1 + chunkX; x < range + chunkX; x++) {
                    renderChunk(x, chunkZ - range + rangeVal + 1, stack);
                }
            }
        } finally {
            RenderUtils.stopDrawVirtual(stack);
        }
    }

    private void renderChunk(int x, int z, MatrixStack event) {
        long chunkKey = ChunkPos.toLong(x, z);

        if (chunkSeedCache.containsKey(chunkKey)) {
            Map<Ore, Set<Vec3d>> chunk = chunkSeedCache.get(chunkKey);

            for (Map.Entry<Ore, Set<Vec3d>> oreRenders : chunk.entrySet()) {
                if (oreRenders.getKey().active.getOriginValue() == Boolean.TRUE) {
                    Color color = oreRenders.getKey().color;
                    for (Vec3d pos : oreRenders.getValue()) {
                        Vec3d centerPos = BlockPos.ofFloored(pos).toCenterPos();

                        // event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1,
                        // oreRenders.getKey().color, 0);
                        RenderUtils.drawOutlinedBox(
                                event, centerPos.add(RenderTasks.FROM), centerPos.add(RenderTasks.TO), color);
                    }
                }
            }
        }
    }

    public Map<String, Set<Vec3d>> getSeedOres(int x, int z) {
        Map<String, Set<Vec3d>> map = new HashMap<>();
        for (var entry :
                chunkSeedCache.getOrDefault(ChunkPos.toLong(x, z), Map.of()).entrySet()) {
            map.put(entry.getKey().active.getKeyName(), entry.getValue());
        }
        return map;
    }

    private void updateChunk(Chunk chunk) {
        if (!enable.get()) return;
        if (!checkCurrentSeedExistence()) {
            return;
        }
        var chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        ClientWorld world = mc.world;
        // clear cache when switching world
        Map<Ore, Set<Vec3d>> h;

        if (chunkSeedCache.containsKey(chunkKey) || world == null || oreConfig == null) {
            h = chunkSeedCache.get(chunkKey);
        } else {
            Set<RegistryKey<Biome>> biomes = new HashSet<>();
            ChunkPos.stream(chunkPos, 1).forEach(chunkPosx -> {
                Chunk chunkxx = world.getChunk(chunkPosx.x, chunkPosx.z, ChunkStatus.BIOMES, false);
                if (chunkxx == null) return;

                for (ChunkSection chunkSection : chunkxx.getSectionArray()) {
                    chunkSection
                            .getBiomeContainer()
                            .forEachValue(entry -> biomes.add(entry.getKey().get()));
                }
            });
            Set<Ore> oreSet =
                    biomes.stream().flatMap(b -> getDefaultOres(b).stream()).collect(Collectors.toSet());

            int chunkX = chunkPos.x << 4;
            int chunkZ = chunkPos.z << 4;
            ChunkRandom random = new ChunkRandom(ChunkRandom.RandomProvider.XOROSHIRO.create(0));

            long populationSeed = random.setPopulationSeed(getCurrentSeed(), chunkX, chunkZ);
            h = new ConcurrentHashMap<>();
            for (Ore ore : oreSet) {

                Set<Vec3d> ores = ConcurrentHashMap.newKeySet();

                random.setDecoratorSeed(populationSeed, ore.index, ore.step);

                int repeat = ore.count.get(random);

                for (int i = 0; i < repeat; i++) {

                    if (ore.rarity != 1F && random.nextFloat() >= 1 / ore.rarity) {
                        continue;
                    }

                    int x = random.nextInt(16) + chunkX;
                    int z = random.nextInt(16) + chunkZ;
                    int y = ore.heightProvider.get(random, ore.heightContext);
                    BlockPos origin = new BlockPos(x, y, z);

                    RegistryKey<Biome> biome =
                            chunk.getBiomeForNoiseGen(x, y, z).getKey().get();

                    if (!getDefaultOres(biome).contains(ore)) {
                        continue;
                    }

                    if (ore.scattered) {
                        ores.addAll(generateHidden(world, random, origin, ore.size));
                    } else {
                        ores.addAll(generateNormal(world, random, origin, ore.size, ore.discardOnAirChance));
                    }
                }
                if (!ores.isEmpty()) {
                    h.put(ore, ores);
                }
            }
        }
        if (h != null && !h.isEmpty()) {
            // fake ore do not load automatically
            chunkSeedCache.put(chunkKey, h);
            if (enableFakeOres.get()) {
                updateOreClientSide(chunkKey, h);
            }
        }
    }
    // for fake ores
    private void updateOreClientSide(long chunkey, Map<Ore, Set<Vec3d>> ores) {
        if (mc.world == null) return;
        if (!enableFakeOres.get()) return;

        // return blockstates already cached
        var map0 = fakeOres.remove(chunkey);
        if (map0 != null && !map0.isEmpty()) {
            removeChunkFakeOres(chunkey, map0);
        }

        Map<BlockPos, BlockState> newFakeOres = new ConcurrentHashMap<>();
        int minY = mc.world.getBottomY();
        for (var ore0 : ores.entrySet()) {
            Ore oreType = ore0.getKey();
            var sample = oreType.sampleBlock;
            var sampleDeepslate = oreType.sampleDeepslateBlock;
            loop:
            for (var pos : ore0.getValue()) {
                if (pos.y < minY + 4) {
                    // mostly bedrock , escape
                    continue;
                }
                var block = pos.y > 0 ? sample : sampleDeepslate;
                var blockState = block.getDefaultState();
                var blockPos = BlockPos.ofFloored(pos);
                BlockState state = mc.world.getBlockState(blockPos);
                // todo: air can be faked!!! that's allshit,
                // fuck you 3c3u
                if (!state.isAir() && state.getBlock() != sample && state.getBlock() != sampleDeepslate) {
                    // not naked and not the same
                    for (Direction direction : Direction.values()) {
                        BlockPos testPos = blockPos.offset(direction);
                        if (mc.world.getBlockState(testPos).isAir()) {
                            continue loop;
                        }
                    }
                    newFakeOres.put(blockPos, state);
                    // run sync
                    Tasks.scheduleDelayed(
                            () -> {
                                if (mc.world != null) mc.world.setBlockState(blockPos, blockState);
                            },
                            2);
                }
            }
        }
        fakeOres.put(chunkey, newFakeOres);
    }

    private Map<RegistryKey<Biome>, List<Ore>> oreConfig;

    private List<Ore> getDefaultOres(RegistryKey<Biome> biomeRegistryKey) {
        if (oreConfig.containsKey(biomeRegistryKey)) {
            return oreConfig.get(biomeRegistryKey);
        } else {
            return oreConfig.values().stream().findAny().get();
        }
    }

    static {
        Ore.init();
    }

    // ====================================
    // Mojang code
    // ====================================

    private static ArrayList<Vec3d> generateNormal(
            ClientWorld world, ChunkRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = MathHelper.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - MathHelper.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - MathHelper.ceil(g) - i;
        int q = 2 * (MathHelper.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getTopY(Heightmap.Type.MOTION_BLOCKING, s, t)) {
                    return generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir);
                }
            }
        }

        return new ArrayList<>();
    }

    private static ArrayList<Vec3d> generateVeinPart(
            ClientWorld world,
            ChunkRandom random,
            int veinSize,
            double startX,
            double endX,
            double startZ,
            double endZ,
            double startY,
            double endY,
            int x,
            int y,
            int z,
            int size,
            int i,
            float discardOnAir) {

        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double[] ds = new double[veinSize * 4];

        ArrayList<Vec3d> poses = new ArrayList<>();

        int n;
        double p;
        double q;
        double r;
        double s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = MathHelper.lerp(f, startX, endX);
            q = MathHelper.lerp(f, startY, endY);
            r = MathHelper.lerp(f, startZ, endZ);
            s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (MathHelper.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < veinSize - 1; ++n) {
            if (!(ds[n * 4 + 3] <= 0.0D)) {
                for (int o = n + 1; o < veinSize; ++o) {
                    if (!(ds[o * 4 + 3] <= 0.0D)) {
                        p = ds[n * 4] - ds[o * 4];
                        q = ds[n * 4 + 1] - ds[o * 4 + 1];
                        r = ds[n * 4 + 2] - ds[o * 4 + 2];
                        s = ds[n * 4 + 3] - ds[o * 4 + 3];
                        if (s * s > p * p + q * q + r * r) {
                            if (s > 0.0D) {
                                ds[o * 4 + 3] = -1.0D;
                            } else {
                                ds[n * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        for (n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (!(u < 0.0D)) {
                double v = ds[n * 4];
                double w = ds[n * 4 + 1];
                double aa = ds[n * 4 + 2];
                int ab = Math.max(MathHelper.floor(v - u), x);
                int ac = Math.max(MathHelper.floor(w - u), y);
                int ad = Math.max(MathHelper.floor(aa - u), z);
                int ae = Math.max(MathHelper.floor(v + u), ab);
                int af = Math.max(MathHelper.floor(w + u), ac);
                int ag = Math.max(MathHelper.floor(aa + u), ad);

                for (int ah = ab; ah <= ae; ++ah) {
                    double ai = ((double) ah + 0.5D - v) / u;
                    if (ai * ai < 1.0D) {
                        for (int aj = ac; aj <= af; ++aj) {
                            double ak = ((double) aj + 0.5D - w) / u;
                            if (ai * ai + ak * ak < 1.0D) {
                                for (int al = ad; al <= ag; ++al) {
                                    double am = ((double) al + 0.5D - aa) / u;
                                    if (ai * ai + ak * ak + am * am < 1.0D) {
                                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                                        if (!bitSet.get(an)) {
                                            bitSet.set(an);
                                            mutable.set(ah, aj, al);
                                            if (aj >= -64
                                                    && aj < 320
                                                    && (world.getBlockState(mutable)
                                                            .isOpaque())) {
                                                if (shouldPlace(world, mutable, discardOnAir, random)) {
                                                    poses.add(new Vec3d(ah, aj, al));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return poses;
    }

    private static boolean shouldPlace(ClientWorld world, BlockPos orePos, float discardOnAir, ChunkRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) {
            return true;
        }

        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(orePos.add(direction.getVector())).isOpaque() && discardOnAir != 1F) {
                return false;
            }
        }
        return true;
    }

    private static ArrayList<Vec3d> generateHidden(ClientWorld world, ChunkRandom random, BlockPos blockPos, int size) {

        ArrayList<Vec3d> poses = new ArrayList<>();

        int i = random.nextInt(size + 1);

        for (int j = 0; j < i; ++j) {
            size = Math.min(j, 7);
            int x = randomCoord(random, size) + blockPos.getX();
            int y = randomCoord(random, size) + blockPos.getY();
            int z = randomCoord(random, size) + blockPos.getZ();
            if (world.getBlockState(new BlockPos(x, y, z)).isOpaque()) {
                if (shouldPlace(world, new BlockPos(x, y, z), 1F, random)) {
                    poses.add(new Vec3d(x, y, z));
                }
            }
        }

        return poses;
    }

    private static int randomCoord(ChunkRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) size);
    }

    public static class Ore {
        private static final AttrKeyValue<Boolean> coal = AttrKeyValue.bool("Coal");
        private static final AttrKeyValue<Boolean> iron = AttrKeyValue.bool("Iron");
        private static final AttrKeyValue<Boolean> gold = AttrKeyValue.bool("Gold");
        private static final AttrKeyValue<Boolean> redstone = AttrKeyValue.bool("Redstone");
        private static final AttrKeyValue<Boolean> diamond = AttrKeyValue.bool("Diamond");
        private static final AttrKeyValue<Boolean> lapis = AttrKeyValue.bool("Lapis");
        private static final AttrKeyValue<Boolean> copper = AttrKeyValue.bool("Copper");
        private static final AttrKeyValue<Boolean> emerald = AttrKeyValue.bool("Emerald");
        private static final AttrKeyValue<Boolean> quartz = AttrKeyValue.bool("Quartz");
        private static final AttrKeyValue<Boolean> debris = AttrKeyValue.bool("Ancient Debris");
        private static final Map<String, Pair<Block, Block>> oreMapping =
                ImmutableMap.<String, Pair<Block, Block>>builder()
                        .put("Coal", Pair.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE))
                        .put("Iron", Pair.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE))
                        .put("Gold", Pair.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE))
                        .put("Redstone", Pair.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE))
                        .put("Diamond", Pair.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE))
                        .put("Lapis", Pair.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE))
                        .put("Copper", Pair.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE))
                        .put("Emerald", Pair.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE))
                        .put("Quartz", Pair.of(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_QUARTZ_ORE))
                        .put("Ancient Debris", Pair.of(Blocks.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS))
                        .build();
        public static final List<AttrKeyValue<Boolean>> oreSettings = new ArrayList<>(
                Arrays.asList(coal, iron, gold, redstone, diamond, lapis, copper, emerald, quartz, debris));

        public static void reloadOreSettings(String value) {
            try {
                var regex = Pattern.compile(value).asMatchPredicate();
                for (var ore : oreSettings) {
                    if (regex.test(ore.getKeyName().toLowerCase(Locale.ROOT))) {
                        ore.valueChange(ore, "true");
                    } else {
                        ore.valueChange(ore, "false");
                    }
                }
            } catch (Throwable e) {
                // Debug.chat("");
            }
        }

        public static void init() {}

        public static Map<RegistryKey<Biome>, List<Ore>> getRegistry() {

            RegistryWrapper.WrapperLookup registry = BuiltinRegistries.createWrapperLookup();
            RegistryWrapper.Impl<PlacedFeature> features = registry.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
            var reg = registry.getWrapperOrThrow(RegistryKeys.WORLD_PRESET)
                    .getOrThrow(WorldPresets.DEFAULT)
                    .value()
                    .createDimensionsRegistryHolder()
                    .dimensions();
            RegistryKey<DimensionOptions> options = CommonUtils.getCurrentDimensionOption();
            var dim = reg.get(options);

            var biomes = dim.chunkGenerator().getBiomeSource().getBiomes();
            var biomes1 = biomes.stream().toList();

            List<PlacedFeatureIndexer.IndexedFeatures> indexer = PlacedFeatureIndexer.collectIndexedFeatures(
                    biomes1,
                    biomeEntry -> biomeEntry.value().getGenerationSettings().getFeatures(),
                    true);

            Map<PlacedFeature, Ore> featureToOre = new HashMap<>();
            registerOre(
                    featureToOre, indexer, features, OrePlacedFeatures.ORE_COAL_LOWER, 6, coal, new Color(47, 44, 54));
            registerOre(
                    featureToOre, indexer, features, OrePlacedFeatures.ORE_COAL_UPPER, 6, coal, new Color(47, 44, 54));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_IRON_MIDDLE,
                    6,
                    iron,
                    new Color(236, 173, 119));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_IRON_SMALL,
                    6,
                    iron,
                    new Color(236, 173, 119));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_IRON_UPPER,
                    6,
                    iron,
                    new Color(236, 173, 119));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_GOLD, 6, gold, new Color(247, 229, 30));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_GOLD_LOWER,
                    6,
                    gold,
                    new Color(247, 229, 30));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_GOLD_EXTRA,
                    6,
                    gold,
                    new Color(247, 229, 30));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_GOLD_NETHER,
                    7,
                    gold,
                    new Color(247, 229, 30));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_GOLD_DELTAS,
                    7,
                    gold,
                    new Color(247, 229, 30));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_REDSTONE,
                    6,
                    redstone,
                    new Color(245, 7, 23));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_REDSTONE_LOWER,
                    6,
                    redstone,
                    new Color(245, 7, 23));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_DIAMOND,
                    6,
                    diamond,
                    new Color(33, 244, 255));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_DIAMOND_BURIED,
                    6,
                    diamond,
                    new Color(33, 244, 255));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_DIAMOND_LARGE,
                    6,
                    diamond,
                    new Color(33, 244, 255));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_DIAMOND_MEDIUM,
                    6,
                    diamond,
                    new Color(33, 244, 255));
            registerOre(featureToOre, indexer, features, OrePlacedFeatures.ORE_LAPIS, 6, lapis, new Color(8, 26, 189));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_LAPIS_BURIED,
                    6,
                    lapis,
                    new Color(8, 26, 189));
            registerOre(
                    featureToOre, indexer, features, OrePlacedFeatures.ORE_COPPER, 6, copper, new Color(239, 151, 0));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_COPPER_LARGE,
                    6,
                    copper,
                    new Color(239, 151, 0));
            registerOre(
                    featureToOre, indexer, features, OrePlacedFeatures.ORE_EMERALD, 6, emerald, new Color(27, 209, 45));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_QUARTZ_NETHER,
                    7,
                    quartz,
                    new Color(205, 205, 205));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_QUARTZ_DELTAS,
                    7,
                    quartz,
                    new Color(205, 205, 205));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_DEBRIS_SMALL,
                    7,
                    debris,
                    new Color(209, 27, 245));
            registerOre(
                    featureToOre,
                    indexer,
                    features,
                    OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE,
                    7,
                    debris,
                    new Color(209, 27, 245));

            Map<RegistryKey<Biome>, List<Ore>> biomeOreMap = new HashMap<>();

            biomes1.forEach(biome -> {
                biomeOreMap.put(biome.getKey().get(), new ArrayList<>());
                biome.value().getGenerationSettings().getFeatures().stream()
                        .flatMap(RegistryEntryList::stream)
                        .map(RegistryEntry::value)
                        .filter(featureToOre::containsKey)
                        .forEach(feature -> {
                            biomeOreMap.get(biome.getKey().get()).add(featureToOre.get(feature));
                        });
            });
            return biomeOreMap;
        }

        private static void registerOre(
                Map<PlacedFeature, Ore> map,
                List<PlacedFeatureIndexer.IndexedFeatures> indexer,
                RegistryWrapper.Impl<PlacedFeature> oreRegistry,
                RegistryKey<PlacedFeature> oreKey,
                int genStep,
                AttrKeyValue<Boolean> active,
                Color color) {
            var orePlacement = oreRegistry.getOrThrow(oreKey).value();

            int index = indexer.get(genStep).indexMapping().applyAsInt(orePlacement);
            var pair = oreMapping.getOrDefault(active.getKeyName(), Pair.of(Blocks.IRON_ORE, Blocks.IRON_ORE));
            Ore ore = new Ore(orePlacement, pair.getFirst(), pair.getSecond(), genStep, index, active, color);

            map.put(orePlacement, ore);
        }

        public int step;
        public int index;
        public Block sampleBlock;
        public Block sampleDeepslateBlock;
        public AttrKeyValue<Boolean> active;
        public IntProvider count = ConstantIntProvider.create(1);
        public HeightProvider heightProvider;
        public HeightContext heightContext;
        public float rarity = 1;
        public float discardOnAirChance;
        public int size;
        public Color color;
        public boolean scattered;

        private Ore(
                PlacedFeature feature,
                Block block,
                Block deepslate,
                int step,
                int index,
                AttrKeyValue<Boolean> active,
                Color color) {
            this.step = step;
            this.index = index;
            this.active = active;
            this.color = color;
            this.sampleBlock = block;
            this.sampleDeepslateBlock = deepslate;
            int bottom = MinecraftClient.getInstance().world.getBottomY();
            int height = MinecraftClient.getInstance().world.getDimension().logicalHeight();
            this.heightContext = new HeightContext(null, HeightLimitView.create(bottom, height));

            for (PlacementModifier modifier : feature.placementModifiers()) {
                if (modifier instanceof CountPlacementModifier count) {
                    this.count = count.count;

                } else if (modifier instanceof HeightRangePlacementModifier height0) {
                    this.heightProvider = height0.height;

                } else if (modifier instanceof RarityFilterPlacementModifier rare) {
                    this.rarity = rare.chance;
                }
            }

            FeatureConfig featureConfig = feature.feature().value().config();

            if (featureConfig instanceof OreFeatureConfig oreFeatureConfig) {
                this.discardOnAirChance = oreFeatureConfig.discardOnAirChance;
                this.size = oreFeatureConfig.size;
            } else {
                throw new IllegalStateException("config for " + feature + "is not OreFeatureConfig.class");
            }

            if (feature.feature().value().feature() instanceof ScatteredOreFeature) {
                this.scattered = true;
            }
        }
    }
}
