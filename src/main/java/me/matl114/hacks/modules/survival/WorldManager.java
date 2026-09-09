package me.matl114.hacks.modules.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import me.matl114.accessors.interfaces.EntityInventory;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.task.ServerStorage;
import me.matl114.hacks.utils.world.BlockStorage;
import me.matl114.hacks.utils.world.EntityStorage;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.NBTUtils;
import me.matl114.utils.algorithms.SerialExecutor;
import me.matl114.utils.world.BlockLocation;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrialSpawnerBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;

public class WorldManager extends BaseModule {
    public static WorldManager INSTANCE;

    public WorldManager() {
        super("WorldManager");
        INSTANCE = this;
    }

    public ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "world-manager");

    public final FlagRef enableEntityPersistentStorage =
            builder(root.add("enable-entity"), Boolean.class).defaultValue(true).build();

    public final FlagRef enableBlockEntityPersistentStorage = builder(root.add("enable-block-entities"), Boolean.class)
            .defaultValue(true)
            .build();

    public final Map<UUID, EntityStatus> currentEntities = new ConcurrentHashMap<>();

    public final Map<BlockLocation, BlockStatus> currentBlocks = new ConcurrentHashMap<>();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(ServerStorage.getServerStorageLoad(), this::onLoad);
        registerListener(Listener.getPostGameTick(), this::onGameTick);
        registerListener(Listener.getEntityRemoveListener(), this::onEntityDeath);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.VILLAGER), this::onVillagerProfessionUpdate);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(SetTradeOffersS2CPacket.class),
                this::onVillagerTradeUpdate);
        registerListener(
                Listener.getBlockUpdateListener().getChannel(Blocks.TRIAL_SPAWNER), this::onTrialSpawnerStateUpdate);
        registerListener(ServerStorage.getServerStorageSave(), this::onSave);
        registerListener(ServerStorage.getServerStorageLoad(), this::onLoad);
    }

    private final Executor asyncExecutor = new SerialExecutor(CompletableFuture::runAsync);

    public static final String ENTITY_DATA_KEY = "slimefunhelper:world_manager/entity_data_storage";
    public static final String BLOCK_DATA_KEY = "slimefunhelper:world_manager/block_data_storage";

    public static final String KEY_VILLAGER_TRADE = "slimefunhelper:villager/trade_info";
    public static final String KEY_VILLAGER_TRADE_LOCK = "slimefunhelper:trade_lock";
    public static final String KEY_VILLAGER_TRADE_LIST = "slimefunhelper:trade_list";

    public void setVillagerTradeLock(VillagerEntity villager, boolean lock) {
        var status = getStatus(villager, true);
        NbtCompound nbtCompound = status.getDataContainer();
        NbtCompound sub = NBTUtils.ensurePath(nbtCompound, KEY_VILLAGER_TRADE);
        sub.putByte(KEY_VILLAGER_TRADE_LOCK, lock ? (byte) 1 : (byte) 0);
        status.markDirty();
    }

    public boolean isVillagerTradeLock(VillagerEntity villager) {
        var status = getStatus(villager, false);
        return status != null
                && NBTUtils.resolve(status.getDataContainer(), KEY_VILLAGER_TRADE, KEY_VILLAGER_TRADE_LOCK)
                        instanceof NbtByte nbtByte
                && nbtByte.byteValue() == (byte) 1;
    }

    public static boolean canVillagerResetTrade(MerchantScreenHandler handler) {
        return handler.getExperience() == 0 && handler.getLevelProgress() <= 1;
    }

    public void setVillagerTradeList(VillagerEntity villager, List<TradeRecord> trades) {
        var status = getStatus(villager, true);
        NbtCompound nbtCompound = status.getDataContainer();
        NbtCompound sub = NBTUtils.ensurePath(nbtCompound, KEY_VILLAGER_TRADE);
        NBTUtils.putValue(
                sub,
                KEY_VILLAGER_TRADE_LIST,
                trades,
                Codec.list(TradeRecord.CODEC),
                mc.getNetworkHandler().getRegistryManager());
        status.markDirty();
    }

    @Nullable
    public List<TradeRecord> getVillagerTradeList(VillagerEntity villager) {
        var status = getStatus(villager, false);
        return status != null
                        && NBTUtils.resolve(status.getDataContainer(), KEY_VILLAGER_TRADE, KEY_VILLAGER_TRADE_LIST)
                                instanceof NbtList list
                ? NBTUtils.toValue(
                        list,
                        Codec.list(TradeRecord.CODEC),
                        mc.getNetworkHandler().getRegistryManager())
                : null;
    }

    public record TradeRecord(ItemStack result, ItemStack buy1, ItemStack buy2, int buyLimit) {
        public static final Codec<TradeRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        VItem.ITEM_STACK_CODEC.fieldOf("result").forGetter(TradeRecord::result),
                        VItem.ITEM_STACK_CODEC
                                .optionalFieldOf("buy1", ItemStack.EMPTY)
                                .forGetter(TradeRecord::buy1),
                        VItem.ITEM_STACK_CODEC
                                .optionalFieldOf("buy2", ItemStack.EMPTY)
                                .forGetter(TradeRecord::buy2),
                        Codec.INT.fieldOf("buy-limit").forGetter(TradeRecord::buyLimit))
                .apply(instance, TradeRecord::new));
    }

    public void onVillagerTradeUpdate(Event<SetTradeOffersS2CPacket> eventSetTrade) {
        if (eventSetTrade.context.getSyncId() == mc.player.currentScreenHandler.syncId) {
            if (mc.player.currentScreenHandler instanceof EntityInventory<?> inventory
                    && inventory.getOwner() instanceof VillagerEntity villager) {
                asyncExecutor.execute(() -> {
                    var offers = eventSetTrade.context.getOffers();
                    var canRefresh =
                            eventSetTrade.context.getExperience() == 0 && eventSetTrade.context.getLevelProgress() <= 1;
                    setVillagerTradeLock(villager, !canRefresh);
                    List<TradeRecord> trades = offers.stream()
                            .map(offer -> {
                                return new TradeRecord(
                                        offer.getSellItem(),
                                        offer.getFirstBuyItem().itemStack(),
                                        offer.getSecondBuyItem()
                                                .map(TradedItem::itemStack)
                                                .orElse(ItemStack.EMPTY),
                                        offer.getMaxUses());
                            })
                            .toList();
                    setVillagerTradeList(villager, trades);
                });
            }
        }
    }

    public void onVillagerProfessionUpdate(Event<DataTracker.SerializedEntry<?>> eventDataUpdate) {
        if (eventDataUpdate.getArgs(0) instanceof VillagerEntity villager) {
            if (eventDataUpdate.context.id() == VDataFlag.ID_VILLAGER_PROFESSION_DATA
                    && eventDataUpdate.context.value() instanceof VillagerData data) {
                asyncExecutor.execute(() -> {
                    var profession = data.profession().getKey().orElse(null);
                    if (Objects.equals(profession, VillagerProfession.NONE)
                            || Objects.equals(profession, VillagerProfession.NITWIT)) {
                        var status = getStatus(villager, false);
                        if (status != null) {
                            status.getDataContainer().remove(KEY_VILLAGER_TRADE);
                            status.markDirty();
                        }
                    } else {
                        if (data.level() > 1) {
                            setVillagerTradeLock(villager, true);
                        }
                    }
                });
            }
        }
    }

    public static final String KEY_TRIAL_INFO = "slimefunhelper:trial/trial_info";

    public static final String KEY_TRIAL_FINISH_GLOBAL_TIME = "slimefunhelper:trial_cooldown_global_time";

    public static final String KEY_TRIAL_ACTIVE_GLOBAL_TIME = "slimefunhelper:trial_active_global_time";

    public void onTrialSpawnerStateUpdate(Event<BlockUpdate> event) {
        if (event.context.oldState().getBlock() == Blocks.TRIAL_SPAWNER
                && event.context.newState().getBlock() == Blocks.TRIAL_SPAWNER) {
            BlockState oldState = event.context.oldState();
            BlockState newState = event.context.newState();
            BlockPos pos = event.context.pos();
            if (mc.world.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity be) {
                TrialSpawnerState oldAct = oldState.get(TrialSpawnerBlock.TRIAL_SPAWNER_STATE);
                TrialSpawnerState newAct = newState.get(TrialSpawnerBlock.TRIAL_SPAWNER_STATE);
                if (oldAct != newAct) {
                    if (newAct == TrialSpawnerState.COOLDOWN) {
                        var bc = getStatus(be, true);
                        var sub = NBTUtils.ensurePath(bc.getDataContainer(), KEY_TRIAL_INFO);
                        sub.putLong(KEY_TRIAL_FINISH_GLOBAL_TIME, System.currentTimeMillis());
                        bc.markDirty();
                    }
                    if (newAct == TrialSpawnerState.ACTIVE) {
                        var bc = getStatus(be, true);
                        var sub = NBTUtils.ensurePath(bc.getDataContainer(), KEY_TRIAL_INFO);
                        sub.putLong(KEY_TRIAL_ACTIVE_GLOBAL_TIME, System.currentTimeMillis());
                        bc.markDirty();
                    }
                }
            }
        }
    }

    public OptionalLong getTrialSpawnerCooldownStartTime(BlockEntity be) {
        var container = getStatus(be, false);
        if (container == null) {
            return OptionalLong.empty();
        }
        var nbtLong = NBTUtils.resolve(container.getDataContainer(), KEY_TRIAL_INFO, KEY_TRIAL_FINISH_GLOBAL_TIME);
        return nbtLong instanceof NbtLong longValue ? OptionalLong.of(longValue.longValue()) : OptionalLong.empty();
    }

    public OptionalLong getTrialSpawnerActiveStartTime(BlockEntity be) {
        var container = getStatus(be, false);
        if (container == null) {
            return OptionalLong.empty();
        }
        var nbtLong = NBTUtils.resolve(container.getDataContainer(), KEY_TRIAL_INFO, KEY_TRIAL_ACTIVE_GLOBAL_TIME);
        return nbtLong instanceof NbtLong longValue ? OptionalLong.of(longValue.longValue()) : OptionalLong.empty();
    }

    public void onLoad(Event<ServerStorage.Meta> event) {
        ServerStorage.Meta meta = event.context;
        DynamicRegistryManager manager = event.getArgs(1);
        currentEntities.clear();
        currentBlocks.clear();
        for (var storage : meta.allEntityStorages()) {
            if (storage.contains(ENTITY_DATA_KEY)) {
                EntityStatus status = storage.get(ENTITY_DATA_KEY, EntityStatus.CODEC, manager);
                if (status != null) {
                    currentEntities.put(status.getSelf(), status);
                }
            }
        }
        for (var re : meta.toBlockList()) {
            if (re.contains(BLOCK_DATA_KEY)) {
                BlockStatus status = re.get(BLOCK_DATA_KEY, BlockStatus.CODEC, manager);
                if (status != null) {
                    currentBlocks.put(BlockLocation.of(re.getDimension(), re.getPos()), status);
                }
            }
        }
    }

    int timer = 0;

    public void onGameTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
        if (++timer < 10) {
            return;
        }
        timer = 0;
        var iter = currentBlocks.entrySet().iterator();
        while (iter.hasNext()) {
            var r = iter.next();
            var re = r.getKey();
            if (re.isLocationLoaded(mc.world)) {
                BlockPos pos = re.getPos();
                BlockEntity be = mc.world.getBlockEntity(pos);
                if (be != null && be.getType() == r.getValue().getType()) {
                    r.getValue().update(pos, be);
                } else {
                    iter.remove();
                    onRemoveBlock(re);
                }
            }
        }
        var iter2 = currentEntities.entrySet().iterator();
        while (iter2.hasNext()) {
            var re = iter2.next();
            if (mc.world.getEntityLookup().get(re.getKey()) instanceof LivingEntity entity) {
                re.getValue().update(entity);
            }
        }
    }

    public EntityStatus getStatus(LivingEntity entity, boolean create) {
        if (entity.getHealth() > 0) {
            return create
                    ? currentEntities.computeIfAbsent(entity.getUuid(), EntityStatus::new)
                    : currentEntities.get(entity.getUuid());
        } else {
            return null;
        }
    }

    public BlockStatus getStatus(BlockEntity entity, boolean create) {
        BlockLocation bl = BlockLocation.of(entity.getWorld(), entity.getPos());
        return currentBlocks.compute(bl, (k, v) -> {
            if (v == null) {
                return create ? new BlockStatus(entity.getType()) : null;
            } else {
                return v.getType() == entity.getType() ? v : null;
            }
        });
    }

    public void onEntityDeath(Event<Entity> event) {
        if (checkNull()) return;
        Entity entity = event.context;
        if (entity instanceof LivingEntity lv && lv.getHealth() <= 0) {
            currentEntities.remove(entity.getUuid());
            var meta = ServerStorage.getStorage();
            if (meta != null) {
                EntityStorage storage = meta.getEntityStorage(entity.getUuid(), false);
                if (storage != null) {
                    storage.put(ENTITY_DATA_KEY, null);
                }
            }
        }
    }

    private void onRemoveBlock(BlockLocation location) {
        BlockStorage storage = ServerStorage.getStorage().getBlockStorage(location.world(), location.getPos(), false);
        if (storage != null) {
            storage.put(BLOCK_DATA_KEY, null);
            ServerStorage.update(storage, true);
        }
    }

    public void onSave(Event<ServerStorage.Meta> event) {
        DynamicRegistryManager manager = event.getArgs(1);
        ServerStorage.Meta meta = event.context;
        if (enableEntityPersistentStorage.getValue()) {
            for (var entry : currentEntities.entrySet()) {
                EntityStatus status = entry.getValue();
                if (status.isDirty()) {
                    EntityStorage storage = meta.getEntityStorage(entry.getKey(), true);
                    if (!status.isEmpty()) {
                        storage.put(ENTITY_DATA_KEY, status, EntityStatus.CODEC, manager);
                    } else {
                        storage.put(ENTITY_DATA_KEY, null);
                    }
                    status.dirty = false;
                }
            }
        }
        if (enableBlockEntityPersistentStorage.get()) {
            for (var entry : currentBlocks.entrySet()) {
                BlockLocation location = entry.getKey();
                BlockStatus status = entry.getValue();
                if (status.isDirty()) {
                    if (!status.isEmpty()) {
                        BlockStorage storage = meta.getBlockStorage(location.world(), location.getPos(), true);
                        storage.put(BLOCK_DATA_KEY, status, BlockStatus.CODEC, manager);
                    } else {
                        BlockStorage storage = meta.getBlockStorage(location.world(), location.getPos(), false);
                        if (storage != null) {
                            storage.put(BLOCK_DATA_KEY, null);
                            ServerStorage.update(storage, true);
                        }
                    }
                    status.dirty = false;
                }
            }
        }
    }

    @Getter
    public static class EntityStatus {
        final UUID self;
        Optional<UUID> owner = Optional.empty();
        long lastUpdatedMs;
        NbtCompound dataContainer = new NbtCompound();
        boolean dirty = false;
        public static final Codec<EntityStatus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Uuids.INT_STREAM_CODEC.fieldOf("uuid").forGetter(EntityStatus::getSelf),
                        Uuids.INT_STREAM_CODEC.optionalFieldOf("owner").forGetter(EntityStatus::getOwner),
                        Codec.LONG.optionalFieldOf("timestamp", 0L).forGetter(EntityStatus::getLastUpdatedMs),
                        NbtCompound.CODEC
                                .optionalFieldOf("data", new NbtCompound())
                                .forGetter(EntityStatus::getDataContainer))
                .apply(instance, EntityStatus::new));

        @Setter
        public Consumer<LivingEntity> updateCallback;

        public EntityStatus(UUID self, Optional<UUID> owner, long lastUpdatedMs, NbtCompound dataContainer) {
            this.self = self;
            this.owner = owner;
            this.lastUpdatedMs = lastUpdatedMs;
            this.dataContainer = dataContainer.copy();
        }

        public EntityStatus(UUID uid) {
            this.self = uid;
            lastUpdatedMs = System.currentTimeMillis();
        }

        public void markDirty() {
            this.dirty = true;
        }

        public void updateTime() {
            lastUpdatedMs = System.currentTimeMillis();
        }

        public void update(LivingEntity entity) {
            updateTime();
            if (updateCallback != null) {
                updateCallback.accept(entity);
            }
        }

        public boolean isEmpty() {
            return owner.isEmpty() && dataContainer.isEmpty();
        }
    }

    @Getter
    public static class BlockStatus {
        long lastUpdatedMs;
        final BlockEntityType<?> type;
        NbtCompound dataContainer = new NbtCompound();
        boolean dirty = false;

        @Setter
        BiConsumer<BlockPos, BlockEntity> updateCallback;

        public BlockStatus(BlockEntityType<?> type, long lastUpdatedMs, NbtCompound dataContainer) {
            this.type = type;
            this.lastUpdatedMs = lastUpdatedMs;
            this.dataContainer = dataContainer.copy();
        }

        public BlockStatus(BlockEntityType<?> type) {
            this.type = type;
            lastUpdatedMs = System.currentTimeMillis();
        }

        public void markDirty() {
            this.dirty = true;
        }

        public void updateTime() {
            lastUpdatedMs = System.currentTimeMillis();
        }

        public void update(BlockPos pos, BlockEntity blockEntity) {
            updateTime();
            if (updateCallback != null) {
                updateCallback.accept(pos, blockEntity);
            }
        }

        public boolean isEmpty() {
            return dataContainer.isEmpty();
        }

        public static final Codec<BlockStatus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Registries.BLOCK_ENTITY_TYPE
                                .getCodec()
                                .fieldOf("block-type")
                                .forGetter(BlockStatus::getType),
                        Codec.LONG.fieldOf("timestamp").forGetter(BlockStatus::getLastUpdatedMs),
                        NbtCompound.CODEC
                                .optionalFieldOf("data", new NbtCompound())
                                .forGetter(BlockStatus::getDataContainer))
                .apply(instance, BlockStatus::new));
    }
}
