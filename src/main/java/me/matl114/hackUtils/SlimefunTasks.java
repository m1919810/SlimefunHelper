package me.matl114.hackUtils;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.matl114.access.*;
import me.matl114.gui.basic.InputHandler;
import me.matl114.gui.presets.choices.QuestionScreen;
import me.matl114.gui.slimefun.SavedItemWidget;
import me.matl114.gui.slimefun.SlimefunChoiceScreen;
import me.matl114.gui.slimefun.SlimefunEntryListScreen;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.SlotElement;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.ConfigLoader;
import me.matl114.managers.Configs;
import me.matl114.managers.ScheduleService;
import me.matl114.utils.*;
import me.matl114.utils.UtilClass.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.matl114.gui.FilterService.*;
import static me.matl114.hackUtils.SlimefunTasks.MultiBlockEntry.DIR_CONSIDER;
import static me.matl114.hackUtils.SlimefunTasks.MultiBlockEntry.DIR_SYMM;

public class SlimefunTasks {
    public static void init(){

    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static record ItemStackWithId(ItemStack stack, String identifier){
        public static ItemStackWithId ofNullable(ItemStack item){
            if(item == null || item.isEmpty())return EMPTY;
            String optional = generateId(item);
            return new ItemStackWithId(item, optional);
//            if(optional != null){
//
//            }else {
//                return new ItemStackWithId(item, Registries.ITEM.getId(item.getItem()).toString());
//            }
        }
        public static String generateId(ItemStack item){
            if(item == null || item.isEmpty()){
                return EMPTY.identifier;
            }
            String optional = ItemStackUtils.getSfId(item);
            return optional != null ? optional: Registries.ITEM.getId(item.getItem()).toString();

        }
        public static ItemStackWithId EMPTY = new ItemStackWithId(ItemStack.EMPTY, "minecraft:air");
    }
    public static final JsonCodec<ItemStackSample> ITEM_SAMPLE_CODEC = new JsonCodec<ItemStackSample>() {
        @Override
        public ItemStackSample deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if(json.isJsonNull()){
                return ItemStackSample.EMPTY;
            }
            if(json.isJsonObject()){
                JsonObject jsonMap = json.getAsJsonObject();
                try{
                    ItemStack stack = ItemStack.CODEC.decode(ItemStackUtils.registry().getOps(JsonOps.INSTANCE), jsonMap).getOrThrow().getFirst();
                    return stack.isEmpty() ? ItemStackSample.EMPTY: new ItemStackSample(stack);
                }catch (Throwable e){
                    throw new JsonParseException(e);
                }
            }else{
                String jsonString = json.getAsString();
                try{
                    NbtCompound nbtElement = (NbtCompound) new StringNbtReader(new StringReader(jsonString)).parseElement();
                    ItemStack stack = ItemStack.CODEC.decode(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), nbtElement).getOrThrow().getFirst();
                    return stack.isEmpty()? ItemStackSample.EMPTY : new ItemStackSample(stack);
                }catch (Throwable e){
                    throw new NbtException(e.getMessage());
                }
            }

        }

