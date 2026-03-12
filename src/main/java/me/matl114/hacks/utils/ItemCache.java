package me.matl114.hacks.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import javax.annotation.Nonnull;
import lombok.Getter;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannel;
import me.matl114.managers.ScheduleService;
import me.matl114.managers.config.ConfigLoader;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.codecs.NullCodec;
import me.matl114.utils.itemdb.ItemStackData;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class ItemCache {
    String fileName;

    @Getter
    volatile boolean loaded;

    private volatile boolean loading = false;
    boolean dirty = false;
    Set<String> activeIds = new LinkedHashSet<>();
    Map<String, ItemStackData> map = new LinkedHashMap<>();
    Map<ItemStackData, Pair<String, ItemStackData>> byItem = new HashMap<>();
    ReentrantLock lock = new ReentrantLock();
    boolean autoGc = false;

    public ItemCache(String saveJsonFile) {
        this.fileName = saveJsonFile;
        // auto save
        ScheduleService.launchAsyncRepeatTask(
                () -> {
                    if (this.loaded) {
                        this.save();
                    }
                },
                1000 * 60,
                1000 * 60 * 5);
        // auto unload
        Listener.getServerDisconnectPoint().registerHandler((ev) -> {
            if (this.loaded) {
                this.unload();
            }
        });

        Listener.getGameJoinPoint().registerHandler((ev) -> {
            if (this.loaded) {
                this.unload();
            }
            // auto getAccess if not
            // 10 sec after you enter the game, run Async
            ScheduleService.launchAsyncDelayedTask(this::getAccess, 1000);
        });
    }
    // may refer to unloaded content sometimes, so load is needed
    public boolean checkAccess() {
        if (this.loaded) {
            return true;
        } else if (this.loading) {
            return false;
        } else {
            try {
                CompletableFuture.runAsync(this::getAccess);
                return false;
            } catch (Exception e) {
                return false;
            }
        }
    }
    // totally async method
    private synchronized ItemCache getAccess() {
        if (this.loaded) {
            return this;
        } else {
            load();
            return this;
        }
    }

    Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static Codec<Map<String, ItemStackData>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, ItemStackData.CODEC);
    public static final String PREFIX = "customitems:";

    public void load() {
        loading = true;
        try {
            try {
                // check the access to registry(),
                ItemStackUtils.registry();
            } catch (Throwable e) {
                throw new IllegalStateException("Illegal access to registry!", e);
            }
            JsonObject jsonObject;
            Debug.info("Start loading item cache");
            long startTime = System.currentTimeMillis();
            lock.lock();
            try {
                if (loaded) {
                    return;
                }
                try {
                    String jsonStr = ConfigLoader.loadExternalJson(this.fileName);
                    jsonObject = gson.fromJson(jsonStr, JsonObject.class);
                } catch (Throwable e) {
                    Debug.info("Error while loading ItemDatabase");
                    Debug.info(e);
                    loaded = false;
                    return;
                }
                map = new LinkedHashMap<>();
                map = new LinkedHashMap<>(MAP_CODEC
                        .decode(JsonOps.INSTANCE, jsonObject)
                        .getOrThrow()
                        .getFirst());
                dirty = false;
                byItem = new HashMap<>();
                map.forEach((key, value) -> {
                    byItem.put(value, Pair.of(key, value));
                });
                activeIds.clear();
                Debug.info("Finish data loading of item cache, using", System.currentTimeMillis() - startTime, "ms");
                try {
                    itemDataBaseLoad.broadcast(null);
                } catch (Throwable e) {
                    Debug.info("Error while loading ItemDatabase");
                    Debug.info(e);
                }

                loaded = true;
            } finally {
                lock.unlock();
            }
        } finally {
            loading = false;
        }
    }

    public void unload() {
        try {
            // prevent unload before load, then activeIds will crash because of this

            if (loaded) {
                lock.lock();
                try {
                    try {
                        itemDataBaseUnload.broadcast(null);
                    } catch (Throwable e) {
                        Debug.info("Error while unloading ItemDatabase");
                        Debug.info(e);
                    }
                    if (autoGc) {
                        gc();
                    }
                } finally {
                    lock.unlock();
                }
            }
            save();
            lock.lock();
            try {
                map = new LinkedHashMap<>();
                byItem = new HashMap<>();
                loaded = false;
            } finally {
                lock.unlock();
            }
        } finally {
            loaded = false;
        }
    }

    public void gc() {
        Iterator<Map.Entry<String, ItemStackData>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ItemStackData> entry = iterator.next();
            if (!activeIds.contains(entry.getKey())) {
                dirty = true;
                iterator.remove();
            }
        }
    }

    public void save() {
        lock.lock();
        try {
            try {
                itemDataBaseSave.broadcast(null);
            } catch (Throwable e) {
                Debug.info("Error while saving ItemDatabase");
                Debug.info(e);
            }
            if (dirty) {
                dirty = false;
                JsonElement jsonElement =
                        MAP_CODEC.encodeStart(JsonOps.INSTANCE, map).getOrThrow();
                // run Async
                CompletableFuture.runAsync(() -> {
                    String jsonStr = gson.toJson(jsonElement);
                    try {
                        ConfigLoader.saveToFile(this.fileName, jsonStr);
                    } catch (IOException e) {
                        Debug.info(e);
                    }
                });
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * during the loaded lifecycle, any reference to the ItemStack must call markForReference, or itemStack will be removed next unload
     * @param id
     */
    public void markForReference(String id) {
        activeIds.add(id);
    }

    private ItemStackData getItem(String id) {
        markForReference(id);
        return map.get(id);
    }

    public void markDirty() {
        dirty = true;
    }

    private Pair<String, ItemStackData> putInternal(String s, ItemStackData item) {
        dirty = true;
        map.put(s, item);
        var pair = Pair.of(s, item);
        byItem.put(item, pair);
        markForReference(s);
        return pair;
    }

    private void removeInternal(String s) {
        dirty = true;
        var re = map.remove(s);
        if (re != null) {
            byItem.remove(re);
        }
    }

    private Pair<String, ItemStackData> registerInternal(ItemStackData stackData) {
        int hashCode = stackData.hashCode();
        String hashString = Integer.toHexString(hashCode);
        String customStringRaw = PREFIX + hashString;
        String customString = customStringRaw;
        int duplicateIndex = 0;
        // find a index which is available
        while (map.containsKey(customString)) {
            duplicateIndex++;
            customString = customStringRaw + "_" + duplicateIndex;
        }
        return putInternal(customString, stackData);
    }

    public Pair<String, ItemStackData> getOrRegisterItem(ItemStackData stackData) {
        Pair<String, ItemStackData> pair = byItem.get(stackData);
        if (pair == null) {
            return registerInternal(
                    stackData instanceof ItemStackData.DataSource dataSource
                            ? dataSource
                            : ItemStackData.wrapAsData(stackData.getItemStack()));
        } else {
            markForReference(pair.getFirst());
            return pair;
        }
    }

    public Pair<String, ItemStackData> getOrRegisterItem(ItemStack stack) {
        ItemStackData stackData = ItemStackData.wrapRaw(stack);
        Pair<String, ItemStackData> pair = byItem.get(stackData);
        if (pair == null) {
            stackData = ItemStackData.wrapAsData(stack);
            return registerInternal(stackData);
        } else {
            markForReference(pair.getFirst());
            return pair;
        }
    }

    public String getItemIdOrNull(ItemStack stack) {
        if (ItemStackUtils.hasInPatch(stack)) {
            var pair = byItem.get(ItemStackData.wrapRaw(stack));
            if (pair == null) {
                return null;
            } else {
                markForReference(pair.getFirst());
                return pair.getFirst();
            }
        } else {
            // use vanilla id for
            Identifier identifier = Registries.ITEM.getId(stack.getItem());
            return identifier == null ? "minecraft:air" : identifier.toString();
        }
    }

    @Nonnull
    public String getOrRegisterCodecId(ItemStackData item) {
        if (item instanceof ItemStackData.Missing missingIndex) {
            return missingIndex.customId();
        } else if (ItemStackUtils.hasInPatch(item.getIcon())) {
            return this.getOrRegisterItem(item).getFirst();
        } else {
            // use vanilla id for
            Identifier identifier = Registries.ITEM.getId(item.getIcon().getItem());
            return identifier == null ? "minecraft:air" : identifier.toString();
        }
    }

    @Nonnull
    public String getOrRegisterCodecId(ItemStack item) {
        if (ItemStackUtils.hasInPatch(item)) {
            return this.getOrRegisterItem(item).getFirst();
        } else {
            // use vanilla id for
            Identifier identifier = Registries.ITEM.getId(item.getItem());
            return identifier == null ? "minecraft:air" : identifier.toString();
        }
    }

    @Nonnull
    public ItemStack getFromCodecId(String id) {
        if (id.startsWith(ItemCache.PREFIX)) {
            var re = this.getItem(id);
            return (re != null && re.isValid()) ? re.getItemStack().copy() : ItemStackData.MISSING;
        } else {
            return new ItemStack(Registries.ITEM.get(Identifier.tryParse(id)));
        }
    }

    @Nonnull
    public ItemStackData getDataFromCodecId(String id) {
        if (id.startsWith(ItemCache.PREFIX)) {
            var re = this.getItem(id);
            return (re != null) ? re : new ItemStackData.Missing(id);
        } else {
            return ItemStackData.wrapRaw(new ItemStack(Registries.ITEM.get(Identifier.tryParse(id))));
        }
    }

    public Codec<ItemStackData> createStackDataCodec() {
        return Codec.STRING.xmap(this::getDataFromCodecId, this::getOrRegisterCodecId);
    }

    public Codec<ItemStack> createStackSampleCodec() {
        return Codec.STRING.xmap(this::getFromCodecId, this::getOrRegisterCodecId);
    }

    public Codec<ItemStack> createStackCodec() {
        Codec<ItemStack> itemStackSampleCodec = createStackSampleCodec();
        Codec<ItemStack> stackCodec = RecordCodecBuilder.create(instance -> instance.group(
                        itemStackSampleCodec.fieldOf("typeid").forGetter(Function.identity()),
                        Codec.INT.fieldOf("amount").forGetter(ItemStack::getCount))
                .apply(instance, ItemStack::copyWithCount));
        return new NullCodec<>(stackCodec, ItemStack::isEmpty, ItemStack.EMPTY);
    }

    @Getter
    @Broadcast
    public final EventChannel<Void> itemDataBaseLoad = new EventChannel<>();

    @Getter
    @Broadcast
    public final EventChannel<Void> itemDataBaseSave = new EventChannel<>();

    @Getter
    @Broadcast
    public final EventChannel<Void> itemDataBaseUnload = new EventChannel<>();
}
