package me.matl114.hacks;

import me.matl114.events.Listener;
import me.matl114.hacks.utils.recipes.RecipeIngredient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RecipeTasks {
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static Map<Identifier,RecipeRecord> CACHE;
    private static RecipeManager INSTANCE ;
    private static DynamicRegistryManager REGISTRY;
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
    public static record RecipeRecord(Identifier identifier, Recipe<?> instance, RecipeType<?> type, ItemStack output, RecipeIngredient[] ingredients) implements me.matl114.hacks.utils.recipes.RecipeEntry {
        public static RecipeRecord of(RecipeEntry<?> instance, RecipeType type){
            return new RecipeRecord(instance.id(), (Recipe<?>) (Object)instance.value(), type, instance.value().getResult( mc.world.getRegistryManager()), RecipeTasks.transfer3x3RecipeDisplay(instance.value(), instance.value().getIngredients().stream().map(v -> new RecipeIngredient(v.getMatchingStacks())).toArray(RecipeIngredient[]::new)));
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
        public RecipeIngredient[] ingredient() {
            return ingredients;
        }

    }


    public static RecipeIngredient[] transfer3x3RecipeDisplay(RecipeTasks.RecipeRecord recipeRecord){
        return recipeRecord.ingredients();
    }
    public static RecipeIngredient[] transfer3x3RecipeDisplay(Recipe<?> instance, RecipeIngredient[] ingred){

        RecipeIngredient[] ingredients = new RecipeIngredient[9];

        if(instance instanceof ShapedRecipe shaped){
            List<Ingredient> raw = RecipeTasks.getIngredients(shaped);
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            for (int i=0; i< 3; ++i){
                for(int j = 0; j< 3; ++j){
                    if(i < height && j < width){
                        ingredients[3*i + j] = new RecipeIngredient(RecipeTasks.streamIngredientOptions(raw.get(width * i + j)).toArray(ItemStack[]::new));
                    }else {
                        ingredients[3*i + j] = RecipeIngredient.EMPTY;
                    }
                }
            }
        }else {
            System.arraycopy(ingred, 0, ingredients, 0, ingred.length);
            for (int i = ingred.length; i<9 ; ++i){
                ingredients[i] = RecipeIngredient.EMPTY;
            }
        }
        return ingredients;
    }








    public static List<Ingredient> getIngredients(Recipe<?> recipe){
        return recipe.getIngredients();
    }

    public static Stream<ItemStack> streamIngredientOptions(Ingredient ingredient){
        return Arrays.stream(ingredient.getMatchingStacks());
    }

    public static List<Ingredient> getIngredients(RecipeEntry<?> recipeEntry){
        return recipeEntry.value().getIngredients();
    }

    public static ItemStack getRecipeResult(RecipeEntry<?> recipeEntry){
        return recipeEntry.value().getResult(MinecraftClient.getInstance().world.getRegistryManager());
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