        @Override
        public JsonElement serialize(ItemStackSample src, Type typeOfSrc, JsonSerializationContext context) {
            if(src == ItemStackSample.EMPTY || src.sample().isEmpty()){
                return JsonNull.INSTANCE;
            }
            try{
               // JsonElement jsonMap = ItemStack.CODEC.encodeStart(ItemStackUtils.registry().getOps(JsonOps.INSTANCE), src.sample).getOrThrow();
                //return jsonMap;
                NbtCompound nbt = (NbtCompound) src.sample().encode(ItemStackUtils.registry());
                String nbtString = new StringNbtWriter().apply(nbt);
                return new JsonPrimitive(nbtString);
            }catch (Throwable e){
                throw new JsonParseException(e);
            }
        }
    };

    private static final Random rand = new Random();
    private static SlimefunRegistryDataBase check(){
        dataOperationLock.lock();
        try{
            if(DATA == null){
                DATA = new SlimefunRegistryDataBase();
                DATA.loadData();
            }
            //Preconditions.checkArgument(DATA != null, "Illegal State, you are not in a game!");
            return DATA;
        }finally {
            dataOperationLock.unlock();
        }

    }
    private static CompletableFuture<SlimefunRegistryDataBase> checkAsync(){
        if(DATA == null){
            return CompletableFuture.supplyAsync(SlimefunTasks::check);
        }else{
            return CompletableFuture.completedFuture(DATA);
        }
    }
    public static String getIdOrNull(ItemStack item){
        return check().getIdOrNull(item);
    }

    public static String getSfIdOrNull(ItemStack item){
        String sfid = ItemStackUtils.getSfId(item);
        return sfid == null? getIdOrNull(item) : sfid;
    }

    public static String getOrAddId(ItemStack item){
        return check().getOrAddId(item);

    }
    public static ItemStack byId(String id){
        return check().byId(id);
    }

    public static final JsonCodec<CraftingType> TYPE_CODEC = new JsonCodec<CraftingType>(){
        @Override
        public JsonElement serialize(CraftingType src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("rid", (String) src.id);
            jsonObject.add("icon", context.serialize(src.icon));
            return jsonObject;
        }

        @Override
        public CraftingType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String rid = jsonObject.getAsJsonPrimitive("rid").getAsString();
            ItemStack icon = context.deserialize(jsonObject.getAsJsonObject("icon"), ItemStack.class);
            return new CraftingType(rid, icon);
        }
    };

    public static final JsonCodec<SlimefunRecipeEntry> ENTRY_CODED = new JsonCodec<SlimefunRecipeEntry>() {
        @Override
        public SlimefunRecipeEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject data = json.getAsJsonObject();
            String rid = data.getAsJsonPrimitive("rid").getAsString();
            String id = data.getAsJsonPrimitive("id").getAsString();
            ItemStack output = context.deserialize(data.getAsJsonObject("output"), ItemStack.class);
            if(output == null){

                Debug.info("Deserialize output failure", id);
                output = ItemStack.EMPTY;
            }
            ItemStack[] ingredient = context.deserialize(data.getAsJsonArray("ingredient"), ItemStack[].class);
            ItemStackWithId[] ingredientEntry = new ItemStackWithId[ingredient.length];
            //resolve the NULL problem
            for (int i=0 ;i<ingredient.length; ++i){
                ingredientEntry[i] = ItemStackWithId.ofNullable(ingredient[i]);
            }
            return new SlimefunRecipeEntry(rid, id, ingredientEntry, output);
        }

        @Override
        public JsonElement serialize(SlimefunRecipeEntry src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject data = new JsonObject();
            data.addProperty("rid", (String) src.rid);
            data.addProperty("id", (String) src.id);
            data.add("output", context.serialize((ItemStack)src.output));
            data.add("ingredient", context.serialize((ItemStack[])src.inputs()));
            return data;
        }
    };




    private static final Type DATA_MAP_TYPE = new TypeToken<Map<String, ItemStackSample>>(){}.getType();
    private static final Type TYPE_MAP_TYPE = new TypeToken<Map<String, CraftingType>>(){}.getType();
    private static final Type RECIPE_MAP_TYPE = new TypeToken<Map<String, SlimefunRecipeEntry>>(){}.getType()
        ;
    private static final Type ID_LIST_TYPE = new TypeToken<Map<String, List<String>>>(){}.getType();

    private static final Map<String, ItemStack> SUPPORT_VANILLA_RTYPE = Map.of(
        "minecraft:crafting", new ItemStack(Items.CRAFTING_TABLE),
        "minecraft:smelting", new ItemStack(Items.FURNACE),
        "minecraft:blasting", new ItemStack(Items.BLAST_FURNACE),
        "minecraft:smoking", new ItemStack(Items.SMOKER),
        "minecraft:campfire_cooking", new ItemStack(Items.CAMPFIRE),
        "minecraft:stonecutting", new ItemStack(Items.STONECUTTER),
        "minecraft:smithing", new ItemStack(Items.SMITHING_TABLE)
    );
    private static ItemStack getSupportVanillaIcon(String rid){
        return SUPPORT_VANILLA_RTYPE.getOrDefault(rid,null);
    }
    public static ItemStack getRecipeTypeIcon(String rid){
        ItemStack rt;
        if((rt = getSupportVanillaIcon(rid)) == null){
            rt = getSlimefunRecipeTypeIcon(rid);
        }
        return rt;
    }
    public static ItemStack getSlimefunRecipeTypeIcon(String rid){
        return check().ALL_RECIPE_TYPE.getOrDefault(rid,CraftingType.EMPTY).icon;
    }
    public static  SlimefunRegistryDataBase DATA;
    private static final ReentrantLock dataOperationLock = new ReentrantLock();

    private static class SlimefunRegistryDataBase{
        private DirtyMap<String, CraftingType> ALL_RECIPE_TYPE;
        private DirtyMap<String, SlimefunRecipeEntry> ALL_RECIPE_ENTRY;
        private DirtyCollectionImpl<Set<String>,String> SAVED_ITEM_ID;
        private static DirtyMap<ItemStackSample, String> ITEM_SAMPLE_MAP;
        private static Map<String, ItemStackSample> ITEM_SAMPLE_INDEX ;
        private Set<String> ACTIVE_ID = new HashSet<>();
        public final JsonCodec<ItemStack> ITEM_CODEC = new JsonCodec<ItemStack>() {
            @Override
            public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if(json.isJsonNull()){
                    return ItemStack.EMPTY;
                }
                var jsonObject = json.getAsJsonObject();
                String val = jsonObject.getAsJsonPrimitive("typeid").getAsString();
                ItemStack sampleCopy = byId(val);
                int amount = jsonObject.getAsJsonPrimitive("amount").getAsInt();
                sampleCopy.setCount(amount);
                return sampleCopy;
            }

            @Override
            public JsonElement serialize(ItemStack src, Type typeOfSrc, JsonSerializationContext context) {
                if(src.isEmpty()){
                    return JsonNull.INSTANCE;
                }
                String typeid = getOrAddId(src);
                var json = new JsonObject();
                json.addProperty("typeid",typeid);
                json.addProperty("amount", src.getCount());
                return json;
            }
        };
        private final Gson RECIPES_JSON_CODEC = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(ItemStackSample.class, ITEM_SAMPLE_CODEC)
            .registerTypeAdapter(ItemStack.class, ITEM_CODEC)
            .registerTypeAdapter(CraftingType.class, TYPE_CODEC)
            .registerTypeAdapter(SlimefunRecipeEntry.class, ENTRY_CODED)
            .create();
        //TODO: check if custom enchantment load fail in certain server. check data save, do not remove original key when data load failure
        private boolean loaded = false;
        private boolean loadSuccess = false;
        public void loadData(){
            dataOperationLock.lock();
            try{
                if(loaded)return;
                loaded = true;
                Debug.chat("载入粘液物品记录中...");
                Map<String, ItemStackSample> map0 = new Object2ReferenceOpenHashMap<>();
                String dataJson = ConfigLoader.loadExternalJson("sfhelper-configs/recipes/item-database.json");
                map0.putAll(RECIPES_JSON_CODEC.fromJson(dataJson, DATA_MAP_TYPE));
                Map<ItemStackSample, String> data0 = new Object2ReferenceOpenHashMap<>();
                map0.forEach((k,v)->data0.put(v,k));
                ITEM_SAMPLE_MAP = new DirtyMap<>(data0);
                ITEM_SAMPLE_INDEX = new Object2ReferenceOpenHashMap<>(map0);
                ACTIVE_ID.clear();

                Map<String, CraftingType> map = new Object2ReferenceOpenHashMap<>();
                String typeJson = ConfigLoader.loadExternalJson("sfhelper-configs/recipes/craft-types.json");
                map.putAll(RECIPES_JSON_CODEC.fromJson(typeJson, TYPE_MAP_TYPE));
                ALL_RECIPE_TYPE = new DirtyMap<>(map);
                Map<String, SlimefunRecipeEntry> map1 = new Object2ReferenceOpenHashMap<>();
                String recipeJson = ConfigLoader.loadExternalJson("sfhelper-configs/recipes/recipe-data.json");
                map1.putAll(RECIPES_JSON_CODEC.fromJson(recipeJson, RECIPE_MAP_TYPE));
                ALL_RECIPE_ENTRY = new DirtyMap<>(map1);
                resetMultiblockRegistry();
                map1.forEach((k,v)->{
                    if(MULTIBLOCK_REGEX.asMatchPredicate().test(v.rid)){
                        addToMultiblockRegistry(v);
                    }
                });
                Set<String> saveId = new LinkedHashSet<>();
                String savedItemIds = ConfigLoader.loadExternalJson("sfhelper-configs/recipes/saved-items.json");
                saveId.addAll(((Map<String, List<String>>)RECIPES_JSON_CODEC.fromJson(savedItemIds, ID_LIST_TYPE)).getOrDefault("saved-ids",List.of()));
                SAVED_ITEM_ID = new DirtyCollectionImpl<>(saveId);
                //mark saved item id as active id:
                ACTIVE_ID.addAll(saveId);
                var iter = ITEM_SAMPLE_MAP.entrySet().iterator();
                while (iter.hasNext()){
                    if(!ACTIVE_ID.contains( iter.next().getValue()) ){
                        iter.remove();
                    }
                }
                var iter2 = ITEM_SAMPLE_INDEX.entrySet().iterator();
                while (iter2 .hasNext()){
                    if(!ACTIVE_ID.contains(iter2.next().getKey())){
                        iter2.remove();
                    }
                }
                ACTIVE_ID.clear();
                loadSuccess = true;
            } catch (Throwable e){
                Debug.chat("粘液数据库加载失败,可能是数据库出现了损坏或者服务器版本不支持");
                Debug.chat("请进入本地存档测试,或者使用日志文件提问");
                Debug.info("Error while loading slimefun database :");
                e.printStackTrace();
                loadSuccess = false;
                ALL_RECIPE_TYPE = new DirtyMap<>(new Object2ReferenceOpenHashMap<>());
                ALL_RECIPE_ENTRY = new DirtyMap<>(new Object2ReferenceOpenHashMap<>());
                SAVED_ITEM_ID = new DirtyCollectionImpl<>(new LinkedHashSet<>());
                ITEM_SAMPLE_MAP = new DirtyMap<>(new Object2ReferenceOpenHashMap<>());
                ITEM_SAMPLE_INDEX = new Object2ReferenceOpenHashMap<>();
            } finally {
                dataOperationLock.unlock();
            }


        }
        public void saveData(){
            //save async
            //problem occurred, registry access denied
            if(!loadSuccess){
                //do not save when data load failure
                return;
            }
            if(!loaded)return;
            Map<String, String> savingData = new LinkedHashMap<>();
            if(!Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_RECIPE_SAVE).get()){
                return;
            }
            //serialization must be in main thread, because of registry access
            dataOperationLock.lock();
            try{
                try{
                    if(ALL_RECIPE_TYPE != null && ALL_RECIPE_TYPE.isDirty()){
                        ALL_RECIPE_TYPE.setDirty(false);
                        Map<String, CraftingType> map = ALL_RECIPE_TYPE.getDelegate();
                        String savedData = RECIPES_JSON_CODEC.toJson(map);
                        savingData.put("sfhelper-configs/recipes/craft-types.json", savedData);
                    }
                }catch (Throwable e){
                    Debug.info("序列化RecipeTypes数据失败, 错误:");
                    Debug.info(e);
                }
                try{
                    if(ALL_RECIPE_ENTRY != null && ALL_RECIPE_ENTRY.isDirty()){
                        ALL_RECIPE_ENTRY.setDirty(false);
                        Map<String, SlimefunRecipeEntry> map = ALL_RECIPE_ENTRY.getDelegate();
                        String savedData = RECIPES_JSON_CODEC.toJson(map);
                        savingData.put("sfhelper-configs/recipes/recipe-data.json", savedData);
                    }
                }catch (Throwable e){
                    Debug.info("序列化RecipeEntry数据失败, 错误:");
                    Debug.info(e);
                }
                try{
                    if(SAVED_ITEM_ID != null && SAVED_ITEM_ID.isDirty()){
                        SAVED_ITEM_ID.setDirty(false);
                        List<String> list = SAVED_ITEM_ID.getDelegate().stream().toList();
                        String savedData = RECIPES_JSON_CODEC.toJson(Map.of("saved-ids" ,list));
                        savingData.put("sfhelper-configs/recipes/saved-items.json", savedData);
                    }
                }catch (Throwable e){
                    Debug.info("序列化SavedItem Id数据失败, 错误:");
                    Debug.info(e);
                }
                //save at last in case of key missing for save
                try{
                    if(ITEM_SAMPLE_MAP != null && ITEM_SAMPLE_MAP.isDirty()){
                        ITEM_SAMPLE_MAP.setDirty(false);
                        String savedData = RECIPES_JSON_CODEC.toJson(ITEM_SAMPLE_INDEX);
                        savingData.put("sfhelper-configs/recipes/item-database.json", savedData);
                    }
                }catch (Throwable e){
                    Debug.info("序列化ItemStack DB数据失败, 错误:");
                    Debug.info(e);
                }
            }finally {
                dataOperationLock.unlock();
            }
            if(!savingData.isEmpty()){
                CompletableFuture.runAsync(()->{
                    for(var re : savingData.entrySet()){
                        try{
                            ConfigLoader.saveToFile(re.getKey(), re.getValue());
                        }catch (Throwable e){
                            Debug.info("保存", re.getKey(), "文件时报错:");
                            Debug.info(e);
                        }
                    }
                });
            }
        }
        private Map<String, MultiBlockEntry> MULTIBLOCK_REGISTRIES = new Object2ReferenceOpenHashMap<>();

        private Map<Block, Set<MultiBlockEntry>> MULTIBLOCK_INDEXED_BY_POTENTIALS = new Reference2ReferenceOpenHashMap<>();
        private void resetMultiblockRegistry(){
            if(MULTIBLOCK_REGISTRIES != null){
                MULTIBLOCK_REGISTRIES.clear();
            }
            MULTIBLOCK_REGISTRIES = new Object2ReferenceOpenHashMap<>();
            if(MULTIBLOCK_INDEXED_BY_POTENTIALS != null){
                MULTIBLOCK_INDEXED_BY_POTENTIALS = new Reference2ReferenceOpenHashMap<>();
            }
        }
        //private method
        private void addToMultiblockRegistry(SlimefunRecipeEntry entry){
            var newEntry = MultiBlockEntry.of(entry.inputs(), entry.id);
            if(newEntry == null)return;
            MULTIBLOCK_REGISTRIES.put(entry.id, newEntry);
            Set<Block> potentialTriggerBlocks = newEntry.getPotentials();
            for (Block block: potentialTriggerBlocks){
                MULTIBLOCK_INDEXED_BY_POTENTIALS.computeIfAbsent(block, (b)->new ReferenceArraySet<>()).add(newEntry);
            }
        }

        public String getIdOrNull(ItemStack item){
            //fix NBT Tool item with viaversion , in hasinPatch
            if(ItemStackUtils.hasInPatch(item)){
                //only nbt item use custom type;
                ItemStackSample sample = ItemStackSample.of(item);
                String re =  ITEM_SAMPLE_MAP.getOrDefault(sample, null);
                if(re != null){
                    ACTIVE_ID.add(re);
                }
                return re;
            }else {
                Identifier identifier = Registries.ITEM.getId(item.getItem());
                return identifier == null ? "minecraft:air" : identifier.toString();
            }
        }

        public String getOrAddId(ItemStack item){
            if(ItemStackUtils.hasInPatch(item)){
                //only nbt item use custom type;
                ItemStackSample sample = ItemStackSample.of(item);
                String re =  ITEM_SAMPLE_MAP.computeIfAbsent(sample, (s)->{
                    String newName ;
                    do{
                        newName = "customitems:" +rand.nextInt(1145141919);
                    }while (ITEM_SAMPLE_INDEX.containsKey(newName));

                    ITEM_SAMPLE_INDEX.put(newName, s);
                    ITEM_SAMPLE_MAP.setDirty();
                    return newName;
                });
                ACTIVE_ID.add(re);
                return re;
            }else {
                Identifier identifier = Registries.ITEM.getId(item.getItem());
                return identifier == null ? "minecraft:air" : identifier.toString();
            }
        }
        public ItemStack byId(String id){
            if(id.startsWith("customitems:")){
                ACTIVE_ID.add(id);
                var re= ITEM_SAMPLE_INDEX.get(id);
                return re != null ? re.sample().copy() : ItemStack.EMPTY;
            }else {
                return new ItemStack(Registries.ITEM.get(Identifier.tryParse(id)));
            }
        }

        public void putSlimefunEntry(SlimefunRecipeEntry entry){
            if(!loadSuccess){
                return;
            }
            ALL_RECIPE_ENTRY.put(entry.id(), entry);
            if(MULTIBLOCK_REGEX.asMatchPredicate().test(entry.rid())){
                check().addToMultiblockRegistry(entry);
            }
        }
        public void validateRecipeType(String rid, ItemStack icon){
            if (!loadSuccess){
                return;
            }
            if(!check().ALL_RECIPE_TYPE.containsKey(rid)){
                ItemStack ICON = icon.isEmpty()? ITEM_NULL_TYPE.copy(): icon.copy();
                ICON.setCount(1);
                check().ALL_RECIPE_TYPE.put(rid, new CraftingType(rid, ICON));
            }
        }

    }


    public static interface BlockMatcher{
        public Set<Block> getPotentials();
        default boolean match(Block b){
            return getPotentials().contains(b);
        }
    }
    public static record SingleBlockMatch(Block block) implements BlockMatcher{
        public Set<Block> getPotentials(){
            return Set.of(block);
        }
        public boolean match(Block b){
            return b == block;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SingleBlockMatch && block == ((SingleBlockMatch) o).block;
        }
    }
    public static record TaggedBlockMatch(TagKey<Block> blockTagKey) implements BlockMatcher{
        public Set<Block> getPotentials(){
            return Registries.BLOCK.getEntryList(blockTagKey).orElseThrow().stream().map(RegistryEntry::value).collect(Collectors.toUnmodifiableSet());
        }

        public boolean match(Block b){
            return b.getRegistryEntry().isIn(blockTagKey);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TaggedBlockMatch && ((TaggedBlockMatch) o).blockTagKey == this.blockTagKey;
        }
    }
    //means the AIR in the recipe, any block is ok and none is required
    public static final BlockMatcher ANY_MATCH = new BlockMatcher() {
        @Override
        public Set<Block> getPotentials() {
            return Set.of();
        }

        @Override
        public boolean match(Block b) {
            return true;
        }
    };
    public static final BlockMatcher NONE_MATCH = new BlockMatcher() {
        @Override
        public Set<Block> getPotentials() {
            return Set.of();
        }
        @Override
        public boolean match(Block b) {
            return false;
        }
    };
    public static class DispenserMultiBlockLookup{
        public BlockMatcher[] blockTypes;
        public Point dispenserPos;
        boolean symm;
        public Map<BiPredicate<World,BlockPos>, Vec3i> predicate2LeftRightAxis = new Object2ReferenceArrayMap<>();
        public DispenserMultiBlockLookup(BlockMatcher[] blockTypes, boolean isSymm){
            this.blockTypes = blockTypes;
            this.symm  = isSymm;
            initData();
        }
        //todo fix the press chamber and the supreme core-factory bug
        private void initData(){
            for (int i=0 ;i<9 ;++i){
                if(blockTypes[i] != ANY_MATCH && blockTypes[i].match(Blocks.DISPENSER)){
                    dispenserPos = new Point(i%3, i/3);
                    break;
                }
            }
            if(dispenserPos != null){
                //has dispensor
                for (Direction dir: symm? DIR_SYMM : DIR_CONSIDER){{
                    generatePredicate(dir.getVector());
                }}
            }
        }
        private void generatePredicate(Vec3i axis){
            List<BiPredicate<World,BlockPos>> listPredicates = new ArrayList<>();
            for (int i=0 ;i<9; ++i){
                BlockMatcher matcher = blockTypes[i];
                //jump dispenser
                if(i == dispenserPos.x + dispenserPos.y*3)continue;
                if(matcher != ANY_MATCH){
                    int daxis = -dispenserPos.x +i%3;
                    int dy = -dispenserPos.y + i/3;
                    Vec3i targetPos = new Vec3i(axis.getX() * daxis, dy, axis.getZ()* daxis);
                    listPredicates.add((world, pos)->{
                        return matcher.match( world.getBlockState(  pos.add(targetPos)).getBlock());
                    });
                }
            }
            predicate2LeftRightAxis.put(((clientWorld, blockPos) -> {
                for (var pd: listPredicates){
                    if(!pd.test(clientWorld, blockPos))return false;
                }
                return true;
            }), axis);

        }
        public Collection<MultiBlockLocation> lookup(World world, BlockPos dispensorPos){
            Collection<MultiBlockLocation> block = new HashSet<>();
            if(dispensorPos != null){
                for (var pd: predicate2LeftRightAxis.entrySet()){
                    if(pd.getKey().test(world, dispensorPos)){
                        BlockPos pos = dispensorPos.add(-this.dispenserPos.x * pd.getValue().getX(), -this.dispenserPos.y, -this.dispenserPos.x * pd.getValue().getZ() );
                        block.add(new MultiBlockLocation(pos, pd.getValue()));
                    }
                }
            }
            return block;
        }


    }
    @AllArgsConstructor
    public static class MultiBlockLocation {
        BlockPos leftDown;
        Vec3i left2Right;

        public BlockPos getComponentBlock(int x, int y){
            return leftDown.add(left2Right.getX() * x, y, left2Right.getZ() *x );
        }
    }

    @AllArgsConstructor
    public static class MultiBlockWithLocation{
        public MultiBlockEntry multiBlockEntry;
        public MultiBlockLocation location;
    }

    //should store from left to right, **from low to high**
    public static record MultiBlockEntry(String id, BlockMatcher[] blockTypes, Collection<Point> optionalActionBlock, DispenserMultiBlockLookup lookup, boolean symm) implements BlockMatcher{
        public Set<Block> getPotentials(){
            Set<Block> blocks = new HashSet<>();
            blocks.addAll(blockTypes[1].getPotentials());
            blocks.addAll(blockTypes[4].getPotentials());
            blocks.addAll(blockTypes[7].getPotentials());
            return blocks;
        }
        private static final TaggedBlockMatch LOGS = new TaggedBlockMatch(BlockTags.LOGS);
        private static final TaggedBlockMatch WOODEN_TRAPDOORS = new TaggedBlockMatch(BlockTags.WOODEN_TRAPDOORS);
        private static final TaggedBlockMatch WOODEN_SLABS = new TaggedBlockMatch(BlockTags.WOODEN_SLABS);
        private static final TaggedBlockMatch WOODEN_FENCES = new TaggedBlockMatch(BlockTags.WOODEN_FENCES);
        private static final BlockMatcher FIRE = new BlockMatcher(){
            public Set<Block> getPotentials(){
                Set<Block> fires =  Registries.BLOCK.getEntryList(BlockTags.FIRE).orElseThrow().stream().map(RegistryEntry::value).collect(Collectors.toCollection(HashSet::new));
                fires.add(Blocks.AIR);
                return fires;
            }

            public boolean match(Block b){
                return b == Blocks.AIR || b.getRegistryEntry().isIn(BlockTags.FIRE);
            }

            @Override
            public boolean equals(Object o) {
                return o == this;
            }
        };

        public static MultiBlockEntry of(ItemStack[] inputs, String id){
            if(inputs.length != 9){
                return null;
            }
            BlockMatcher[] array = new BlockMatcher[9];
            for (int i=0; i< 9; ++i){
                Item item = inputs[i].getItem();
                net.minecraft.block.Block block = null;
                if(item == Items.AIR){
                    block = Blocks.AIR;
                }else if(item == Items.FLINT_AND_STEEL){
                    block = Blocks.FIRE;
                }else {
                    block = Block.getBlockFromItem(item);
                }
                RegistryEntry<Block> blockRegistryEntry = block.getRegistryEntry();
                int idx = (2-i/3) * 3 + i%3;
                if(blockRegistryEntry.isIn(BlockTags.LOGS)){
                    array[idx] = LOGS;
                }else if(blockRegistryEntry.isIn(BlockTags.WOODEN_TRAPDOORS)){
                    array[idx] = WOODEN_TRAPDOORS;
                }else if(blockRegistryEntry.isIn(BlockTags.WOODEN_SLABS)){
                    array[idx] = WOODEN_SLABS;
                }else if(blockRegistryEntry.isIn(BlockTags.WOODEN_FENCES)){
                    array[idx] = WOODEN_FENCES;
                }else if(blockRegistryEntry.isIn(BlockTags.FIRE)){
                    array[idx] = FIRE;
                }else{
                    if(item == Items.AIR){
                        array[idx] = ANY_MATCH;
                    }else{
                        array[idx] = block == Blocks.AIR ? NONE_MATCH : new SingleBlockMatch(block);
                    }
                }
            }
            boolean symm =true;
            for (int i=0 ;i< 3; ++i){
                if(!Objects.equals(array[3*i], array[3*i+2])){
                    symm = false;
                    break;
                }
            }
            //judge the optional Action
            Collection<Point> coord = new HashSet<>();
            //发射器在中间
            if( array[7].match(Blocks.DISPENSER)){
                if(array[4].match(Blocks.DISPENSER)){
                    if( array[1].match(Blocks.DISPENSER)){
                        coord.add(new Point(1,1));
                        coord.add(new Point(1,2));
                    }else{
                        coord.add(new Point(1,0));
                    }
                }else{
                    coord.add(new Point(1, 1));
                }
            }else {
                coord.add(new Point(1,2));
            }
            DispenserMultiBlockLookup lookup = new DispenserMultiBlockLookup(array, symm);
            return new MultiBlockEntry(id,  array, coord, lookup, symm);
        }
        static Direction[] DIR_CONSIDER =new Direction[] {Direction.NORTH, Direction.WEST,Direction.SOUTH,Direction.EAST};
        static Direction[] DIR_SYMM = new Direction[] {Direction.NORTH, Direction.WEST};

        public MultiblockOffset matchDirection(World world, BlockPos blockPos){
            position:
            for (int i=0 ;i <=2 ;++i){
                //match middle first
                BlockPos.Mutable middleBotton = blockPos.mutableCopy().move(0, -i,0);
                for (int s = 0; s <= 2; ++s){
                    Block block = world.getBlockState(middleBotton).getBlock();
                    if(!blockTypes[1+ 3*s].match(block)){
                        continue position;
                    }
                    middleBotton.move(0,1,0);
                }
                //middle match
                //then consider symm
                Direction currentDir = null;
                directionMatch:
                for (Direction dir: symm? DIR_SYMM : DIR_CONSIDER){

                    BlockPos.Mutable leftBotton = blockPos.mutableCopy().move(0,-i,0).move(dir);
                    for (int s = 0; s <= 2; ++s){

                        Block block = world.getBlockState(leftBotton).getBlock();
                        if(!blockTypes[3*s].match(block)){
                            continue directionMatch;
                        }
                        leftBotton.move(0,1,0);
                    }
                    currentDir = dir;
                    if(!symm){
                        BlockPos.Mutable rightBotton = blockPos.mutableCopy().move(0,-i,0).move(currentDir,-1);
                        //if not symm, still need check
                        for (int s = 0; s <= 2; ++s){
                            Block block = world.getBlockState(rightBotton).getBlock();
                            if(!blockTypes[3*s + 2].match(block)){
                                continue directionMatch;
                            }
                            rightBotton.move(0,1,0);
                        }
                    }
                    return new MultiblockOffset(i, currentDir);
                }
            }
            return null;
        }
        public boolean anyMatchMiddle(World world, BlockPos blockPos){
            return matchDirection(world, blockPos) != null;
        }
        public List<BlockPos> getPositionByDirection(BlockPos blockPos, MultiblockOffset direcion){
             blockPos.add(0,-direcion.dy, 0);
             int dy = direcion.dy;
             Direction dir = direcion.direction;
            List<BlockPos> pos = new ArrayList<>(9);
            for (int i=-1; i<= 1; ++i){
                for (int j=0; j<= 2; ++j){
                    //only match existing block
                    if(blockTypes[3*j + (i + 1)] != ANY_MATCH){
                        pos.add(blockPos.add(i* dir.getOffsetX(), j-dy, i*dir.getOffsetZ()));
                    }
                }
            }
            return pos;
        }
        public Collection<BlockPos> getOptionalActionFromDispenser(ClientWorld world, BlockPos pos){
            Collection<MultiBlockLocation> locations = lookup.lookup(world, pos);
            if(locations != null && !locations.isEmpty()){
                Set<BlockPos> poseSet = new HashSet<>();
                for (var ml: locations){
                    for (var pt: optionalActionBlock){
                        poseSet.add(ml.getComponentBlock(pt.x, pt.y));
                    }
                }
                return poseSet;
            }
            return Set.of();
        }
        //public Collection<Pair<BlockPos, MultiBlockEntry>>

        public Optional<CraftingType> getOptionalCraftingType(){
            return id == null ? Optional.empty(): check().ALL_RECIPE_TYPE.values().stream().filter(ct->id.equals( ItemStackUtils.getSfId(ct.icon)))
                .findFirst();
        }
    }
    public static record MultiblockOffset(int dy, Direction direction){

    }


    private static Config.FlagRef ENABLE_CLICK = Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_MULTIBLOCK_CLICKER);
    private static Config.IntRef CLICK_RATE = Configs.SLIMEFUN_CONFIG.getInt(Configs.SLIMEFUN_MB_RATE);
    private static int lastChatTimestamp = 0;
    private static int lastInteractTimestamp = 0;
    private static void onClickBlock(Event<BlockHitResult> result){
        if(!ENABLE_CLICK.get()){
            return;
        }
        //judge
        onClickBlockExecute(result.context(), false, true);
    }
    private static final Config.FlagRef legalMode = Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_MB_LEGAL);
    private static final Config.EnumRef<Configs.LegalTargetingMode> legalTargetingMode = Configs.SLIMEFUN_CONFIG.getEnum(Configs.SLIMEFUN_MB_LEGAL_MODE);
    private static final Random interactOffsetRand = new Random();
