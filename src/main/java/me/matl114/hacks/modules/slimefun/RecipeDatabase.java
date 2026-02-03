package me.matl114.hacks.modules.slimefun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ReferenceArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.multiblock.BlockMatcher;
import me.matl114.hacks.utils.recipes.RecipeEntry;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigLoader;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.events.Event;
import me.matl114.utils.itemdb.ItemStackData;
import me.matl114.utils.itemdb.ItemStackDataWithAmount;
import me.matl114.utils.collections.Point;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;



public class RecipeDatabase extends BaseModule {
    public static final String[] SLIMEFUN_RECIPE_TITLE = {"recipe-record","rp-title"};
    public static final String[] SLIMEFUN_MULTIBLOCK_MATCHER = {"recipe-record", "multiblock-pattern"};
    public static final String[] SLIMEFUN_RECIPE_RECORD = {"recipe-record","enable"};
    public static final String[] SLIMEFUN_RECIPE_LOCKED = {"recipe-record", "lock-current-data"};
    public static final String[] SLIMEFUN_RECIPE_SAVE = {"recipe-record","save-data"};
    public static final String[] SLIMEFUN_LOCK_EXISTING = {"recipe-record", "lock-existing"};


    public RecipeDatabase() {

    }

    public final FlagRef enable = flagBuilder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_RECIPE_RECORD)
        .build();

    public final FlagRef saveData = builder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_RECIPE_SAVE, FlagRef.TYPE)
        .defaultValue(true)
        .build();

    public final FlagRef lockExistingData = flagBuilder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_LOCK_EXISTING)
        .build();

    public final FlagRef lockCurrentData = flagBuilder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_RECIPE_LOCKED)
        .build();



    public final StringRef multiBlockRecipeType = builder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_MULTIBLOCK_MATCHER, StringRef.TYPE)
        .defaultValue("^(多方块结构|MultiBlock)$")
        .validator(Configs.REGEX_VALIDATOR)
        .build();

    public final StringRef slimefunBookTitle = builder(Configs.SLIMEFUN_CONFIG, SLIMEFUN_RECIPE_TITLE, StringRef.TYPE)
        .defaultValue("^(Slimefun 指南.*)$")
        .validator(Configs.REGEX_VALIDATOR)
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        // specific this database
        registerListener(InvTasks.getCustomItemDatabase().getItemDataBaseLoad(), (ev) -> {
            onLoad();
        });
        registerListener(InvTasks.getCustomItemDatabase().getItemDataBaseSave(), (ev) -> {
            onSave();
        });
        registerListener(InvTasks.getCustomItemDatabase().getItemDataBaseUnload(), (ev) -> {
            onUnload();
        });
        //load lately
        id2CraftType.clear();
        id2Recipe.clear();
        if(InvTasks.getCustomItemDatabase().isLoaded()){
            onLoad();
        }
        registerListener(Listener.getPacketPostHandlePoint().getChannel(OpenScreenS2CPacket.class), this::onScreenOpen);
    }
    @Getter
    boolean loaded = false;

    public void ensureLoaded(){
        if(!loaded){
            //trigger load
            InvTasks.getCustomItemDatabase().getAccess();
        }
    }

    private static final String RECIPE_FILE = "sfhelper-configs/recipes/recipe-data.json";
    private static final String RECIPE_TYPE_FILE = "sfhelper-configs/recipes/craft-types.json";
    private final Map<String, CraftingType> id2CraftType = new LinkedHashMap<>();
    private boolean dirtyCraftType = false;
    private final Map<String, SlimefunRecipeEntry> id2Recipe = new LinkedHashMap<>();
    private boolean dirtyRecipe = false;
    private final Codec<Map<String, CraftingType>> craftTypeMapCodec = Codec.unboundedMap(Codec.STRING, CraftingType.CODEC);
    private final Codec<Map<String, SlimefunRecipeEntry>> recipeMapCodec = Codec.unboundedMap(Codec.STRING, SlimefunRecipeEntry.CODEC);
    Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();
    public void onLoad(){
        loaded =  true;
        Debug.chat("载入粘液物品记录中...");
        try{
            id2CraftType.clear();
            String craftTypeId = ConfigLoader.loadExternalJson(RECIPE_TYPE_FILE);
            JsonObject jsonElement = gson.fromJson(craftTypeId, JsonObject.class);
            Map<String, CraftingType> craftTypeMap = craftTypeMapCodec.decode(JsonOps.INSTANCE, jsonElement).getOrThrow().getFirst();
            id2CraftType.putAll(craftTypeMap);
            dirtyCraftType = false;
        }catch (Throwable e){
            Debug.info("反序列化RecipeTypes数据失败, 错误:");
            Debug.info(e);
        }
        try{
            id2Recipe.clear();
            String craftTypeId = ConfigLoader.loadExternalJson(RECIPE_FILE);
            JsonObject jsonElement = gson.fromJson(craftTypeId, JsonObject.class);
            Map<String, SlimefunRecipeEntry> craftTypeMap = recipeMapCodec.decode(JsonOps.INSTANCE, jsonElement).getOrThrow().getFirst();
            id2Recipe.putAll(craftTypeMap);
            dirtyRecipe = false;
        }catch (Throwable e){
            Debug.info("反序列化RecipeEntry数据失败, 错误:");
            Debug.info(e);
        }
        resetMultiblockRegistry();
        id2Recipe.forEach((k,v)->{
            if(Pattern.matches(multiBlockRecipeType.get(), v.rid)){
                addToMultiblockRegistry(v);
            }
        });
    }

    public void onSave(){
        if(saveData.get()){
            if(dirtyCraftType){
                dirtyCraftType = false;
                try{
                    JsonElement jsonElement = craftTypeMapCodec.encodeStart(JsonOps.INSTANCE, id2CraftType).getOrThrow();
                    CompletableFuture.runAsync(()->{
                        String jsonStr = gson.toJson(jsonElement);
                        try {
                            ConfigLoader.saveToFile(RECIPE_TYPE_FILE, jsonStr);
                        } catch (IOException e) {
                            Debug.info(e);
                        }
                    });
                }catch (Throwable e){
                    Debug.info("序列化RecipeTypes数据失败, 错误:");
                    Debug.info(e);
                }

            }
            if(dirtyRecipe){
                dirtyRecipe = false;
                try{
                    JsonElement jsonElement = recipeMapCodec.encodeStart(JsonOps.INSTANCE, id2Recipe).getOrThrow();
                    CompletableFuture.runAsync(()->{
                        String jsonStr = gson.toJson(jsonElement);
                        try {
                            ConfigLoader.saveToFile(RECIPE_FILE, jsonStr);
                        } catch (IOException e) {
                            Debug.info(e);
                        }
                    });
                }catch (Throwable e){
                    Debug.info("序列化RecipeEntry数据失败, 错误:");
                    Debug.info(e);
                }
            }
        }
    }

    public void onUnload(){
        loaded = false;
    }

    private void putInternal(SlimefunRecipeEntry entry){
        id2Recipe.put(entry.id, entry);
        dirtyRecipe = true;
        if(Pattern.matches(multiBlockRecipeType.get(), entry.rid)){
            addToMultiblockRegistry(entry);
        }
    }
    private void putRecipeType(String rid, ItemStack icon){
        if(!id2CraftType.containsKey(rid)){
            ItemStack icon2 = icon.isEmpty()? ITEM_NULL_TYPE.copy(): icon.copy();
            icon2.setCount(1);
            id2CraftType.put(rid, new CraftingType(rid, ItemStackDataWithAmount.of(icon2)));
            dirtyCraftType = true;
        }
    }

    public void putSlimefunEntry(SlimefunRecipeEntry entry){
        ensureLoaded();
        putInternal(entry);
    }
    public void validateRecipeType(String rid, ItemStack icon){
        ensureLoaded();
        putRecipeType(rid, icon);
    }
    private static final Set<?> SCREEN_TYPES = Set.of(ScreenHandlerType.GENERIC_9X6, ScreenHandlerType.GENERIC_9X3, ScreenHandlerType.GENERIC_9X4,ScreenHandlerType.GENERIC_9X5);
    public void onScreenOpen(Event<OpenScreenS2CPacket> event){
        if(mc.player == null)return;
        HandledScreen screen = ClientPlayerAccess.of(mc.player).getServerOpeningScreen();
        if(screen == null || screen.getScreenHandler().syncId != event.context.getSyncId()){
            return;
        }
        if(this.enable.get() && !lockCurrentData.get() && SCREEN_TYPES.contains(screen.getScreenHandler().getType()) && screen instanceof GenericContainerScreen container && screen.getTitle() != null){
            String title = screen.getTitle().getString();
            if(title != null){
                title = title.replaceAll("§.", "");
                if(Pattern.matches(multiBlockRecipeType.get(), title)){
                    Tasks.addPacketCatcher(new Tasks.TimedPacketCatcher<InventoryS2CPacket>(InventoryS2CPacket.class, 20) {
                        @Override
                        public int catchPacket(InventoryS2CPacket packet) {
                            if(packet.getSyncId() == container.getScreenHandler().syncId){
                                //execute immediately after the update of menu
                                mc.executeSync(()->{
                                    onScreenContent(container);
                                });
                                return REMOVAL & (~CANCEL);
                            }
                            return (~REMOVAL)& (~CANCEL);
                        }
                    });

                }
            }
        }

    }

    private static final int[] recipeSlots = {3,4,5,12,13,14,21,22,23};

    public void onScreenContent(GenericContainerScreen screen){
        if(mc.player == null)return;
        DefaultedList<Slot> slots = screen.getScreenHandler().slots;
        //brief judgement of recipe
        if(slots.size() >= 27 && slots.get(2).getStack().isEmpty() && slots.get(11).getStack().isEmpty() && slots.get(20).getStack().isEmpty() && slots.get(15).getStack().isEmpty() && slots.get(17).getStack().isEmpty() && slots.get(25).getStack().isEmpty() && !slots.get(16).getStack().isEmpty()){
            ItemStack stack = slots.get(16).getStack();
            String id = ItemStackUtils.getSfId(stack);

            if(id != null){
                boolean shouldUpdate = false;
                ensureLoaded();

                if(id2Recipe.containsKey(id)){

                    //存在这个,
                    SlimefunRecipeEntry entry = id2Recipe.get(id);
                    if(entry.output().isEmpty() && !slots.get(16).getStack().isEmpty()){
                        shouldUpdate = true;
                    }else if(lockExistingData.get()){
                        shouldUpdate = false;
                    } else{
                        ItemStack[] ingredient = entry.inputs();
                        if(ingredient.length == 9){
                            for (int i=0 ;i<9 ;++i){
                                ItemStack stackI =  slots.get(recipeSlots[i]).getStack();
                                //if it is lock, return immediately
                                if(isLockedItem(stackI))return;
                                if(!ItemStackUtils.matchItemWithoutLore(ingredient[i], stackI)){
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
                        if(isLockedItem(stackI))return;
                    }
                    //不存在这个
                    shouldUpdate = true;
                }
                if(shouldUpdate){
                    ItemStack rtypeIcon = slots.get(10).getStack();
                    String recipeTypeName = rtypeIcon.isEmpty()? "NULL_RECIPE": rtypeIcon.getName().getString().replace("§.", "");
                    validateRecipeType(recipeTypeName, rtypeIcon);

                    List<ItemStackDataWithAmount> ingredients = new ArrayList<>();
                    for (int i=0 ; i<9 ; ++i){
                        ingredients.add(ItemStackDataWithAmount.of(slots.get(recipeSlots[i]).getStack()));
                    }
                    ItemStackDataWithAmount output = ItemStackDataWithAmount.of(  slots.get(16).getStack());
                    SlimefunRecipeEntry entry = new SlimefunRecipeEntry(recipeTypeName, id, ingredients, output);
                    putSlimefunEntry(entry);
                }
            }
        }
    }
    public static boolean isLockedItem(ItemStack lockIcon){
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

    public CraftingType getCraftType(String rid){
        ensureLoaded();
        return id2CraftType.get(rid);
    }

    public SlimefunRecipeEntry getRecipeEntry(String id){
        ensureLoaded();
        return id2Recipe.get(id);
    }

    public Map<String, CraftingType> getId2CraftType(){
        ensureLoaded();
        return Collections.unmodifiableMap(id2CraftType);
    }

    public Map<String, SlimefunRecipeEntry> getId2Recipe(){
        ensureLoaded();
        return Collections.unmodifiableMap(id2Recipe);
    }

    public Map<String, MultiBlockEntry> getMultiBlockRegistry(){
        ensureLoaded();
        return Collections.unmodifiableMap(multiBlockRegistry);
    }

    public Set<MultiBlockEntry> getPotentialMultiBlocks(Block block){
        ensureLoaded();
        return multiBlockIndexedByBlockPotentials.getOrDefault(block, Set.of());
    }




    public static final ItemStack ITEM_NULL_TYPE = new ItemStack(Items.BARRIER);

    public static record CraftingType(String id, ItemStackDataWithAmount icon){
        public static final CraftingType EMPTY = new CraftingType("NULL", new ItemStackDataWithAmount(ItemStackData.wrapCopy(ITEM_NULL_TYPE), 1));

        public ItemStack iconStack(){
            return icon.getAsItemStack();
        }


        public static Codec<CraftingType> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.STRING.fieldOf("rid").forGetter(CraftingType::id),
                InvTasks.CUSTOM_AMOUNT_ITEM_DATA_CODEC.fieldOf("icon").forGetter(CraftingType::icon)
            ).apply(instance, CraftingType::new)
        );
    }
    @Accessors(fluent = true)
    public static class SlimefunRecipeEntry implements RecipeEntry {
        @Getter
        String rid;
        @Getter
        String id;
        public List<ItemStackDataWithAmount> getIngredientData(){
            return ingredients;
        }
        public ItemStackDataWithAmount getOutputData(){
            return output;
        }
        final List<ItemStackDataWithAmount> ingredients;
       final   ItemStackDataWithAmount output;
        final ItemStack[] finalizedIngredients;
       final   ItemStack finalizedOutput;

        public SlimefunRecipeEntry(String rid, String id, List<ItemStackDataWithAmount> ingredientEntry, @NonNull ItemStackDataWithAmount output){
            this.rid = rid;
            this.id = id;
            this.ingredients = List.copyOf(ingredientEntry);
            ItemStack[] itemStacks = new ItemStack[9];
            for (int i=0; i<ingredientEntry.size(); ++i){
                itemStacks[i] = ingredientEntry.get(i).getAsItemStack();
            }
            for (int i = ingredientEntry.size(); i<9 ;++i){
                itemStacks[i] = ItemStack.EMPTY;
            }
            finalizedIngredients = itemStacks;
            this.output = output;
            this.finalizedOutput = output.getAsItemStack();

        }



        public ItemStack[] inputs(){
            return finalizedIngredients;
        }
        public Ingredient[] ingredient(){
            Ingredient[] items = new Ingredient[9];
            for (int i=0; i < finalizedIngredients.length; ++i){
                items[i] = Ingredient.ofStacks( finalizedIngredients[i]) ;
            }
            for (int i = finalizedIngredients.length; i<9 ;++i){
                items[i] = Ingredient.EMPTY;
            }
            return items;
        }

        @Override
        public ItemStack output() {
            return finalizedOutput;
        }

        @Override
        public String toString() {
            return "SlimefunRecipeEntry[ rid = "+rid +" , id = "+id +" , ingredient = "+ ingredients.toString() + ", output = "+ output +" ]";
        }

        public static final Codec<SlimefunRecipeEntry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.STRING.fieldOf("rid").forGetter(SlimefunRecipeEntry::rid),
                Codec.STRING.fieldOf("id").forGetter(SlimefunRecipeEntry::id),
                Codec.list(InvTasks.CUSTOM_AMOUNT_ITEM_DATA_CODEC).fieldOf("ingredient").forGetter(SlimefunRecipeEntry::getIngredientData),
                InvTasks.CUSTOM_AMOUNT_ITEM_DATA_CODEC.fieldOf("output").forGetter(SlimefunRecipeEntry::getOutputData)
            ).apply(instance, SlimefunRecipeEntry::new)
        );
    }


    private Map<String, MultiBlockEntry> multiBlockRegistry = new Object2ReferenceOpenHashMap<>();

    private Map<Block, Set<MultiBlockEntry>> multiBlockIndexedByBlockPotentials = new Reference2ReferenceOpenHashMap<>();


    private void resetMultiblockRegistry(){
        if(multiBlockRegistry != null){
            multiBlockRegistry.clear();
        }
        multiBlockRegistry = new Object2ReferenceOpenHashMap<>();
        if(multiBlockIndexedByBlockPotentials != null){
            multiBlockIndexedByBlockPotentials = new Reference2ReferenceOpenHashMap<>();
        }
    }

    //private method
    private void addToMultiblockRegistry(SlimefunRecipeEntry entry){
        var newEntry = MultiBlockEntry.of(entry.inputs(), entry.id);
        if(newEntry == null)return;
        multiBlockRegistry.put(entry.id, newEntry);
        Set<Block> potentialTriggerBlocks = newEntry.getPotentials();
        for (Block block: potentialTriggerBlocks){
            multiBlockIndexedByBlockPotentials.computeIfAbsent(block, (b)->new ReferenceArraySet<>()).add(newEntry);
        }
    }


    public static record MultiBlockEntry(String id, BlockMatcher[] blockTypes, Collection<Point> optionalActionBlock, DispenserMultiBlockLookup lookup, boolean symm) implements BlockMatcher {
        public Set<Block> getPotentials(){
            Set<Block> blocks = new HashSet<>();
            blocks.addAll(blockTypes[1].getPotentials());
            blocks.addAll(blockTypes[4].getPotentials());
            blocks.addAll(blockTypes[7].getPotentials());
            return blocks;
        }
        private static final Tagged LOGS = new Tagged(BlockTags.LOGS);
        private static final Tagged WOODEN_TRAPDOORS = new Tagged(BlockTags.WOODEN_TRAPDOORS);
        private static final Tagged WOODEN_SLABS = new Tagged(BlockTags.WOODEN_SLABS);
        private static final Tagged WOODEN_FENCES = new Tagged(BlockTags.WOODEN_FENCES);
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
                        array[idx] = block == Blocks.AIR ? NONE_MATCH : new Single(block);
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
    }
    static Direction[] DIR_CONSIDER =new Direction[] {Direction.NORTH, Direction.WEST,Direction.SOUTH,Direction.EAST};
    static Direction[] DIR_SYMM = new Direction[] {Direction.NORTH, Direction.WEST};

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
                if(blockTypes[i] != BlockMatcher.ANY_MATCH && blockTypes[i].match(Blocks.DISPENSER)){
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
                if(matcher != BlockMatcher.ANY_MATCH){
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
        public Collection<MultiBlockHelper.MultiBlockLocation> lookup(World world, BlockPos dispensorPos){
            Collection<MultiBlockHelper.MultiBlockLocation> block = new HashSet<>();
            if(dispensorPos != null){
                for (var pd: predicate2LeftRightAxis.entrySet()){
                    if(pd.getKey().test(world, dispensorPos)){
                        BlockPos pos = dispensorPos.add(-this.dispenserPos.x * pd.getValue().getX(), -this.dispenserPos.y, -this.dispenserPos.x * pd.getValue().getZ() );
                        block.add(new MultiBlockHelper.MultiBlockLocation(pos, pd.getValue()));
                    }
                }
            }
            return block;
        }


    }








}
