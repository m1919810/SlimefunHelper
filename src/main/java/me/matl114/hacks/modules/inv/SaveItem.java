package me.matl114.hacks.modules.inv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.config.ConfigLoader;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.impl.itemdb.ItemStackData;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SaveItem extends BaseModule {
    private static final String[] SAVE_ITEM_KEY = {"hotkeys", "save-slot-item"};

    public SaveItem() {

    }

    public KeyBindRef keyBind = hotkey(SAVE_ITEM_KEY)
        .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_A, KeyCode.MOUSE_BUTTON_1))
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

    public void ensureLoad(){
        if(!loaded){
            InvTasks.getCustomItemDatabase().getAccess();
        }
    }

    public static final String SAVE_PATH =
        "sfhelper-configs/recipes/saved-items.json";

    public Codec<Map<String, ItemStackData>> mapCodec =
        Codec.list(Codec.STRING).xmap(
        lst -> (Map<String, ItemStackData>)lst.stream().collect(Collectors.toMap(Function.identity(), InvTasks.getCustomItemDatabase()::getDataFromCodecId, (k, v)-> v, LinkedHashMap::new)),
        mp -> mp.keySet().stream().toList()
    ).fieldOf("saved-ids").codec();
    Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();
    boolean dirty = false;
    public void onLoad(){
        try{
            String savedItemIds = ConfigLoader.loadExternalJson(SAVE_PATH);
            JsonElement json = gson.fromJson(savedItemIds, JsonElement.class);
            savedItemDataMap.clear();
            Map<String, ItemStackData> itemDataMap = mapCodec.decode(JsonOps.INSTANCE, json).getOrThrow().getFirst();
            savedItemDataMap.putAll(itemDataMap);
            dirty = false;
        }catch (Throwable e){
            Debug.info(e);
        }
        loaded = true;
    }

    public void onSave(){

        if(dirty){
            dirty = false;
            try{
                JsonElement json = mapCodec.encodeStart(JsonOps.INSTANCE, savedItemDataMap).getOrThrow();
                CompletableFuture.runAsync(()->{
                    String jsonStr = gson.toJson(json);
                    try {
                        ConfigLoader.saveToFile(SAVE_PATH, jsonStr);
                    } catch (IOException e) {
                        Debug.info(e);
                    }
                });
            }catch (Throwable e){
                Debug.info("序列化SavedItems数据失败, 错误:");
                Debug.info(e);
            }
        }
    }

    public void onUnload(){
        loaded = false;
    }

    public boolean saveItem(){
        ClientPlayerEntity player = mc.player;
        if(player==null)return false;

        ItemStack heldItem = ScreenUtils.getSelectingOrHandItem();

        if(heldItem != null && !heldItem.isEmpty()){
            ensureLoad();
            addSaveItem(heldItem);
            return true;
        }else if (heldItem != null){
            Debug.chat(Text.literal("不能保存空物品").formatted(Formatting.RED));
        }
        return false;
    }

    public void addSaveItem(ItemStack item){
        ensureLoad();
        Pair<String, ItemStackData> dataPair = InvTasks.getCustomItemDatabase().getOrRegisterItem(item);
        if(savedItemDataMap.containsKey(dataPair.getFirst())){
            Debug.chat(Text.literal("该物品已经保存过了!").formatted(Formatting.YELLOW));
        }else {
            savedItemDataMap.put(dataPair.getFirst(), dataPair.getSecond());
            Debug.chat(Text.literal("成功保存物品!").formatted(Formatting.GREEN));
        }
    }
    public void removeSavedItem(ItemStack item){
        ensureLoad();
        String id = InvTasks.getCustomItemDatabase().getItemIdOrNull(item);
        if(id != null && savedItemDataMap.remove(id) != null){
            Debug.chat(Text.literal("已经成功移除这个保存物品").formatted(Formatting.GREEN));
        }
    }


    public Map<String, ItemStackData> getSavedItemDataMap(){
        ensureLoad();
        return Collections.unmodifiableMap(savedItemDataMap);
    }


}