//    private static final Deque<LegalMovementManager.MovementModifier> taskQueue = new ArrayDeque<>();

    private static void onClickBlockExecute(BlockHitResult result, boolean delayClick, boolean clickMany){
        if(result == null)return;
        BlockPos pos = result.getBlockPos();
        Block block = mc.world.getBlockState(pos).getBlock();
        //do not speed up when opening crafting dispensor
        if(block == Blocks.DISPENSER || block == Blocks.DROPPER){
            return;
        }
        var potentials = check().MULTIBLOCK_INDEXED_BY_POTENTIALS.get(block);
        if(potentials == null || potentials.isEmpty())return;
        Optional<MultiBlockEntry> first = potentials.stream()
            .filter(m->m.anyMatchMiddle(mc.world, pos))
            .findFirst();
        if(first.isEmpty())return;
        if(lastChatTimestamp + 5*20 < Tasks.getTick()){
            Debug.chat(Text.literal("[fast click] Interacting with multiblock: ").formatted(Formatting.RED),first.get().id);
            lastChatTimestamp = Tasks.getTick();
        }

        if(legalMode.get()){
            //the 300ms limit or the legalMode
            if( !clickMany || lastInteractTimestamp + ( 5) < Tasks.getTick()){
                //todo: add legal mode selection, try interact to turn around
                lastInteractTimestamp = Tasks.getTick();
                Vec3d interactTarget = result.getBlockPos().toCenterPos();
                boolean useDelayMove = legalTargetingMode.getValue() == Configs.LegalTargetingMode.DELAY_MOVEMENT;
                Vec3d interactLook =  interactTarget.add(
                    interactOffsetRand.nextDouble(-0.05d, 0.05d),
                    interactOffsetRand.nextDouble(-0.05d, 0.05d),
                    interactOffsetRand.nextDouble(-0.05d, 0.05d)
                );
                Vec3d cacheDirection = interactLook.subtract(mc.player.getEyePos()).normalize();
                Vec2f pitchYaw = EntityUtils.rotationToPitchYaw(cacheDirection);
                
                if(!useDelayMove && legalTargetingMode.getValue() == Configs.LegalTargetingMode.USEITEM_PACKET){
                    //find a hand which contains a item
                    //do not pass grimac
                    //will consume packet-limit, shit
                    Hand hand;
                    if(!mc.player.getMainHandStack().isEmpty()){
                        hand = Hand.MAIN_HAND;
                    } else if (!mc.player.getOffHandStack().isEmpty())
                    {
                        hand = Hand.OFF_HAND;
                    }else {
                        //try
                        hand = null;
                    }
                    if(hand != null){

                        for(int i=0 ; i< (clickMany?  CLICK_RATE.get(): 1); ++i){
                            mc.interactionManager.sendSequencedPacket(mc.world, (z)-> new PlayerInteractItemC2SPacket(hand, z, pitchYaw.y, pitchYaw.x));
                            mc.interactionManager.sendSequencedPacket(mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,result, sequence)));
                        }
                        ClientAccess.of(mc).setCooldown(0);
                    }else{
                        useDelayMove = true;
                    }
                    //fallback situation
                }
                if(useDelayMove){
                    ClientPlayerAccess.of(mc.player).getLegalMovementManager().addMovementModifier( new LegalMovementManager.MovementModifier(){
                        @Override
                        public int priority(){
                            return -10000000;
                        }

                        @Override
                        public boolean mayModifyRotation() {
                            return true;
                        }

                        @Override
                        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                            ClientPlayerEntity args = movementManagerEvent.context().playerStatus.entity;
//                        float pitch = args.getPitch();
//                        float yaw = args.getYaw();
                            EntityUtils.setEntityPitchSafe(args, pitchYaw.x);
                            EntityUtils.setEntityYawSafe(args, pitchYaw.y);
                        }

                        @Override
                        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                            //enable delay execute!
                            if(!enabledThisTick)return true;
                            for(int i=0 ; i< (clickMany?  CLICK_RATE.get(): 1); ++i){
                                mc.interactionManager.sendSequencedPacket(mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,result, sequence)));
                            }
                            ClientAccess.of(mc).setCooldown(0);
                            movementManagerEvent.context().playerStatus.restoreRotation();

//                        taskQueue.pollFirst();
//                        var nextTask = taskQueue.peekFirst();
//                        if(nextTask != null){
//                            Tasks.scheduleDelayed(()->ClientPlayerAccess.of(mc.player).getLegalMovementManager().addMovementModifier(nextTask), 0);
//                        }
                            return false;
                        }
                    });
                }

                //tasks will execute one by one: why?
                // we return kept if modify is not enabled in one tick
                //only one will run in a tick

