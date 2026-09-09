package me.matl114.hacks.modules.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import lombok.With;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.hacks.WorldTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.extra.EventNotify;
import me.matl114.hacks.modules.task.ServerStorage;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.world.ChunkStorage;
import me.matl114.hooks.XaeroHooks;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypoint;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointAccess;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointFactory;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.MathUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;

public class SearchLabel extends BaseModule {
    public final ModulePath travellingControl = makePath(Configs.SURVIVAL_CONFIG, "travelling-control");
    public final ModulePath searchControl = travellingControl.add("search-label");

    public SearchLabel() {
        super("SearchLabel");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(searchControl.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(
                    searchControl.addHotkey(), new MultiKeyBind(), searchControl.addEnable())
            .build();

    public final FlagRef labelImportantBlocks =
            flagBuilder(searchControl.add("enable-important-blocks")).build();

    public final NBTRef<EntrySet<Block>> importantBlocks = builder(
                    searchControl.add("important-blocks"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(Registries.BLOCK, List.of(Blocks.CHEST, Blocks.SHULKER_BOX)))
            .build();

    public final IntRef importantBlocksCount =
            intBuilder(searchControl.add("block-counts")).defaultValue(8).build();

    public final NBTRef<EntrySet<Block>> instantBlocks = builder(
                    searchControl.add("instant-blocks"), EntrySet.<Block>parameter())
            .defaultValue(
                    new EntrySet<>(Registries.BLOCK, List.of(Blocks.SHULKER_BOX, Blocks.CRAFTER, Blocks.ENDER_CHEST)))
            .build();

    public final FlagRef labelImportantItems =
            flagBuilder(searchControl.add("enable-important-items")).build();

    public final NBTRef<EntrySet<Item>> importantItems = builder(
                    searchControl.add("important-items"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.ELYTRA, Items.FILLED_MAP, Items.SHULKER_BOX)))
            .build();

    public final IntRef importantItemsCount =
            intBuilder(searchControl.add("item-counts")).defaultValue(1).build();

    public final FlagRef labelImportantEntities =
            flagBuilder(searchControl.add("enable-important-entities")).build();

    public final NBTRef<EntrySet<EntityType<?>>> importantEntities = builder(
                    searchControl.add("important-entities"), EntrySet.<EntityType<?>>parameter())
            .defaultValue(new EntrySet<>(
                    Registries.ENTITY_TYPE,
                    List.of(EntityType.PLAYER, EntityType.CHEST_MINECART, EntityType.HOPPER_MINECART)))
            .build();

    public final IntRef importantEntitiesCount =
            intBuilder(searchControl.add("entity-counts")).defaultValue(4).build();

    public final NBTRef<EntrySet<EntityType<?>>> instantEntities = builder(
                    searchControl.add("instant-entities"), EntrySet.<EntityType<?>>parameter())
            .defaultValue(
                    new EntrySet<>(Registries.ENTITY_TYPE, List.of(EntityType.MINECART, EntityType.HOPPER_MINECART)))
            .build();

    public final FlagRef labelInWorldMap = builder(searchControl.add("label-in-world-map"), Boolean.class)
            .defaultValue(true)
            .updateListener(this::updateWorldMapSettings)
            .build();

    public final FlagRef notify =
            flagBuilder(searchControl.add("notify-when-update-label")).build();

    public final FlagRef log =
            flagBuilder(searchControl.add("log-when-update-label")).build();

    public final FlagRef ignoreStructures =
            flagBuilder(searchControl.add("ignore-structures")).build();

    public final FlagRef removeIfClose =
            flagBuilder(searchControl.add("remove-if-close-to-target")).build();

    private static final String KEY_CHUNK_LABEL_RECORD = "slimefunhelper:search_label_chunk_records";

    private void updateWorldMapSettings(boolean show) {
        if (checkNull()) return;
        if (currentAccess == null) return;
        if (show) {
            loadCurrentData(currentAccess);
        } else {
            unloadCurrentData(currentAccess);
        }
    }

    private void setupNewAccess(IXWaypointAccess access) {
        destroyCurrentAccess();
        currentWaypoints.clear();
        currentAccess = access;
        loadCurrentData(currentAccess);
    }

    private void destroyCurrentAccess() {
        if (currentAccess != null) {
            unloadCurrentData(currentAccess);
        }
        currentAccess = null;
    }

    private void updateCurrentAccess() {
        var factory = XaeroHooks.getInstance().getWaypointFactory();
        if (factory == null) {
            destroyCurrentAccess();
            return;
        }
        var access = factory.getCurrentWaypointSet();
        if (!Objects.equals(access, currentAccess)) {
            if (access != null) {
                setupNewAccess(access);
            } else {
                destroyCurrentAccess();
            }
        }
    }

    private void loadCurrentData(IXWaypointAccess access) {
        currentWaypoints.clear();
        var currentWorldMap = ServerStorage.getStorage().chunkStorageMap.get(mc.world.getRegistryKey());
        if (currentWorldMap == null) {
            return;
        }
        IXWaypointFactory factory = XaeroHooks.getInstance().getWaypointFactory();
        if (factory == null) {
            return;
        }
        for (var re : currentWorldMap.entrySet()) {
            ChunkPos pos = re.getKey();
            ChunkStorage storage = re.getValue();
            ChunkRecord record = storage.get(KEY_CHUNK_LABEL_RECORD, ChunkRecord.CODEC);
            if (record != null && !record.isEmpty()) {
                if (removeIfClose.get() && record.reached()) {
                    continue;
                }
                IXWaypoint newWaypoint = createWaypoint(pos, record, factory);
                addTo(newWaypoint, access);
            }
        }
    }

    private String createLabel(ChunkRecord record) {
        StringBuilder name = new StringBuilder("Label:\n");
        record.typeBlock.ifPresent(block -> name.append("Block:")
                .append(RegistryDisplays.getDisplay(block).getString())
                .append("\n"));
        record.typeItem.ifPresent(block -> name.append("Item:")
                .append(RegistryDisplays.getDisplay(block).getString())
                .append("\n"));
        record.typeEntities.ifPresent(block -> name.append("Entity:")
                .append(RegistryDisplays.getDisplay(block).getString())
                .append("\n"));
        return name.toString();
    }

    private IXWaypoint createWaypoint(ChunkPos pos, ChunkRecord record, IXWaypointFactory factory) {

        int color;
        if (record.typeEntities.isPresent()) {
            color = Formatting.RED.ordinal();
        } else if (record.typeItem.isPresent()) {
            color = Formatting.YELLOW.ordinal();
        } else {
            color = Formatting.GREEN.ordinal();
        }
        double scale = mc.world.getDimension().coordinateScale();

        return factory.createWaypoint(
                (int) (pos.getCenterX() * scale),
                64,
                (int) (pos.getCenterZ() * scale),
                createLabel(record),
                "L",
                color,
                0,
                true,
                true);
    }

    private void addTo(IXWaypoint newWaypoint, IXWaypointAccess access) {
        access.removeIf(s -> {
            return s.getX() == newWaypoint.getX()
                    && s.getY() == newWaypoint.getY()
                    && s.getZ() == newWaypoint.getZ()
                    && s.isTemp() == newWaypoint.isTemp()
                    && Objects.equals(s.getInitials(), newWaypoint.getInitials());
        });
        access.add(newWaypoint);
        access.requestRefresh();
        currentWaypoints.add(newWaypoint);
    }

    private void remove(IXWaypointAccess access, ChunkPos pos) {
        double scale = mc.world.getDimension().coordinateScale();
        access.removeIf(s -> {
            return s.getX() == (int) (pos.getCenterX() * scale)
                    && s.getY() == 64
                    && s.getZ() == (int) (pos.getCenterZ() * scale)
                    && s.isTemp()
                    && Objects.equals(s.getInitials(), "L");
        });
        access.requestRefresh();
    }

    private void update(ChunkPos pos, ChunkRecord record) {
        if (record.isEmpty()) {
            ChunkStorage storage = ServerStorage.getChunkStorage(pos);
            if (storage != null) {
                storage.put(KEY_CHUNK_LABEL_RECORD, null);
            }
            if (currentAccess != null) {
                remove(currentAccess, pos);
            }
        } else {
            ChunkStorage storage = ServerStorage.getOrCreateChunkStorage(pos);
            storage.put(KEY_CHUNK_LABEL_RECORD, record, ChunkRecord.CODEC);
            if (labelInWorldMap.get() && currentAccess != null) {
                var factory = XaeroHooks.getInstance().getWaypointFactory();
                if (factory == null) {
                    return;
                }
                addTo(createWaypoint(pos, record, factory), currentAccess);
            }
            if (notify.get()) {
                EventNotify.INSTANCE.notify("[SlimefunHelper]自动标点", createLabel(record));
            }
            if (log.get()) {
                logI18N("message.module.search-label.label-update", createLabel(record));
            }
        }
    }

    private void markReached(ChunkPos pos) {
        ChunkStorage storage = ServerStorage.getChunkStorage(pos);
        if (storage == null || storage.get(KEY_CHUNK_LABEL_RECORD) == null) {
            return;
        }
        ChunkRecord record = storage.get(KEY_CHUNK_LABEL_RECORD, ChunkRecord.CODEC);
        if (record.reached()) return;
        if (record.isEmpty()) {
            storage.put(KEY_CHUNK_LABEL_RECORD, null);
            if (currentAccess != null) {
                remove(currentAccess, pos);
            }
        } else {
            storage.put(KEY_CHUNK_LABEL_RECORD, record.withReached(true), ChunkRecord.CODEC);
            if (removeIfClose.get()) {
                remove(currentAccess, pos);
            }
        }
    }

    private void unloadCurrentData(IXWaypointAccess access) {
        for (var re : currentWaypoints) {
            access.remove(re);
        }
        currentWaypoints.clear();
    }

    IXWaypointAccess currentAccess;
    final Set<IXWaypoint> currentWaypoints = new HashSet<>();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onPostTick);
        registerListener(Listener.getServerEntitySpawnListener(), this::onEntitySpawn);
        registerListener(Listener.getChunkUpdateListener(), this::onChunkPostLoad);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.ITEM), this::handleItemEntityItemData);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.ITEM_FRAME), this::handleItemFrameItemData);
    }

    ChunkPos lastChunkPos = null;

    public void onPostTick(Event<ClientPlayerEntity> eventUpdate) {
        if (enable.get() && labelInWorldMap.get() && XaeroHooks.getInstance().getWaypointFactory() != null) {
            updateCurrentAccess();
        } else {
            destroyCurrentAccess();
        }
        if (enable.get()) {
            if (!Objects.equals(lastChunkPos, mc.player.getChunkPos())) {
                if (mc.player.isOnGround()) {
                    lastChunkPos = mc.player.getChunkPos();
                    markReached(mc.player.getChunkPos());
                }
            }
        }
    }

    public void onEntitySpawn(Event<Entity> entityEvent) {
        if (enable.get()
                && labelImportantEntities.get()
                && entityEvent.context.squaredDistanceTo(mc.player.getPos()) > 256) {
            ChunkPos pos = entityEvent.context.getChunkPos();
            ChunkStorage storage = ServerStorage.getChunkStorage(pos);
            ChunkRecord record =
                    storage == null ? ChunkRecord.EMPTY : storage.get(KEY_CHUNK_LABEL_RECORD, ChunkRecord.CODEC);
            record = record == null ? ChunkRecord.EMPTY : record;
            ChunkRecord oldRecord = record;
            if (!oldRecord.reached() && oldRecord.typeEntities().isEmpty()) {
                if (instantEntities.get().test(entityEvent.context.getType())) {
                    record = record.withTypeEntities(Optional.of(entityEvent.context.getType()));
                } else if (importantEntities.get().test(entityEvent.context.getType())) {
                    // check all entity in chunk
                    if (1 >= importantEntitiesCount.get()
                            || (int) collectCurrentChunk(pos).stream()
                                                    .filter(s -> importantEntities
                                                            .get()
                                                            .test(s.getType()))
                                                    .count()
                                            + 1
                                    >= importantEntitiesCount.get()) {
                        record = record.withTypeEntities(Optional.of(entityEvent.context.getType()));
                    }
                }
            }
            if (!Objects.equals(record, oldRecord)) {
                update(pos, record);
            }
        }
    }

    public void handleItemEntityItemData(Event<DataTracker.SerializedEntry<?>> entryUpdateEvent) {
        if (enable.get() && labelImportantItems.get()) {
            var entry = entryUpdateEvent.context();
            if (entry.id() == VDataFlag.ID_ITEM_ITEMSTACK
                    && (entry.value()) instanceof ItemStack stack
                    && entryUpdateEvent.getArgs(0) instanceof ItemEntity item) {
                onItemEntity(item, stack);
            }
        }
    }

    public void handleItemFrameItemData(Event<DataTracker.SerializedEntry<?>> entryUpdateEvent) {
        if (enable.get() && labelImportantItems.get()) {
            var entry = entryUpdateEvent.context();
            if (entry.id() == VDataFlag.ID_ITEM_FRAME_ITEMSTACK
                    && entry.value() instanceof ItemStack stack
                    && entryUpdateEvent.getArgs(0) instanceof ItemFrameEntity item) {
                onItemEntity(item, stack);
            }
        }
    }

    public void onItemEntity(Entity entity, ItemStack itemStack) {
        if (!itemStack.isEmpty()
                && importantItems.get().test(itemStack.getItem())
                && entity.getPos().squaredDistanceTo(mc.player.getPos()) > 256) {
            ChunkPos pos = entity.getChunkPos();
            ChunkStorage storage = ServerStorage.getChunkStorage(pos);
            ChunkRecord oldRecord =
                    storage == null ? ChunkRecord.EMPTY : storage.get(KEY_CHUNK_LABEL_RECORD, ChunkRecord.CODEC);
            oldRecord = oldRecord == null ? ChunkRecord.EMPTY : oldRecord;
            if (!oldRecord.reached() && oldRecord.typeItem().isEmpty()) {
                if (1 >= importantItemsCount.get()
                        || 1
                                        + (int) collectCurrentChunk(entity.getChunkPos()).stream()
                                                .filter(s -> {
                                                    if (s instanceof ItemEntity item
                                                            && !item.getStack().isEmpty()) {
                                                        return importantItems
                                                                .get()
                                                                .test(item.getStack()
                                                                        .getItem());
                                                    } else if (s instanceof ItemFrameEntity frame
                                                            && !frame.getHeldItemStack()
                                                                    .isEmpty()) {
                                                        return importantItems
                                                                .get()
                                                                .test(frame.getHeldItemStack()
                                                                        .getItem());
                                                    } else {
                                                        return false;
                                                    }
                                                })
                                                .count()
                                >= importantItemsCount.get()) {
                    oldRecord = oldRecord.withTypeItem(Optional.of(itemStack.getItem()));
                    update(pos, oldRecord);
                }
            }
        }
    }

    public void onChunkPostLoad(Event<ChunkPos> eventUpdate) {
        if (enable.get() && labelImportantBlocks.get()) {
            ChunkPos chunkPos = eventUpdate.context;
            Tasks.scheduleDelayed(
                    () -> {
                        WorldTasks.scheduleChunkTask(
                                chunkPos,
                                () -> {
                                    ChunkStorage storage = ServerStorage.getChunkStorage(chunkPos);
                                    ChunkRecord oldRecord = storage == null
                                            ? ChunkRecord.EMPTY
                                            : storage.get(KEY_CHUNK_LABEL_RECORD, ChunkRecord.CODEC);
                                    oldRecord = oldRecord == null ? ChunkRecord.EMPTY : oldRecord;
                                    if (oldRecord.reached()) {
                                        return;
                                    }
                                    ChunkRecord newRecord = oldRecord;
                                    Map<BlockPos, BlockState> allStates = null;
                                    find_block:
                                    {
                                        if (allStates == null) {
                                            allStates = WorldUtils.scannChunk(
                                                    mc.world.getChunk(chunkPos.x, chunkPos.z), (bp, bs) -> !bs.isAir());
                                        }
                                        Map<BlockPos, BlockState> filtered = new HashMap<>();
                                        for (var re : allStates.entrySet()) {
                                            if (instantBlocks
                                                    .get()
                                                    .test(re.getValue().getBlock())) {
                                                newRecord = newRecord.withTypeBlock(Optional.of(
                                                        re.getValue().getBlock()));
                                                break find_block;
                                            }
                                            if (importantBlocks
                                                    .get()
                                                    .test(re.getValue().getBlock())) {
                                                filtered.put(re.getKey(), re.getValue());
                                            }
                                        }
                                        if (ignoreStructures.get()) {
                                            filtered = filterStructureSpecialBlocks(filtered, allStates);
                                            if (!filtered.isEmpty() && filtered.size() >= importantBlocksCount.get()) {
                                                newRecord =
                                                        newRecord.withTypeBlock(Optional.of(filtered.values().stream()
                                                                .findFirst()
                                                                .orElseThrow()
                                                                .getBlock()));
                                            } else {
                                                newRecord = newRecord.withTypeBlock(Optional.empty());
                                            }
                                        } else {
                                            var scannResult =
                                                    filtered.values().stream().toList();
                                            if (!scannResult.isEmpty()
                                                    && scannResult.size() >= importantBlocksCount.get()) {
                                                newRecord = newRecord.withTypeBlock(Optional.of(
                                                        scannResult.get(0).getBlock()));
                                            } else {
                                                newRecord = newRecord.withTypeBlock(Optional.empty());
                                            }
                                        }
                                    }

                                    if (!Objects.equals(newRecord, oldRecord)) {
                                        update(chunkPos, newRecord);
                                    }
                                },
                                true);
                    },
                    2);
        }
    }

    private Set<Entity> collectCurrentChunk(ChunkPos chunkPos) {
        Set<Entity> currentChunkEntities = new HashSet<>();
        for (var re : mc.world.getEntities()) {
            if (MathUtils.intersectsXZ(
                    re.getBoundingBox(),
                    chunkPos.getStartX(),
                    chunkPos.getStartZ(),
                    chunkPos.getStartX() + 16,
                    chunkPos.getStartZ() + 16)) {
                currentChunkEntities.add(re);
            }
        }
        return currentChunkEntities;
    }

    private static final BlockState DEFAULT_STATE = Blocks.AIR.getDefaultState();

    private static final Set<Block> OVERWORLD_STRUCTURE_BLOCKS = Set.of(
            Blocks.MOSSY_COBBLESTONE,
            Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS,
            Blocks.SANDSTONE,
            Blocks.CUT_SANDSTONE,
            Blocks.CHISELED_SANDSTONE,
            Blocks.PRISMARINE,
            Blocks.PRISMARINE_BRICKS,
            Blocks.DARK_PRISMARINE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.CHISELED_DEEPSLATE,
            Blocks.TUFF,
            Blocks.POLISHED_TUFF,
            Blocks.TUFF_BRICKS,
            Blocks.CHISELED_TUFF,
            Blocks.CHISELED_TUFF_BRICKS,
            Blocks.TERRACOTTA,
            Blocks.WHITE_TERRACOTTA,
            Blocks.ORANGE_TERRACOTTA,
            Blocks.BLUE_TERRACOTTA,
            Blocks.LIGHT_BLUE_TERRACOTTA,
            Blocks.RED_TERRACOTTA,
            Blocks.YELLOW_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA,
            Blocks.GRAY_TERRACOTTA,
            Blocks.GREEN_TERRACOTTA,
            Blocks.BLACK_TERRACOTTA,
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN);

    private static final Set<Block> DESERT_PYRAMID_BLOCKS =
            Set.of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE);

    private static final Set<Block> JUNGLE_TEMPLE_BLOCKS = Set.of(Blocks.MOSSY_COBBLESTONE);

    private static final Set<Block> OCEAN_STRUCTURE_BLOCKS =
            Set.of(Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE);

    private static final Set<Block> UNDERGROUND_STRUCTURE_BLOCKS = Set.of(
            Blocks.MOSSY_COBBLESTONE,
            Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.CHISELED_DEEPSLATE,
            Blocks.TUFF,
            Blocks.POLISHED_TUFF,
            Blocks.TUFF_BRICKS,
            Blocks.CHISELED_TUFF,
            Blocks.CHISELED_TUFF_BRICKS);

    private static final Set<Block> TRIAL_CHAMBER_BLOCKS = Set.of(
            Blocks.TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF, Blocks.CHISELED_TUFF_BRICKS);

    private static final Set<Block> ANCIENT_CITY_BLOCKS = Set.of(
            Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE);

    private static final Set<Block> PILLAGER_OUTPOST_BLOCKS = Set.of(Blocks.DARK_OAK_LOG);

    private static final Set<Block> WOODLAND_MANSION_BLOCKS = Set.of(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD);

    private static final Set<Block> SHIPWRECK_BLOCKS = Set.of(Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG);

    private static final Set<Block> VILLAGE_BLOCKS = Set.of(Blocks.BELL, Blocks.HAY_BLOCK);

    private static final Set<Block> SWAMP_HUT_BLOCKS = Set.of(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS);

    private static final Set<Block> IGLOO_BLOCKS = Set.of(Blocks.SNOW_BLOCK, Blocks.SNOW);

    private static final Set<Block> BURIED_TREASURE_BLOCKS = Set.of();

    private static final Set<Block> NETHER_FORTRESS_BLOCKS = Set.of(
            Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_STAIRS);

    private static final Set<Block> BASTION_BLOCKS = Set.of(
            Blocks.BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Blocks.CHISELED_POLISHED_BLACKSTONE);

    private static final Set<Block> NETHER_RUINED_PORTAL_BLOCKS = Set.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);

    private static final Set<Block> END_CITY_BLOCKS = Set.of(
            Blocks.PURPUR_BLOCK,
            Blocks.PURPUR_PILLAR,
            Blocks.PURPUR_STAIRS,
            Blocks.PURPUR_SLAB,
            Blocks.END_STONE_BRICKS);

    private Map<BlockPos, BlockState> filterStructureSpecialBlocks(
            Map<BlockPos, BlockState> state, Map<BlockPos, BlockState> allBlocks) {
        Map<BlockPos, BlockState> filteredValues = new HashMap<>();

        for (Map.Entry<BlockPos, BlockState> entry : state.entrySet()) {
            BlockPos pos = entry.getKey();
            Block block = entry.getValue().getBlock();

            if (block != Blocks.CHEST && block != Blocks.BARREL) {
                continue;
            }

            Block above = allBlocks.getOrDefault(pos.up(), DEFAULT_STATE).getBlock();

            Block below = allBlocks.getOrDefault(pos.down(), DEFAULT_STATE).getBlock();

            if (mc.world.getDimension().hasCeiling()) {
                if (isNetherStructureContainer(pos, above, below)) {
                    filteredValues.put(pos, entry.getValue());
                }
                continue;
            }

            if (!mc.world.getDimension().hasSkyLight()) {
                if (isEndStructureContainer(pos, above, below)) {
                    filteredValues.put(pos, entry.getValue());
                }
                continue;
            }

            if (isOverworldStructureContainer(pos, above, below)) {
                filteredValues.put(pos, entry.getValue());
            }
        }

        return filteredValues;
    }

    private boolean isOverworldStructureContainer(BlockPos pos, Block above, Block below) {
        RegistryEntry<Biome> biome = mc.world.getBiome(pos);

        if (biome.isIn(BiomeTags.DESERT_PYRAMID_HAS_STRUCTURE) && contains(DESERT_PYRAMID_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.JUNGLE_TEMPLE_HAS_STRUCTURE) && contains(JUNGLE_TEMPLE_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.OCEAN_MONUMENT_HAS_STRUCTURE) && contains(OCEAN_STRUCTURE_BLOCKS, above, below)) {
            return true;
        }

        if ((biome.isIn(BiomeTags.OCEAN_RUIN_COLD_HAS_STRUCTURE) || biome.isIn(BiomeTags.OCEAN_RUIN_WARM_HAS_STRUCTURE))
                && contains(OCEAN_STRUCTURE_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.STRONGHOLD_HAS_STRUCTURE) && contains(UNDERGROUND_STRUCTURE_BLOCKS, above, below)) {
            return true;
        }

        if ((biome.isIn(BiomeTags.MINESHAFT_HAS_STRUCTURE) || biome.isIn(BiomeTags.MINESHAFT_MESA_HAS_STRUCTURE))
                && contains(UNDERGROUND_STRUCTURE_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.ANCIENT_CITY_HAS_STRUCTURE) && contains(ANCIENT_CITY_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.TRIAL_CHAMBERS_HAS_STRUCTURE) && contains(TRIAL_CHAMBER_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.PILLAGER_OUTPOST_HAS_STRUCTURE) && contains(PILLAGER_OUTPOST_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.WOODLAND_MANSION_HAS_STRUCTURE) && contains(WOODLAND_MANSION_BLOCKS, above, below)) {
            return true;
        }

        if ((biome.isIn(BiomeTags.SHIPWRECK_HAS_STRUCTURE) || biome.isIn(BiomeTags.SHIPWRECK_BEACHED_HAS_STRUCTURE))
                && contains(SHIPWRECK_BLOCKS, above, below)) {
            return true;
        }

        if ((biome.isIn(BiomeTags.VILLAGE_DESERT_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.VILLAGE_PLAINS_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.VILLAGE_SAVANNA_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.VILLAGE_SNOWY_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.VILLAGE_TAIGA_HAS_STRUCTURE))
                && contains(VILLAGE_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.SWAMP_HUT_HAS_STRUCTURE) && contains(SWAMP_HUT_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.IGLOO_HAS_STRUCTURE) && contains(IGLOO_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.BURIED_TREASURE_HAS_STRUCTURE) && contains(BURIED_TREASURE_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.TRAIL_RUINS_HAS_STRUCTURE) && contains(OVERWORLD_STRUCTURE_BLOCKS, above, below)) {
            return true;
        }

        if ((biome.isIn(BiomeTags.RUINED_PORTAL_DESERT_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.RUINED_PORTAL_JUNGLE_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.RUINED_PORTAL_OCEAN_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.RUINED_PORTAL_SWAMP_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.RUINED_PORTAL_MOUNTAIN_HAS_STRUCTURE)
                        || biome.isIn(BiomeTags.RUINED_PORTAL_STANDARD_HAS_STRUCTURE))
                && contains(Set.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN), above, below)) {
            return true;
        }

        return pos.getY() < 50 && contains(UNDERGROUND_STRUCTURE_BLOCKS, above, below);
    }

    private boolean isNetherStructureContainer(BlockPos pos, Block above, Block below) {
        RegistryEntry<Biome> biome = mc.world.getBiome(pos);

        if (biome.isIn(BiomeTags.NETHER_FORTRESS_HAS_STRUCTURE) && contains(NETHER_FORTRESS_BLOCKS, above, below)) {
            return true;
        }

        if (biome.isIn(BiomeTags.BASTION_REMNANT_HAS_STRUCTURE) && contains(BASTION_BLOCKS, above, below)) {
            return true;
        }

        return biome.isIn(BiomeTags.RUINED_PORTAL_NETHER_HAS_STRUCTURE)
                && contains(NETHER_RUINED_PORTAL_BLOCKS, above, below);
    }

    private boolean isEndStructureContainer(BlockPos pos, Block above, Block below) {
        RegistryEntry<Biome> biome = mc.world.getBiome(pos);

        return biome.isIn(BiomeTags.END_CITY_HAS_STRUCTURE) && contains(END_CITY_BLOCKS, above, below);
    }

    private boolean contains(Set<Block> blocks, Block above, Block below) {
        return blocks.contains(above) || blocks.contains(below);
    }

    private static final Set<EntityType<?>> SPECIAL_NATURAL_GEN_TYPES = Set.of();

    private List<Entity> filterStructureSpecialEntities(List<Entity> entities) {
        return entities;
    }

    @With
    public record ChunkRecord(
            Optional<Block> typeBlock, Optional<Item> typeItem, Optional<EntityType<?>> typeEntities, boolean reached) {
        public static final ChunkRecord EMPTY =
                new ChunkRecord(Optional.empty(), Optional.empty(), Optional.empty(), false);
        public static final Codec<ChunkRecord> CODEC = RecordCodecBuilder.create(oinstance -> oinstance
                .group(
                        Registries.BLOCK
                                .getCodec()
                                .optionalFieldOf("type-block")
                                .forGetter(ChunkRecord::typeBlock),
                        Registries.ITEM.getCodec().optionalFieldOf("item-type").forGetter(ChunkRecord::typeItem),
                        Registries.ENTITY_TYPE
                                .getCodec()
                                .optionalFieldOf("entity-type")
                                .forGetter(ChunkRecord::typeEntities),
                        Codec.BOOL.optionalFieldOf("reached", false).forGetter(ChunkRecord::reached))
                .apply(oinstance, ChunkRecord::new));

        public boolean isEmpty() {
            return typeBlock.isEmpty() && typeItem.isEmpty() && typeEntities.isEmpty();
        }
    }
}
