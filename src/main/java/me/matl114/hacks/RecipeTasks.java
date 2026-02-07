package me.matl114.hacks;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.utils.recipes.RecipeIngredient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.RecipeBookDataC2SPacket;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.network.packet.s2c.play.RecipeBookRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.RecipeBookSettingsS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.*;
import net.minecraft.recipe.display.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RecipeTasks {
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static Map<Identifier,RecipeRecord> CACHE;
    public static final Map EMPTY = Map.of();
    public static final Map<String, RecipeType> TYPE_MAP = new LinkedHashMap<>();

    public static boolean isVanillaRecipeType(String rid){
        return Registries.RECIPE_TYPE.getOrEmpty(Identifier.tryParse(rid)).isPresent();
    }

    public static Map<Identifier, RecipeRecord> getAllRecipe(){
        init();
        return CACHE;
    }

    private static void init(){

        if(CACHE == null || CACHE.isEmpty()){
            resetCache();
            CACHE = new LinkedHashMap<>();
            //init empty map and do not go in if you are not in a world
            if(mc.world == null){
                return;
            }
            for (var entry : mc.player.getRecipeBook().recipes.entrySet()){
                var key = entry.getKey();
                var value = entry.getValue();
                CACHE.put(Identifier.ofVanilla(String.valueOf(key.index())), RecipeRecord.of(value));
            }
        }
    }
    private static void resetCache(){
        if(CACHE != null){
            CACHE.clear();
        }
        CACHE = null;
    }
    public static record RecipeRecord(Identifier identifier, ItemStack craftingTypeIcon, ItemStack output, RecipeIngredient[] ingredients) implements me.matl114.hacks.utils.recipes.RecipeEntry {
        public static RecipeRecord of(RecipeDisplayEntry instance){
            ContextParameterMap contextParameterMap = SlotDisplayContexts.createParameters(mc.world);
            ItemStack craftingStation = instance.display().craftingStation().getFirst(contextParameterMap);
            ItemStack output = instance.display().result().getFirst(contextParameterMap);
            RecipeIngredient[] recipeIngredients = transfer3x3RecipeDisplay(instance.display(), contextParameterMap);
            return new RecipeRecord(
                Identifier.ofVanilla(String.valueOf(instance.id().index())),
                craftingStation,
                output,
                recipeIngredients
            );
        }
        @Override
        public String rid() {
            return Registries.ITEM.getId(craftingTypeIcon.getItem()).toString();
        }

        @Override
        public String id() {
            return identifier.toString();
        }

        @Override
        public RecipeIngredient[] ingredient() {
            return ingredients;
        }

    }

    public static RecipeIngredient[] transfer3x3RecipeDisplay(RecipeTasks.RecipeRecord recipeRecord){
        return recipeRecord.ingredients();
    }
    public static RecipeIngredient[] transfer3x3RecipeDisplay(RecipeDisplay instance, ContextParameterMap map){

        RecipeIngredient[] ingredients = new RecipeIngredient[9];

        if(instance instanceof ShapedCraftingRecipeDisplay shaped){
            List<SlotDisplay> raw = shaped.ingredients();
            int width = shaped.width();
            int height = shaped.height();
            for (int i=0; i< 3; ++i){
                for(int j = 0; j< 3; ++j){
                    if(i < height && j < width){
                        ingredients[3*i + j] = new RecipeIngredient((raw.get(width * i + j)).getStacks(map).toArray(ItemStack[]::new));
                    }else {
                        ingredients[3*i + j] = RecipeIngredient.EMPTY;
                    }
                }
            }
        }else if(instance instanceof ShapelessCraftingRecipeDisplay shapeless) {
            int var = 0;
            for (var i : shapeless.ingredients()){
                ingredients[var ++] = new RecipeIngredient( i.getStacks(map).toArray(ItemStack[]::new));
                var += 1;
            }
            for (; var < 9 ; ++var){
                ingredients[var] = RecipeIngredient.EMPTY;
            }
        }else if(instance instanceof FurnaceRecipeDisplay shaped){
            ingredients[0] = new RecipeIngredient(shaped.ingredient().getStacks(map).toArray(ItemStack[]::new));
            for (var i = 1 ; i < 9 ; ++i){
                ingredients[i] = RecipeIngredient.EMPTY;
            }
        }else if(instance instanceof StonecutterRecipeDisplay shaped){
            ingredients[0] = new RecipeIngredient(shaped.input().getStacks(map).toArray(ItemStack[]::new));
            for (var i = 1 ; i < 9 ; ++i){
                ingredients[i] = RecipeIngredient.EMPTY;
            }
        }else if (instance instanceof SmithingRecipeDisplay shaped){
            ingredients[1] = new RecipeIngredient(shaped.base().getStacks(map).toArray(ItemStack[]::new));
            ingredients[2] = new RecipeIngredient(shaped.addition().getStacks(map).toArray(ItemStack[]::new));
            ingredients[0] = new RecipeIngredient(shaped.template().getStacks(map).toArray(ItemStack[]::new));
            for (var i = 3 ; i < 9 ; ++i){
                ingredients[i] = RecipeIngredient.EMPTY;
            }
        }else{
            //throw new UnsupportedOperationException("Unsupported type");
            for (var i = 0 ; i < 9 ; ++i){
                ingredients[i] = RecipeIngredient.EMPTY;
            }
        }
        return ingredients;
    }

    public static Stream<ItemStack> streamIngredientOptions(Ingredient ingredient){
        return ingredient.getCustomIngredient().getMatchingItems().map(RegistryEntry::value).map(ItemStack::new);
    }

    public static List<Ingredient> getIngredients(NetworkRecipeId recipeEntry){
        RecipeDisplayEntry entry = mc.player.getRecipeBook().recipes.get(recipeEntry);
        if(entry == null){
            return List.of();
        }
        return entry.craftingRequirements().orElse(List.of());
    }


    public static ItemStack getRecipeResult(NetworkRecipeId recipeEntry){
        RecipeDisplayEntry entry = mc.player.getRecipeBook().recipes.get(recipeEntry);
        if(entry == null){
            return ItemStack.EMPTY;
        }
        ContextParameterMap contextParameterMap = SlotDisplayContexts.createParameters(mc.world);
        List<ItemStack> stacks = entry.getStacks(contextParameterMap);
        return stacks.isEmpty()? ItemStack.EMPTY : stacks.get(0);
    }

    public static void addRecipe(Event<RecipeBookAddS2CPacket> event){
        if(mc.world == null) return;
        init();
        for(RecipeBookAddS2CPacket.Entry entry : event.context().entries()){
            RecipeDisplayEntry entry0 = entry.contents();
            CACHE.put(Identifier.ofVanilla(String.valueOf(entry0.id().index())), RecipeRecord.of(entry0));
        }
    }

    public static void removeRecipe(Event<RecipeBookRemoveS2CPacket> event){
        if(CACHE != null){
            event.context().recipes().stream().map(NetworkRecipeId::index).map(String::valueOf).map(Identifier::ofVanilla)
                .forEach(CACHE::remove);
        }
    }

    static{
        Listener.getServerDisconnectPoint().registerHandler((v)->{
            resetCache();
        });
        Listener.getPacketPostHandlePoint().getChannel(RecipeBookAddS2CPacket.class).registerHandler(RecipeTasks::addRecipe);
        Listener.getPacketPoint().getChannel(RecipeBookRemoveS2CPacket.class).registerHandler(RecipeTasks::removeRecipe);
        Listener.registerSinglePacketListener(SynchronizeRecipesS2CPacket.class, (Consumer<SynchronizeRecipesS2CPacket>) (p)->resetCache());
    }
}