//                var tickWrapper = new ProgressWrapper<ClientPlayerEntity>() {
//                    float pitch ;
//                    float yaw;
//                    @Override
//                    public void preProgress(ClientPlayerEntity args) {
//                        //step back our position
//
//                    }
//
//                    @Override
//                    public void postProgress(ClientPlayerEntity args) {
//                        for(int i=0 ; i< (clickMany?  CLICK_RATE.get(): 1); ++i){
//                            mc.interactionManager.sendSequencedPacket(mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,result, sequence)));
//                        }
//                        ClientAccess.of(mc).setCooldown(0);
//                        EntityUtils.setEntityYawSafe(args, this.yaw);
//                        args.setPitch(this.pitch);
//                        taskQueue.pollFirst();
//                        var nextTask = taskQueue.peekFirst();
//                        if(nextTask != null){
//                            Tasks.scheduleDelayed(()->EntityAccess.of(args).addTickWrapper(nextTask), 0);
//                        }
//                    }
//                    @Override
//                    public boolean stillWrap(ClientPlayerEntity args) {
//                        return false;
//                    }
//                };

//                if(taskQueue.isEmpty()){
//                    EntityAccess.of(mc.player).addTickWrapper(
//                        tickWrapper
//                    );
//                }
//                taskQueue.addLast(tickWrapper);
            }else {
                Debug.chat(Text.literal("[anti-grim] 你点的太快了,可能无法通过反作弊"));
            }
        }else{
            for(int i=0 ; i< (clickMany?  CLICK_RATE.get(): 1); ++i){
                mc.interactionManager.sendSequencedPacket(mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,result, sequence)));
            }
            if(delayClick && clickMany){
                AtomicInteger count = new AtomicInteger(2);
                Tasks.scheduleRepeated(()->{
                    for(int i=0 ; i< CLICK_RATE.get(); ++i){
                        mc.interactionManager.sendSequencedPacket(mc.world, (sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,result, sequence)));
                    }
                    return count.decrementAndGet() <= 0 ;
                },3,4);
            }
            ClientAccess.of(mc).setCooldown(0);
        }

    }
    private static List<Pair<BlockPos, TileInventoryScreen>> screens = new ArrayList<>();
    private static int executeCursor = 0;
    public static void handleMultiBlockExecute(Screen executingScreen, boolean clickMany, boolean clickDouble){
        if(mc.player ==null ||!(executingScreen instanceof TileInventoryScreen tile) || tile.isVirtual() || tile.getWorld() != mc.world){
            return;
        }
        BlockPos pos = tile.getPos();
        Block block = tile.getBlockType();
        if(pos.toCenterPos().squaredDistanceTo(mc.player.getPos()) > 50){
            Debug.chat(Text.literal("[多方块执行] 你离着自动执行的多方块太远了,已关闭自动执行"));
            handleMultiBlockAutoExecuteToggle(tile, false);
            return;
        }
        //opening current Executing
        if(mc.currentScreen instanceof TileInventoryScreen tileExecute && Objects.equals(pos, tileExecute.getPos())){
            boolean hasItem = false;
            for (var slot: tileExecute.castHandled().getScreenHandler().slots){
                if(slot.inventory instanceof PlayerInventory){
                    break;
                }else if(!slot.getStack().isEmpty()){
                    hasItem = true;
                    break;
                }
            }
            //return if there is no item in the screen
            if(!hasItem)return;
        }

        boolean find = false;
        if(block == Blocks.DISPENSER || block == Blocks.DROPPER){
            for (var multiblock: check().MULTIBLOCK_REGISTRIES.values()){
                var optional = multiblock.getOptionalActionFromDispenser(mc.world, pos);
                if(optional.isEmpty())continue;
                find = true;
                for (var bp : optional){
                    BlockHitResult result = RaycastUtils.createHitResult(bp);
                    onClickBlockExecute(result, clickDouble, clickMany);
                }
            }
        }
        if(!find){
            Debug.chat(Text.literal("[多方块执行] 多方块结构与已记录的多方块无法匹配").formatted(Formatting.RED));
            handleMultiBlockAutoExecuteToggle(tile, false);
        }
    }
    public static void handleMultiBlockAutoExecuteClear(){
        Debug.chat(Text.literal("[自动多方块] 已清除 %d 个执行中多方块".formatted(screens.size())).formatted(Formatting.GREEN));
        screens.clear();
        executeCursor = 0;
    }
