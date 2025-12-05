package me.matl114.hackUtils;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.matl114.listenerUtils.Listener;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ChangeUnlockedRecipesS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.*;
import java.util.function.Consumer;

public class RecipeTasks {
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static Map<Identifier,RecipeRecord> CACHE;
    private static RecipeManager INSTANCE ;
    private static DynamicRegistryManager REGISTRY;
    public static final Map EMPTY = Map.of();
    public static final Map<String, RecipeType> TYPE_MAP = new LinkedHashMap<>();


    public static RecipeType getById(String id){
        if(TYPE_MAP .isEmpty()){
            Registries.RECIPE_TYPE.stream().forEach(rt->TYPE_MAP.put(Registries.RECIPE_TYPE.getId(rt).toString(),rt));
        }
        return TYPE_MAP.get(id);
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
            CACHE = new LinkedHashMap<>();
            for (RecipeType<?> types : Registries.RECIPE_TYPE){
                var rcps = (List<RecipeEntry>)(Object)(INSTANCE.listAllOfType((RecipeType) types));
                for (RecipeEntry rcp: rcps){
                    CACHE.put(rcp.id(), RecipeRecord.of(rcp, types));
                }
            }

        }
    }
    private static void resetCache(){

        INSTANCE = null;
        if(CACHE != null){
            CACHE.clear();
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

    }
    static{
//        Listener.getServerDisconnectPoint().registerHandler((v)->{
//            resetCache();
//        });
        Listener.getWorldSwitchPoint().registerHandler((v)->{
            resetCache();
        });
        Listener.registerSinglePacketListener(SynchronizeRecipesS2CPacket.class, (Consumer<SynchronizeRecipesS2CPacket>) (p)->resetCache());
    }
}
