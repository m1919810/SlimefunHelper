package me.matl114.hackUtils;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.matl114.listenerUtils.Listener;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public class RecipeTasks {
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static Map<Item, Map<Identifier,RecipeRecord>> CACHE_BY_ITEM;
    private static Map<Identifier,RecipeRecord> CACHE;
    private static RecipeManager INSTANCE ;
    private static DynamicRegistryManager REGISTRY;
    public static final Map EMPTY = Map.of();
    public static final Map<String, RecipeType> TYPE_MAP = new HashMap<>();


    public static RecipeType getById(String id){
        init();
        if(TYPE_MAP .isEmpty()){
            Registries.RECIPE_TYPE.stream().forEach(rt->TYPE_MAP.put(Registries.RECIPE_TYPE.getId(rt).toString(),rt));
        }
        return TYPE_MAP.get(id);
    }

    public static Map<Identifier, RecipeRecord> getRecipeByOutput(Item item){
        init();
        return CACHE_BY_ITEM.getOrDefault(item, EMPTY);
    }
    public static List<RecipeEntry> getRecipeByType(RecipeType type){
        init();
        return INSTANCE.listAllOfType(type);
    }
    public static Map<Identifier, RecipeRecord> getAllRecipe(){
        init();
        return CACHE;
    }
    private static void init(){
        if(INSTANCE == null || CACHE == null || CACHE.isEmpty()){
            resetCache();
            INSTANCE = Objects.requireNonNull(mc.getNetworkHandler()).getRecipeManager();
            REGISTRY = Objects.requireNonNull(mc.world).getRegistryManager();
            CACHE = new Object2ReferenceOpenHashMap<>();
            for (RecipeType<?> types : Registries.RECIPE_TYPE){
                var rcps = (List<RecipeEntry>)(Object)(INSTANCE.listAllOfType((RecipeType) types));
                for (RecipeEntry rcp: rcps){
                    CACHE.put(rcp.id(), RecipeRecord.of(rcp, types));
                }
            }
            CACHE_BY_ITEM = new Reference2ReferenceOpenHashMap<>();
            for (var entry: CACHE.entrySet()){
                CACHE_BY_ITEM.computeIfAbsent(entry.getValue().output().getItem(),(i)-> new Object2ReferenceOpenHashMap<>()).put(entry.getKey(), entry.getValue());
            }
        }
    }
    private static void resetCache(){
        INSTANCE = null;
        if(CACHE != null){
            CACHE.clear();
        }
        if(CACHE_BY_ITEM != null){
            CACHE_BY_ITEM.clear();
        }
        CACHE = null;
    }
    public static record RecipeRecord(Identifier identifier, Recipe<?> instance, RecipeType<?> type, ItemStack output, Ingredient[] ingredients) implements SlimefunTasks.RecipeEntry {
        public static RecipeRecord of(RecipeEntry<?> instance, RecipeType type){
            return new RecipeRecord(instance.id(), (Recipe<?>) (Object)instance.value(), type, instance.value().getResult( mc.world.getRegistryManager()), SlimefunTasks.transfer3x3RecipeDisplay(instance.value(), instance.value().getIngredients().toArray(Ingredient[]::new)));
        }
        @Override
        public String rid() {
            return Registries.RECIPE_TYPE.getId(type).toString();
        }

        @Override
        public String id() {
            return identifier.toString();
        }

        @Override
        public Ingredient[] ingredient() {
            return ingredients;
        }

        @Override
        public boolean containingIdAsIngredient(String id) {
            String[] ab = id.split(":");
            if(ab.length >=2){
                Identifier identifier1 = new Identifier(ab[0], ab[1]);
                ItemStack item = new ItemStack(Registries.ITEM.get(identifier1));
                for (var ig: ingredients){
                    if(ig.test(item)){
                        return true;
                    }
                }
                return false;
            }
            return false;
        }
    }
    static{
        Listener.getServerDisconnectPoint().registerHandler((v)->{
            resetCache();
        });
    }
}