//    private static boolean AUTO_EXECUTE = false;
    public static boolean isMultiBlockAutoExecute(TileInventoryScreen screen){
        return screens.stream().anyMatch(i -> Objects.equals(screen.getPos(), i.getFirst()));
    }

    public static void handleMultiBlockAutoExecuteToggle(TileInventoryScreen screen, boolean val){
        if(screen.isVirtual()){
            Debug.chat(Text.literal("[自动多方块] 找不到该屏幕对应的方块位置"));
        }else{
            BlockPos pos = screen.getPos();
            screens.removeIf(i-> Objects.equals(i.getFirst(), pos));
            if(val){
                screens.add(Pair.of(pos, screen));
            }
            Debug.chat(Text.literal("[自动多方块] 已切换该屏幕的自动执行状态,目前有 %d 个自动执行中(长按下蹲以全部关闭)".formatted(screens.size())).formatted(Formatting.GREEN));
        }

    }

    public static Collection<MultiBlockWithLocation> getOptionalMultiBlocks(World world, BlockPos dispensor){
        Collection<MultiBlockWithLocation> ans = new HashSet<>();
        for (var multi : check().MULTIBLOCK_REGISTRIES.values()){
            var op = multi.lookup.lookup(world, dispensor);
            if(op != null && !op.isEmpty()){
                for (var ml: op){
                    ans.add(new MultiBlockWithLocation(multi, ml));
                }
            }
        }
        return ans;
    }
    public static Collection<SlimefunTasks.MultiBlockEntry> getOptionalMultiBlockTypes(World world, BlockPos dispensor){
        Collection<MultiBlockEntry> ans = new HashSet<>();
        for (var multi : check().MULTIBLOCK_REGISTRIES.values()){
            var op = multi.lookup.lookup(world, dispensor);
            if(op != null && !op.isEmpty()){
                ans.add(multi);
            }
        }
        return ans;
    }

    public static ItemStack GUIDE_ICON;
    public static ItemStack RTYPE_ICON ;
    public static ItemStack VTYPE_ICON ;
    public static ItemStack SAVED_ICON ;
    private static void initIcon(){
        ItemStack ICON;
        try {
            ICON = ItemStack.fromNbtOrEmpty(ItemStackUtils.registry(), StringNbtReader.parse( "{Count:1b,id:\"minecraft:enchanted_book\",tag:{CustomModelData:2200001,PublicBukkitValues:{\"slimefun:slimefun_guide_mode\":\"SURVIVAL_MODE\"},display:{Lore:['{\"text\":\"\"}','{\"extra\":[{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"yellow\",\"text\":\"右键 \"},{\"italic\":false,\"color\":\"dark_gray\",\"text\":\"⇨ \"},{\"italic\":false,\"color\":\"gray\",\"text\":\"浏览物品\"}],\"text\":\"\"}','{\"extra\":[{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"yellow\",\"text\":\"Shift + 右键 \"},{\"italic\":false,\"color\":\"dark_gray\",\"text\":\"⇨ \"},{\"italic\":false,\"color\":\"gray\",\"text\":\"打开 设置 / 关于\"}],\"text\":\"\"}'],Name:'{\"extra\":[{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"green\",\"text\":\"Slimefun 指南 \"},{\"italic\":false,\"color\":\"gray\",\"text\":\"(箱子界面)\"}],\"text\":\"\"}'}}}"));
        } catch (CommandSyntaxException e) {
            Debug.info("Icon deserialize failure");
            ICON = new ItemStack(Items.ENCHANTED_BOOK);
        }
        GUIDE_ICON = ICON;
        RTYPE_ICON = new ItemStack(Items.KNOWLEDGE_BOOK);
        VTYPE_ICON = new ItemStack(Items.CRAFTING_TABLE);
        SAVED_ICON = new ItemStack(Items.CHAIN_COMMAND_BLOCK);
    }


    public static boolean dataAvailable = false;
    public static void reloadData(){
        Debug.info("Reloading Slimefun Registry Data Base");
        DATA = null;
//        new SlimefunRegistryDataBase();
//        DATA.loadData();
        //run the data load task 10 sec later
        Tasks.scheduleDelayed(()->{

            if(dataAvailable)checkAsync();
        }, 200);
    }
    public static void saveImmediately(){
        if(DATA != null)DATA.saveData();
    }
    public static boolean scheduledSave(){
        CompletableFuture.runAsync(SlimefunTasks::saveImmediately);
        return false;
    }
    private static final ItemStack ITEM_NULL_TYPE = new ItemStack(Items.BARRIER);
    private static final ItemStack CRAFTING_TABLE = new ItemStack(Items.CRAFTING_TABLE);
    private static final int[] recipeSlots = {3,4,5,12,13,14,21,22,23};
    public static boolean handleLockedItem(ItemStack lockIcon){
        if(lockIcon.getItem()==Items.BARRIER){
            List<String> lore = ItemStackUtils.getLoreString(lockIcon);
            for (var str : lore){
                if(str.contains("已锁定")){

                    return true;
                }
            }
            return false;
        }return false;
    }
    private static final Config.FlagRef lockExisting = Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_LOCK_EXISTING);
    public static void delayUpdateGuideRecipe(GenericContainerScreen screen){
        if(mc.player == null)return;
        DefaultedList<Slot> slots = screen.getScreenHandler().slots;
        //brief judgement of recipe
        if(slots.size() >= 27 && slots.get(2).getStack().isEmpty() && slots.get(11).getStack().isEmpty() && slots.get(20).getStack().isEmpty() && slots.get(15).getStack().isEmpty() && slots.get(17).getStack().isEmpty() && slots.get(25).getStack().isEmpty() && !slots.get(16).getStack().isEmpty()){
            ItemStack stack = slots.get(16).getStack();
            String id = ItemStackUtils.getSfId(stack);
            int[] recipeSlots = {3,4,5,12,13,14,21,22,23};

            if(id != null){
                boolean shouldUpdate = false;

                if(check().ALL_RECIPE_ENTRY.containsKey(id)){

                    //存在这个,
                    SlimefunRecipeEntry entry = check().ALL_RECIPE_ENTRY.get(id);
                    if(entry.output().isEmpty() && !slots.get(16).getStack().isEmpty()){
                        shouldUpdate = true;
                    }else if(lockExisting.get()){
                        shouldUpdate = false;
                    } else{
                        ItemStackWithId[] ingredient = entry.ingredientEntry();
                        if(ingredient.length == 9){
                            for (int i=0 ;i<9 ;++i){
                                ItemStack stackI =  slots.get(recipeSlots[i]).getStack();
                                //if it is lock, return immediately
                                if(handleLockedItem(stackI))return;
                                if(!ItemStackUtils.matchItemWithoutLore(ingredient[i].stack(),stackI)){
                                    shouldUpdate = true;
                                    break;
                                }
                            }
                            //都是相同的,不进行update
                        }else {
                            shouldUpdate = true;
                        }
                    }
                }else {
                    for (int i=0 ;i<9 ;++i){
                        ItemStack stackI =  slots.get(recipeSlots[i]).getStack();
                        //if it is lock, return immediately
                        if(handleLockedItem(stackI))return;
                    }
                    //不存在这个
                    shouldUpdate = true;
                }
                if(shouldUpdate){
                    ItemStack rtypeIcon = slots.get(10).getStack();
                    String recipeTypeName = rtypeIcon.isEmpty()? "NULL_RECIPE": rtypeIcon.getName().getString().replace("§.", "");
                    check().validateRecipeType(recipeTypeName, rtypeIcon);

                    ItemStackWithId[] ingredient = new ItemStackWithId[9];
                    for (int i=0 ; i<9 ; ++i){
                        ingredient[i] = ItemStackWithId.ofNullable( slots.get(recipeSlots[i]).getStack().copy());
                    }
                    ItemStack output = slots.get(16).getStack().copy();
                    SlimefunRecipeEntry entry = new SlimefunRecipeEntry(recipeTypeName, id, ingredient, output);
                    check().putSlimefunEntry(entry);

                }
            }
        }
    }

    public static void renderSlimefunItemTask(){

    }

    public static void updateGuideRecipe(GenericContainerScreen screen){
        Tasks.addPacketCatcher(new Tasks.TimedPacketCatcher<InventoryS2CPacket>(InventoryS2CPacket.class, 20) {
            @Override
            public int catchPacket(InventoryS2CPacket packet) {
                if(packet.getSyncId() == screen.getScreenHandler().syncId){
                    //execute immediately after the update of menu
                    mc.executeSync(()->{
                        delayUpdateGuideRecipe(screen);
                    });
                    return REMOVAL & (~CANCEL);
                }
                return (~REMOVAL)& (~CANCEL);
            }
        });

    }
    private static final Identifier TEXTURE = new Identifier("minecraft","textures/gui/container/crafting_table.png");
    private static final Map<Slot, RenderRecipeRecord> CURRENT = new Reference2ReferenceOpenHashMap<>(4);
    private static HandledScreen<?> CURRENT_HANDLING_SCREEN;
    private static interface RenderRecipeRecord{
        default boolean render(DrawContext context, HandledScreen<?> screen){
            if(examine(context, screen)){
                startRender(context, screen);
            }
            return true;
        }
        public void disableRender(HandledScreen<?> screen);
        public boolean examine(DrawContext context, HandledScreen<?> screen);
        public void startRender(DrawContext context, HandledScreen<?> screen);
    }
    private static record RenderRecipeRecordImpl(int textureX, int textureY, int slotDepth, Set<Slot> extraSlots, HolderWithState<ButtonWidget> buttonHolder) implements RenderRecipeRecord{

        public void disableRender(HandledScreen<?> screen){
            HandledScreenAccess.of(screen).getExtraSlots().removeAll(extraSlots);
            if(buttonHolder.state && buttonHolder.val != null){
                HandledScreenAccess.of(screen).removeChildFrom(buttonHolder.val);
            }
        }
        public boolean examine(DrawContext context, HandledScreen<?> screen){
            return true;
        }
        public void startRender(DrawContext context, HandledScreen<?> screen){
            var accessContext =DrawContextAccess.of(context);
            MatrixStack matrics = accessContext.getMatrixStack();
            RenderSystem.enableDepthTest();
            matrics.push();
            matrics.translate(textureX, textureY ,200 + slotDepth);
            matrics.scale(SCALING, SCALING, SCALING);
            context.drawTexture(TEXTURE, 0,0, 0 , 28,15,120, 56,256, 256);
            matrics.pop();
            RenderSystem.disableDepthTest();
            HandledScreenAccess.of(screen).getExtraSlots().addAll(extraSlots);
            if(!buttonHolder.state ){
                buttonHolder.state = true;
                if(buttonHolder.val != null)
                    HandledScreenAccess.of(screen).addDrawableChildTo(buttonHolder.val);
            }
        }
    }
    private static record RenderRecipeRecordNoCache(Slot slot) implements RenderRecipeRecord{

        static final Text data = Text.literal( "暂无缓存数据").formatted(Formatting.BOLD);
        @Override
        public void disableRender(HandledScreen<?> screen) {

        }

        @Override
        public boolean examine(DrawContext context, HandledScreen<?> screen) {
            return HandledScreenAccess.of(screen).isSlotPointed(slot);
        }

        @Override
        public void startRender(DrawContext context, HandledScreen<?> screen) {
            TextRenderer renderer = HandledScreenAccess.of(screen).getTextRenderer();
            var accessContext =DrawContextAccess.of(context);
            RenderSystem.enableDepthTest();
            MatrixStack matrics = accessContext.getMatrixStack();
            matrics.push();
            matrics.translate(0,0f, 500);
            var position = matrics.peek().getPositionMatrix();
            renderer.draw(data, slot.x + 10 - renderer.getWidth(data), slot.y - 6 - 3, Colors.RED, false, position, accessContext.getVertexConsumers(), TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 15728880);
            matrics.pop();
            RenderSystem.disableDepthTest();
        }
    }


    private static final int EXTRA_DEPTH = 256;
    private static final float SCALING = 0.8f;
