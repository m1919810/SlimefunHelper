package me.matl114.hacks.modules.inv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigLoader;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.itemdb.ItemStackData;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SaveItem extends BaseModule {
    public final ModulePath inventory = makePath(Configs.INV_CONFIG, "inventory");

    public SaveItem() {}

    public KeyBindRef keyBind = hotkey(inventory.add("save-slot-item"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asHandler(this::saveItem))
            .build();

    private boolean loaded = false;
    Map<String, ItemStackData> savedItemDataMap = new LinkedHashMap<>();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(InvTasks.getCustomItemDatabase().getItemDataBaseLoad(), (ev) -> {
            onLoad();
        });
        registerListener(InvTasks.getCustomItemDatabase().getItemDataBaseSave(), (ev) -> {
            onSave();
        });
        registerListener(InvTasks.getCustomItemDatabase().getItemDataBaseUnload(), (ev) -> {
            onUnload();
        });
    }

    public boolean ensureLoad() {
        if (!loaded) {
            return InvTasks.getCustomItemDatabase().checkAccess();
        }
        return true;
    }

    public static final String SAVE_PATH = "sfhelper-configs/recipes/saved-items.json";

    public Codec<Map<String, ItemStackData>> mapCodec = Codec.list(Codec.STRING)
            .xmap(
                    lst -> (Map<String, ItemStackData>) lst.stream()
                            .collect(Collectors.toMap(
                                    Function.identity(),
                                    InvTasks.getCustomItemDatabase()::getDataFromCodecId,
                                    (k, v) -> v,
                                    LinkedHashMap::new)),
                    mp -> mp.keySet().stream().toList())
            .optionalFieldOf("saved-ids", Map.of())
            .codec();
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    boolean dirty = false;

    public void onLoad() {
        try {
            String savedItemIds = ConfigLoader.loadExternalJson(SAVE_PATH);
            JsonElement json = gson.fromJson(savedItemIds, JsonElement.class);
            savedItemDataMap.clear();
            Map<String, ItemStackData> itemDataMap =
                    mapCodec.decode(JsonOps.INSTANCE, json).getOrThrow().getFirst();
            savedItemDataMap.putAll(itemDataMap);
            dirty = false;
        } catch (Throwable e) {
            Debug.info(e);
        }
        loaded = true;
    }

    public void onSave() {

        if (dirty) {
            dirty = false;
            try {
                JsonElement json =
                        mapCodec.encodeStart(JsonOps.INSTANCE, savedItemDataMap).getOrThrow();
                CompletableFuture.runAsync(() -> {
                    String jsonStr = gson.toJson(json);
                    try {
                        ConfigLoader.saveToFile(SAVE_PATH, jsonStr);
                    } catch (IOException e) {
                        Debug.info(e);
                    }
                });
            } catch (Throwable e) {
                Debug.info("序列化SavedItems数据失败, 错误:");
                Debug.info(e);
            }
        }
    }

    public void onUnload() {
        loaded = false;
    }

    public boolean saveItem() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        ItemStack heldItem = ScreenUtils.getSelectingOrHandItem();
        if (ensureLoad()) {
            if (heldItem != null && !heldItem.isEmpty()) {
                addSaveItem(heldItem);
                return true;
            } else if (heldItem != null) {
                Debug.chat(Text.literal("不能保存空物品").formatted(Formatting.RED));
            }
        } else {
            Debug.chat(Text.literal("数据库正在加载,请稍后重试..."));
        }

        return false;
    }

    public void addSaveItem(ItemStack item) {
        ensureLoad();
        Pair<String, ItemStackData> dataPair = InvTasks.getCustomItemDatabase().getOrRegisterItem(item);
        if (savedItemDataMap.containsKey(dataPair.getFirst())) {
            Debug.chat(Text.literal("该物品已经保存过了!").formatted(Formatting.YELLOW));
        } else {
            savedItemDataMap.put(dataPair.getFirst(), dataPair.getSecond());
            dirty = true;
            Debug.chat(Text.literal("成功保存物品!").formatted(Formatting.GREEN));
        }
    }

    public void removeSavedItem(ItemStack item) {
        ensureLoad();
        String id = InvTasks.getCustomItemDatabase().getItemIdOrNull(item);
        if (id != null && savedItemDataMap.remove(id) != null) {
            dirty = true;
            Debug.chat(Text.literal("已经成功移除这个保存物品").formatted(Formatting.GREEN));
        }
    }

    public Map<String, ItemStackData> getSavedItemDataMap() {
        ensureLoad();
        return Collections.unmodifiableMap(savedItemDataMap);
    }
}