//    private static final int[] RECIPE_SLOT_DX = {
//        1, 1 + (int)(18*SCALING), 1 + 2*(int)(18*SCALING),
//        1, 1 + (int)(18*SCALING), 1 + 2*(int)(18*SCALING),
//        1, 1 + (int)(18*SCALING), 1 + 2*(int)(18*SCALING),
//    };
//    private static final int[] RECIPE_SLOT_DY = {
//        1, 1, 1,
//        1 + (int)(18*SCALING), 1 + (int)(18*SCALING), 1 + (int)(18*SCALING),
//        1 + 2*(int)(18*SCALING),1 + 2*(int)(18*SCALING),1 + 2*(int)(18*SCALING)
//    };
    //renderingSL

    private static final Ingredient EMPTY =Ingredient.EMPTY;
    private static void releaseAllDisplayRecipe(){
        if(!CURRENT.isEmpty()){
            if(CURRENT_HANDLING_SCREEN != null){
                CURRENT.values().forEach(entry->entry.disableRender( CURRENT_HANDLING_SCREEN));
            }
            CURRENT.clear();
        }
        CURRENT_HANDLING_SCREEN = null;
    }
    private static void releaseSlotRecipe(Slot slot){
        var current = CURRENT.remove(slot);
        if(current != null && CURRENT_HANDLING_SCREEN != null){

            current.disableRender(CURRENT_HANDLING_SCREEN);
        }
        if(CURRENT.isEmpty()){
            releaseAllDisplayRecipe();
        }
    }
    public static Ingredient[] transfer3x3RecipeDisplay(RecipeTasks.RecipeRecord recipeRecord){
        return recipeRecord.ingredients();
    }
    public static Ingredient[] transfer3x3RecipeDisplay(Recipe<?> instance, Ingredient[] ingred){

        Ingredient[] ingredients = new Ingredient[9];

        if(instance instanceof ShapedRecipe shaped){
            List<Ingredient> raw = shaped.getIngredients();
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            for (int i=0; i< 3; ++i){
                for(int j = 0; j< 3; ++j){
                    if(i < height && j < width){
                        ingredients[3*i + j] = raw.get(width * i + j);
                    }else {
                        ingredients[3*i + j] = EMPTY;
                    }
                }
            }
        }else {
            Ingredient[] raw = ingred;
            System.arraycopy(raw, 0, ingredients, 0, raw.length);
            for (int i=raw.length; i<9 ;++i){
                ingredients[i] = EMPTY;
            }
        }
        return ingredients;
    }
    //one recipe screen
    public static void handleOpenRecipeEntryScreen(RecipeEntry recipe){
        if(handleNotEnable())return;
        openOrSwitch( SlimefunEntryListScreen.recipeEntry(List.of(recipe)));
    }
    //typed screen
    public static void handleOpenCraftingTypeScreen(CraftingType type){
        if(handleNotEnable())return;
        openOrSwitch(SlimefunEntryListScreen.recipeEntry(check().ALL_RECIPE_ENTRY.values()
            .stream()
            .filter(i->Objects.equals(i.rid, type.id))
            .map(RecipeEntry.class::cast)
            .toList()
        ));
    }
    public static void handleAutoEnable(){
        ENABLE_RECIPE.set(true);
        Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_RECIPE_SAVE).set(true);
        Configs.SLIMEFUN_CONFIG.save();
        Debug.chat("配方自动记录功能已开启,请使用ctrl+G打开Slimefun settings设置具体参数");
        Debug.chat(Text.literal("注意: 在1.20.5以上的物品数据和1.20.4及以下不互通,如果你进入了via支持的服务器,请注意这一点!").formatted(Formatting.YELLOW));
    }
    private static boolean reject = false;
    public static void handleRejectEnable(){
        Debug.chat("您仍旧可以继续使用GUIDE功能,在这次启动中该弹窗将不再弹出");
        reject = true;
    }
    private static final Text QUESTION_NOT_ENABLE = Text.literal("您当前并未启用配方记录功能,无法体验完整版GUIDE功能,请问您该如何选择?");
    private static final List<QuestionScreen.Solution> QUESTION_SOLUTIONS = List.of(
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,一键启用"), SlimefunTasks::handleAutoEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,但不启用"), SlimefunTasks::handleRejectEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,一键启用"), SlimefunTasks::handleAutoEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,但不启用"), SlimefunTasks::handleRejectEnable),
        QuestionScreen.Solution.of(Text.literal("我已知晓该功能,一键启用"), SlimefunTasks::handleAutoEnable)
    );
    public static boolean handleNotEnable(){
        //没有启用recipe或者没有启用
        if((!ENABLE_RECIPE.get()|| !Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_RECIPE_SAVE).get() )&& !reject){
            openOrSwitch(new QuestionScreen(QUESTION_NOT_ENABLE, QUESTION_SOLUTIONS));
            return true;
        }
        return false;
    }
    //vanilla typed screen
    //optimize vanilla type display

    public static void handleOpenVanillaTypeScreen(RecipeType type){
        if(handleNotEnable())return;
        openOrSwitch(SlimefunEntryListScreen.recipeEntry(RecipeTasks.getAllRecipe().values()
            .stream()
            .filter(i->Objects.equals(type, i.type()))
            .map(RecipeEntry.class::cast)
            .toList())
        );
    }

    private static final Text TITLE_ALL_ITEM = Text.literal("全部记录物品");
    public static final List<Text> TOOLTIPS_ITEM_RULE =  List.of(
        Text.literal("左键查看当前物品合成表"),
        Text.literal("右键查看包含当前物品的合成表"),
        Text.literal("Shift点击的时候会同时显示原版物品配方")
    );
    private static final Text TITLE_ALL_TYPE = Text.literal("全部记录配方类型");
    private static final Text TITLE_ALL_VANILLA =Text.literal("全部原版配方");
    private static final Text TITLE_ALL_SAVED = Text.literal("全部保存物品");
    public static final List<Text> TOOLTIPS_SAVED_RULE =  List.of(
        Text.literal("左键获得一组该物品(仅限创造)"),
        Text.literal("shift左键拷贝/give指令"),
        Text.literal("右键打开物品编辑器")
    );
    public static void openOrSwitch(Screen sf){
        ScreenAccess access = ScreenAccess.of(sf);
        if(mc.currentScreen instanceof SlimefunEntryListScreen<?> sf2 ){
            //当前正在预览配方;,如果要切换到其他配方,使用水平切换
            if(sf instanceof SlimefunEntryListScreen<?>){
                //同级之间水平切换
                access.switchFromCurrent();
            }else if(sf instanceof SlimefunChoiceScreen<?> choosing) {
                //退出到上级,
                sf2.close();
                openOrSwitch(sf);
            }else{
                access.openFromCurrent();
            }
        }else if (mc.currentScreen instanceof SlimefunChoiceScreen<?> sf3){
            if(sf instanceof SlimefunChoiceScreen<?>){
                //同级之间切换
                access.switchFromCurrent();
            }else {
                access.openFromCurrent();
            }
        }else {
            access.openFromCurrent();
        }
    }


    //guide icon
    public static void handleClickGuideIcon(){
        if(handleNotEnable())return;
        openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_ITEM ,TOOLTIPS_ITEM_RULE, ()-> check().ALL_RECIPE_ENTRY.values()
            .stream()
            .toList(),
            (entry)-> new ExecutableWidget(0,0,16,16).setElementHandler(SlotElement.instance(entry.output.copyWithCount(1)).withInputHandler(InputHandler.isLeft(t->{
                if(t){
                    handleOpenRecipeEntryScreen(entry);
                }else {
                    handleClickItemStack(entry.output, false);
                }
            }))), SlimefunRecipeEntry::output
            ).setSearchFilter((BiPredicate<String, SlimefunRecipeEntry>)(BiPredicate) RECIPE_FILTER)
        );
    }

    public static Stream<RecipeEntry> getAllSlimefunRecipeEntry(){
        return check().ALL_RECIPE_ENTRY.values().stream().map(RecipeEntry.class::cast);
    }


    public static void handleClickSaveItemIcon(){
        if(handleNotEnable())return;
        openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_SAVED ,TOOLTIPS_SAVED_RULE, ()->check().SAVED_ITEM_ID
                .stream()
                .map(SlimefunTasks::byId)
                .toList(),
                (entry)-> new ExecutableWidget(0,0,16,16).setElementHandler(SlotElement.instance(entry.copyWithCount(1)).withInputHandler(InputHandler.isLeft(t->{
                    if(t){
                        if(Screen.hasShiftDown()){
                            InvTasks.copyGiveCommand(entry.copy());
                        }else {
                            if(mc.player != null && mc.player.isCreative()){
                                InvTasks.creativeAddItem(entry.copy(), 64);
                            }else {
                                Debug.chat(Text.literal("当前并不处于创造模式,无法获取保存物品!").formatted(Formatting.YELLOW));
                            }
                        }
                    }else {
                        openOrSwitch(SlimefunEntryListScreen.mapToWidget(
                            List.of(entry)
                        , (item)->new SavedItemWidget(0,0,item, null)));
                        //ItemEditTasks.openEditScreen(entry, (it)->{});
                    }
                }))), Function.identity()
            ).setSearchFilter(ITEM_FILTER)
        );
    }

    //rtype icon
    public static void handleClickRtypeIcon(){
        if(handleNotEnable())return;
        openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_TYPE,check().ALL_RECIPE_TYPE.values()
            .stream()
            .toList(),
            (ct)->new ExecutableWidget(0,0,16,16).setElementHandler(SlotElement.instance(ct.icon)
                .withInputHandler(InputHandler.isLeft(t->handleOpenCraftingTypeScreen(ct)))
            ),CraftingType::icon
            ).setSearchFilter(RTYPE_FILTER)
        );
    }
    //crafttable icon
    public static void handleClickCraftTableIcon(){
        if(handleNotEnable())return;
        // remake
        openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_VANILLA, TOOLTIPS_ITEM_RULE,()-> RecipeTasks.getAllRecipe().values().stream().toList(),
            (rp)-> new ExecutableWidget(0,0,16,16)
                .setElementHandler(
                    SlotElement.instance(rp.output().copyWithCount(1))
                        .withInputHandler(InputHandler.isLeft((r)->{
                            if(r){
                                handleOpenRecipeEntryScreen((RecipeEntry) rp);
                            }else{
                                handleClickItemStack(rp.output(), false);
                            }
                        }))
                )

                ,
                RecipeTasks.RecipeRecord::output
            ).setSearchFilter((BiPredicate<String, RecipeTasks.RecipeRecord>)(BiPredicate) RECIPE_FILTER)
        );
//        openOrSwitch(new SlimefunChoiceScreen<>(TITLE_ALL_VANILLA, Registries.RECIPE_TYPE.stream().toList(),
//            (rp)-> new ExecutableWidget(0,0,16,16).setElementHandler(
//                SlotElement.instance(SUPPORT_VANILLA_RTYPE.getOrDefault(Registries.RECIPE_TYPE.getId(rp).toString(), ITEM_NULL_TYPE)).withInputHandler(InputHandler.isLeft(t->handleOpenVanillaTypeScreen(rp)))
//                )
//            ),
//        );
    }
    //clicking action
    public static void handleClickItemStack(ItemStack item, boolean isLeft){
        if(handleNotEnable())return;
        if(item.isEmpty()){
            return;
        }
        List<RecipeEntry> resultToDisplay = new ArrayList<>();
        //logic remake
        boolean shiftDown = Screen.hasShiftDown();
        if(isLeft){
            //搞到当前物品的配方表
            //显示每个输出和当前物品相同的配方表。使用sfid匹配sf物品，弱匹配 匹配其他物品
            //shift点击的时候以itemtype匹配
            String sfid = ItemStackWithId.generateId(item);
            for (var re: check().ALL_RECIPE_ENTRY.values()){
                if(shiftDown){
                    if(Objects.equals(sfid, ItemStackWithId.generateId(re.output()))){
                        resultToDisplay.add(re);
                        continue;
                    }
                }else{
                    if(ItemStackUtils.matchItemWithout(re.output(), item, false, false, false)){
                        resultToDisplay.add(re);
                    }
                }

            }
            for (var re :RecipeTasks.getAllRecipe().values()){
                if(shiftDown){
                    if (re.output().isOf(item.getItem())){
                        resultToDisplay.add(re);
                        continue;
                    }
                }else{
                    if(ItemStackUtils.matchItemWithout(re.output(), item, false, false,false)){
                        resultToDisplay.add(re);
                    }
                }

            }
        }else {
            //显示每个输入中含有当前物品的配方表, 使用sfid匹配sf物品, 弱匹配 匹配其他物品
            //shift点击的时候以itemtype匹配
            String generatedId = ItemStackWithId.generateId(item);
            search:
            for (var re: check().ALL_RECIPE_ENTRY.values()){
                for (var ingre : re.ingredientEntry()){
                    if(shiftDown){
                        if(Objects.equals(generatedId, ingre.identifier)){
                            resultToDisplay.add(re);
                            continue search;
                        }
                    }else{
                        if(ItemStackUtils.matchItemWithout(item, ingre.stack(), false, false, false)){
                            resultToDisplay.add(re);
                            continue search;
                        }
                    }


                }
            }
            search:
            for (var re :RecipeTasks.getAllRecipe().values()){
                for (var ingre : re.ingredient()){
                    if(shiftDown){
                        if(ingre.test(item)){
                            resultToDisplay.add(re);
                            continue search;
                        }
                    }else{
                        if(!item.isEmpty()){
                            for(var matchingStack : ingre.getMatchingStacks()){
                                if(ItemStackUtils.matchItemWithout(matchingStack, item, false, false, false)){
                                    resultToDisplay.add(re);
                                    continue  search;
                                }
                            }
                        }
                    }
                }

            }

        }
        if(resultToDisplay.isEmpty()){
            return;
        }
        openOrSwitch(SlimefunEntryListScreen.recipeEntry(resultToDisplay));
    }
    //recipetype click action
    public static void handleClickRecipeTypeIcon(String type, boolean isLeft){
        if(handleNotEnable())return;
        RecipeType type1 = RecipeTasks.getById(type);
        List<RecipeEntry> myEntry;
        if(type1 != null){
            Map<Identifier, RecipeTasks.RecipeRecord> myCache = RecipeTasks.getAllRecipe();
            myEntry = myCache.values()       // RecipeTasks.getRecipeByType(type1)
                .stream()
                .filter(i-> i.type() == type1)
                .map(RecipeEntry.class::cast)
              //  .map(i->(RecipeEntry)myCache.get(i.id()))
              //  .filter(Objects::nonNull)
                .toList();
        }else {

            myEntry = check().ALL_RECIPE_ENTRY.values()
                .stream()
                .filter(i->i.rid.equals(type))
                .map(RecipeEntry.class::cast)
                .toList();

        }
        if(myEntry.isEmpty())return;
        openOrSwitch(SlimefunEntryListScreen.recipeEntry(myEntry));
    }

    public static List<RecipeEntry> getInventoryRelativeRecipes(Screen inventory, boolean hard){
        if(!(inventory instanceof HandledScreen<?> handled))return List.of();
        var handler = handled.getScreenHandler();
        var slots = handler.slots;
        Set<String> relatedIds = new HashSet<>();
        int size = slots.size();

        for (int i=0 ;i<size;++i){
            ItemStack item = slots.get(i).getStack();
            if(item != null && !item.isEmpty()){
                String optionalItemId = getSfIdOrNull(item);
                if(optionalItemId != null){
                    relatedIds.add(optionalItemId);
                }
            }
        }

        List<RecipeEntry> results = new ArrayList<>();
        // hard, may be obfuscated when at via data or when jeg shit occurs, but it may not influence the final result
        loop:
        for (var iter: check().ALL_RECIPE_ENTRY.values()){
            ItemStackWithId[] ingredients = iter.ingredientEntry();

            for (var ingre: ingredients){
                if(!ingre.stack.isEmpty()){
                    String id = getSfIdOrNull(ingre.stack);
                    if(id != null && relatedIds.contains(id)){
                        //soft accept
                        if(!hard){
                            results.add(iter);
                            break;
                        }
                    }else {
                        //非空但id不在
                        if(hard){
                            //只有严格匹配才会直接跳过
                            continue loop;
                        }
                    }
                }
            }
            //ingre全部通过了id hard才接受
            if(hard){
                results.add(iter);
            }
        }
        return results;
    }

    @ApiMethod
    public static InvTasks.SlotMatchingResult getItemStackMatchingSlot(ScreenHandler screen, ItemStack stack, boolean weakMatch, int... slots){
        if(stack.isEmpty()){
            return InvTasks.getEmptySlots(screen, slots);
        }
        if(!weakMatch){
            return InvTasks.getItemStackMatchingSlot(screen, stack, slots);
        }
        var result = new InvTasks.SlotMatchingResult();
        ItemStack realStack = null;
        String sampleId = getSfIdOrNull(stack);
        var allSlots = screen.slots;
        for (int i : slots){
            Slot slot = allSlots.get(i);
            if(slot != null && slot.inventory instanceof PlayerInventory && !slot.getStack().isEmpty() ){
                if(realStack != null){
                    if( ItemStack.areItemsAndComponentsEqual(slot.getStack(), realStack)){
                        //all match
                        result.addMatchingSlot(i, slot);
                    }
                }else {
                    //the first match itemStack will be the realStack template
                    if(Objects.equals(sampleId,getSfIdOrNull(slot.getStack()) )){
                        realStack = slot.getStack();
                        result.setItemSample(realStack);
                        result.addMatchingSlot(i, slot);
                    }
                }

            }
        }
        return result;
    }
    @ApiMethod
    public static void moveSlimefunRecipePatternToContainer(RecipeEntry entry, ScreenHandler screen, int amount, boolean removeOrigin, int... acceptSlots){
        Preconditions.checkArgument(acceptSlots.length == 9);
        ItemStack[] ingredients = new ItemStack[9];
        Ingredient[] ingre = entry.ingredient();
        Preconditions.checkArgument(ingre.length <= 9);
        //try clear all items first;
//        var handler = screen.getScreenHandler();
//        DefaultedList<Slot> allSlots = handler.slots;
//        for (var i : acceptSlots){
//            Slot slot = allSlots.get(i);
//            if(!slot.getStack().isEmpty()){
//                InvTasks.quickMoveSlot(handler, i, true);
//            }
//        }
        for (var re = 0 ;re < ingre.length; ++re){

            Ingredient var = ingre[re];
            if(var.isEmpty()){
                ingredients[re] = ItemStack.EMPTY;
            }else {
                ingredients[re] = var.getMatchingStacks()[0];
            }
        }
        for (var re = ingre.length ;re < 9; ++re){
            ingredients[re] = ItemStack.EMPTY;
        }
        int[] playerInv = InvTasks.getPlayerInventorySlots(screen).toIntArray();
        InvTasks.moveRecipePatternToContainer(screen, ingredients, acceptSlots, amount, removeOrigin, ((screen1, itemStack) -> getItemStackMatchingSlot(screen1, itemStack, true, playerInv)));
    }



    public static void handleSaveItem(ItemStack item){
        if(handleNotEnable())return;
        String id = getOrAddId(item);
        if(check().SAVED_ITEM_ID.contains(id)){
            Debug.chat(Text.literal("该物品已经保存过了!").formatted(Formatting.YELLOW));
        }else {
            check().SAVED_ITEM_ID.add(id);
            Debug.chat(Text.literal("成功保存物品!").formatted(Formatting.GREEN));
        }
    }
    public static void handleRemoveSaveItem(ItemStack item){
        if(handleNotEnable())return;
        String id = getIdOrNull(item);
        if(id != null && check().SAVED_ITEM_ID.remove(id)){
            Debug.chat(Text.literal("已经成功移除这个保存物品").formatted(Formatting.GREEN));
        }
    }

    public static boolean clickToSaveItem(){
        ClientPlayerEntity player = mc.player;
        if(player==null)return false;

        ItemStack heldItem = ScreenUtils.getSelectingOrHandItem();

        if(heldItem != null && !heldItem.isEmpty()){
            handleSaveItem(heldItem);
            return true;
        }else if (heldItem != null){
            Debug.chat(Text.literal("不能保存空物品").formatted(Formatting.RED));
        }
        return false;
    }
//    public static boolean clickToAddRecipeDisplay(HandledScreen<?> screen){
//        //find if any
//        if(screen == null){
//            return false;
//        }
//        HandledScreenAccess access = HandledScreenAccess.of(screen);
//        var pair = ScreenUtils.getMouseCoord(mc);
//        int mouseX = pair.x;
//        int mouseY = pair.y;
//        Slot slot = null;
//        Set<Slot> extraSlots = access.getExtraSlots();
//        boolean hasRemoval = false;
//        if(!extraSlots.isEmpty()){
//            List<Slot> orderedExtra = extraSlots.stream().sorted(Comparator.comparingInt(s-> - ScaleSlotAccess.of(s).getExtraDepth())).toList();
//            int size = orderedExtra.size();
//            for (int i = 0 ; i < size; ++i){
//                Slot test = orderedExtra.get(i);
//                if(access.isSlotPointed(orderedExtra.get(i), mouseX, mouseY)){
//                    if(!CURRENT.containsKey(test)){
//                        slot = test;
//                        break;
//                    }else{
//                        releaseSlotRecipe(test);
//                        hasRemoval = true;
//                    }
//
//                }
//            }
//        }
//        if(slot == null){
//            Slot test = access.reallyGetSlotAt(mouseX, mouseY);
//            if(!CURRENT.containsKey(test) ){
//                slot= test;
//            }else if(test != null) {
//                releaseSlotRecipe(test);
//                hasRemoval = true;
//            }
//        }
//        if(slot == null)return hasRemoval;
//        //find pointed slot, check recipe
//        ItemStack stack = slot.getStack();
//        if(stack.isEmpty())return hasRemoval;
//        String id = ItemStackUtils.getSfId(stack);
//        int extraDepth = ScaleSlotAccess.of(slot).getExtraDepth() + EXTRA_DEPTH;
//        if(id == null ){
//            //render vanilla item recipe
//            Item itemType = stack.getItem();
//            var re = RecipeTasks.getRecipeByOutput(itemType);
//            RecipeTasks.RecipeRecord recipeRecord = re.values().stream().filter(i->i.type() == RecipeType.CRAFTING).findFirst().orElse(null);
//            if(recipeRecord == null){
//                return hasRemoval;
//            }
//            int x = slot.x;
//            int y = slot.y;
//            int startX = x - (int)(96*SCALING);
//            int startY = y - (int)(45 * SCALING);
//            Set<Slot> slots = new LinkedHashSet<>();
//            Ingredient[] val = transfer3x3RecipeDisplay(recipeRecord);
//
//            Inventory inventory = new MyIngredientImmutableInventory(val);
//            int i;
//            for ( i=0; i < 9 ; ++i){
//                Slot slotT = new CustomDisplaySlot(inventory,i,  startX  + ((int)(((i%3)*18 + 2)*SCALING)),  startY + ((int)(((i/3)*18 + 2)*SCALING)));
//                ScaleSlotAccess.of(slotT).setXYScale(SCALING).setExtraDepth(extraDepth);
//                slots.add(slotT);
//            }
//            ItemStack typeIcon = CRAFTING_TABLE;
//            Inventory outputInv = new SimpleInventory(new ItemStack[]{typeIcon, recipeRecord.output()});
//            Slot slotType = new CustomDisplaySlot(outputInv, 0,  startX + (int)( 64*SCALING), startY + (int)(20*SCALING));
//            ScaleSlotAccess.of(slotType).setXYScale(SCALING).setExtraDepth(extraDepth);
//            slots.add(slotType);
//            Slot slotOut = new CustomDisplaySlot(outputInv, 1,  startX + (int)( 96*SCALING), startY + (int)(20*SCALING) );
//            ScaleSlotAccess.of(slotOut).setXYScale(SCALING).setExtraDepth(extraDepth);
//            slots.add(slotOut);
//            var newEntry = new RenderRecipeRecordImpl(startX, startY, extraDepth, slots, HolderWithState.EMPTY_COMPLETE);
//            CURRENT.put(slot, newEntry);
//
//        }else {
//            //当前matrix: 物品栏右侧为x+, 下侧为y+
//            if(!check().ALL_RECIPE_ENTRY.containsKey(id)){
//                CURRENT.put(slot, new RenderRecipeRecordNoCache(slot));
//            }else {
//                SlimefunRecipeEntry entry = check().ALL_RECIPE_ENTRY.get(id);
//                Ingredient[] ingredient = entry.ingredient();
//                int x = slot.x;
//                int y = slot.y;
//                int startX = x - (int)(96*SCALING);
//                int startY = y - (int)(45 * SCALING);
//                Inventory inventory = new MyIngredientImmutableInventory(ingredient);
//                Set<Slot> slots = new LinkedHashSet<>();
//                for ( int i=0; i < 9 ; ++i){
//                    Slot slotT = new CustomDisplaySlot(inventory,i,  startX  + ((int)(((i%3)*18 + 2)*SCALING)),  startY + ((int)(((i/3)*18 + 2)*SCALING)));
//                    ScaleSlotAccess.of(slotT).setXYScale(SCALING).setExtraDepth(extraDepth);
//                    slots.add(slotT);
//                }
//                ItemStack typeIcon = check().ALL_RECIPE_TYPE.getOrDefault(entry.rid,CraftingType.EMPTY).icon;
//                ItemStack outputItem = entry.output;
//                Inventory outputInv = new SimpleInventory(new ItemStack[]{typeIcon, outputItem});
//                Slot slotType = new CustomDisplaySlot(outputInv, 0,  startX + (int)( 64*SCALING), startY + (int)(20*SCALING));
//                ScaleSlotAccess.of(slotType).setXYScale(SCALING).setExtraDepth(extraDepth);
//                slots.add(slotType);
//                Slot slotOut = new CustomDisplaySlot(outputInv, 1,  startX + (int)( 96*SCALING), startY + (int)(20*SCALING) );
//                ScaleSlotAccess.of(slotOut).setXYScale(SCALING).setExtraDepth(extraDepth);
//                slots.add(slotOut);
//                int ax = startX + (int)( 58*SCALING) + access.getScreenX();
//                int ay = startY + (int)(44*SCALING) + access.getScreenY();
//                ButtonWidget widget = ButtonWidget.builder(Text.literal("配方"),(b)->{
//                        handleOpenRecipeEntryScreen(entry);
//                    })
//                    .dimensions(ax, ay, (int)(28* SCALING), (int)(8*SCALING) )
//                    .build();
//                ClickableAccess.of(widget).setExtraDepth(extraDepth + 210);
//                CURRENT.put(slot, new RenderRecipeRecordImpl(startX, startY, extraDepth, slots, new HolderWithState<>(widget)));
//            }
//        }
//        CURRENT_HANDLING_SCREEN = screen;
//        return true;
//    }

    @Deprecated
    public static void renderSpecificRecipeCache(DrawContext context, HandledScreen screen, int mouseX, int mouseY, float delta){
        if(!ENABLE_RECIPE.get()){
            releaseAllDisplayRecipe();
            return;
        }
        if(!Screen.hasControlDown()){
            //if not control anyMore;
            releaseAllDisplayRecipe();
            return;
        }
        if(!CURRENT.isEmpty()){
            //keep rendering current logic
            //left not complete
            if(CURRENT_HANDLING_SCREEN != screen){
                releaseAllDisplayRecipe();
                return;
            }

            CURRENT.values().removeIf(i -> {
                if(!i.render(context, screen)){
                    i.disableRender(screen);


                    return true;
                }
                return false;
            });
            if(CURRENT.isEmpty()){
                releaseAllDisplayRecipe();
            }
            return;

        }

    }

    private static long lastAutoTick;
    public static void slimefunMultiBlockTick(ClientPlayerEntity player){
        if(!screens.isEmpty()){
            long currentMs = System.currentTimeMillis();
            //fixme: add configuration to delay 300MS
            if(currentMs > (lastAutoTick + (null == mc.currentScreen?2: 1 ) * 300)){
                if(mc.player != null && mc.player.isSneaking()){
                    Debug.chat(Text.literal("[自动多方块] 检测到长按下蹲,清除全部的执行中多方块"));
                    handleMultiBlockAutoExecuteClear();
                }else {
                    lastAutoTick = currentMs;

                    int cursorIndex = (++executeCursor)%screens.size();
                    var executeData = screens.get(cursorIndex);
                    if(executeData !=null && executeData.getSecond() instanceof TileInventoryScreen holder){
                        if(!holder.isVirtual() && holder.getBlockType() == Blocks.DISPENSER){
                            //当玩家关闭界面但并没有取消的时候,以低速运行
                            handleMultiBlockExecute(holder.castHandled(),true, false);
                        }
                    }else {
                        Debug.chat(Text.literal("[自动多方块] 当前执行的界面并没有位置记录,已自动移除"));
                        screens.remove(cursorIndex);
                        executeCursor -= 1;
                    }
                }
            }


        }
    }


    private static final Config.StringRef TITLE_PATTERN = Configs.SLIMEFUN_CONFIG.getString(Configs.SLIMEFUN_RECIPE_TITLE);
    private static final Config.FlagRef ENABLE_RECIPE = Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_RECIPE_RECORD);
    private static final Config.FlagRef RECIPE_LOCK = Configs.SLIMEFUN_CONFIG.getBoolean(Configs.SLIMEFUN_RECIPE_LOCKED);
    private static final Config.StringRef MULTIBLOCK_PATTERN = Configs.SLIMEFUN_CONFIG.getString(Configs.SLIMEFUN_MULTIBLOCK_MATCHER);
    private static Pattern TITLE_REGEX;
    private static Pattern MULTIBLOCK_REGEX;
    public static void listenGuideRecipe(HandledScreen<?> screen){
        if(!ENABLE_RECIPE.get())return;
        //prevent from shit via data
        if(RECIPE_LOCK.get())return;
        if(!SCREEN_TYPES.contains( screen.getScreenHandler().getType())){
            return;
        }

        if(!(screen instanceof GenericContainerScreen generic)){
            return;
        }

        Text title = screen.getTitle();
        if(title == null ){
            return;
        }
        String texts1 = title.getString();
        if(texts1 == null){
            return;
        }
        texts1 = texts1.replaceAll("§.", "");
        if(TITLE_REGEX.asMatchPredicate().test(texts1)){
            updateGuideRecipe(generic);
        }
    }
    private static final Set<?> SCREEN_TYPES = Set.of(ScreenHandlerType.GENERIC_9X6, ScreenHandlerType.GENERIC_9X3, ScreenHandlerType.GENERIC_9X4,ScreenHandlerType.GENERIC_9X5);
    static{
        TITLE_PATTERN.addUpdateListenerWithUpdate((str)->{
            TITLE_REGEX = Pattern.compile(str);
        });

        MULTIBLOCK_PATTERN.addUpdateListenerWithUpdate((str)->{
            MULTIBLOCK_REGEX = Pattern.compile(str);
        });
        Tasks.scheduleDelayed(()->{
            //post init tasks
            Debug.info("Running Slimefun Post Setup Tasks");
            initIcon();
        }, 1);

        //定时保存
        ScheduleService.launchAsyncRepeatTask(SlimefunTasks::saveImmediately, 1000 * 60, 1000 * 60 * 5);
//        Tasks.scheduleRepeated(SlimefunTasks::scheduledSave, 20* 60, 20*60* 5);
        //退出服务器时保存
        Listener.getServerDisconnectPoint().registerHandler((v)->{
            Debug.info("Save Slimefun Data...");
            SlimefunTasks.saveImmediately();
            dataAvailable = false;
        });
        Listener.getGameJoinPoint().registerHandler((v)->{
            dataAvailable = true;
            SlimefunTasks.reloadData();
        });
        Listener.getScreenOpenPoint().registerHandler(SlimefunTasks::listenGuideRecipe);
//        RenderMain.getRenderHandledScreen().registerHandler((ev)->{
//            SlimefunTasks.renderSpecificRecipeCache(ev.context(), (HandledScreen) ev.extraArgs[0], (Integer) ev.extraArgs[1], (Integer) ev.extraArgs[2], (Float)ev.extraArgs[3]);
//        });
        Listener.getPostPlayerUseItemAtBlock().registerHandler(SlimefunTasks::onClickBlock);

        Tasks.registerGameTask(SlimefunTasks::slimefunMultiBlockTick);

    }
    public static record CraftingType(String id, ItemStack icon){
        public static final CraftingType EMPTY = new CraftingType("NULL", ITEM_NULL_TYPE);
    }
    public static BiPredicate<String, SlimefunTasks.CraftingType> RTYPE_FILTER = (str, i)->nameMatch(i.id, str);

    public interface RecipeEntry{
        public String rid();
        public String id();
        public Ingredient[] ingredient();
        public ItemStack output();
    }

    public static record SlimefunRecipeEntry(String rid, String id, ItemStackWithId[] ingredientEntry, @NonNull ItemStack output) implements RecipeEntry{
        public ItemStack[] inputs(){
            ItemStack[] itemStacks = new ItemStack[9];
            for (int i=0; i<ingredientEntry.length; ++i){
                itemStacks[i] = ingredientEntry[i].stack;
            }
            for (int i = ingredientEntry.length; i<9 ;++i){
                itemStacks[i] = ItemStack.EMPTY;
            }
            return itemStacks;
        }
        public Ingredient[] ingredient(){
            Ingredient[] items = new Ingredient[9];
            for (int i=0; i<ingredientEntry.length; ++i){
                items[i] = Ingredient.ofStacks( ingredientEntry[i].stack()) ;
            }
            for (int i = ingredientEntry.length; i<9 ;++i){
                items[i] = Ingredient.EMPTY;
            }
            return items;
        }

        @Override
        public String toString() {
            return "SlimefunRecipeEntry[ rid = "+rid +" , id = "+id +" , ingredient = "+Arrays.asList(ingredientEntry).toString() + ", output = "+ output +" ]";
        }
    }

    public static class SlimefunCommands extends AbstractMainCommand{
        public SubCommand main = genMainCommand("sf");
        public SubCommand give = new SubCommand("give", genArgument("id","amount"), "sf give <id> <amount:default 1> 获取粘液物品(以指令形式)"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String id = re.nextNonnull();
                if(check().ALL_RECIPE_ENTRY.containsKey(id)){
                    SlimefunRecipeEntry entry = check().ALL_RECIPE_ENTRY.get(id);
                    ItemStack itemStack = entry.output().copyWithCount(re.nextInt());
                    String giveCommand = InvTasks.createGiveCommand(itemStack);
                    ChatTasks.sendMessage(giveCommand, true);
                }else {
                    Debug.chat("不存在的id: ", id);
                }
                return true;
            }
        }
            .setTabCompletor("id", ()->check().ALL_RECIPE_ENTRY.keySet().stream().toList())
            .setInt("amount", 1)
            .register(this);
        public SubCommand view = new SubCommand("view", genArgument("id"), "sf view <id> 打开对应物品的配方展示页面"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String id = re.nextNonnull();
                if(check().ALL_RECIPE_ENTRY.containsKey(id)){
                    SlimefunRecipeEntry entry = check().ALL_RECIPE_ENTRY.get(id);
                    if(entry != null)
                        handleOpenRecipeEntryScreen(entry);
                    else
                        Debug.chat("未知错误!");
                }else {
                    Debug.chat("不存在的id: ", id);
                }
                return true;
            }
        }
            .setTabCompletor("id", ()->check().ALL_RECIPE_ENTRY.keySet().stream().toList())
            .register(this);
    }
    static{
        ChatTasks.registerSubCommands("sf", SlimefunCommands::new);
    }
}
