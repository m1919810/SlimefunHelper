package me.matl114.renders.implement;

import me.matl114.access.HandledScreenAccess;
import me.matl114.bukkitUtiils.BukkitConfigDeserializor;
import me.matl114.bukkitUtiils.BukkitItemStack;
import me.matl114.bukkitUtiils.ItemStackHelper;
import me.matl114.hackUtils.Tasks;
import me.matl114.managers.HotKeys;
import me.matl114.renders.RenderMain;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import static me.matl114.utils.ItemStackUtils.*;

import java.util.*;

public class SlimefunRender {
    public static void init(){

    }

    protected static String NETWORK_STORAGE_PATH="networks:quantum_storage";
    protected static String OLD_NETWORK_STORAGE_PATH="networks-changed:quantum_storage";
    protected static String NETWORK_STORAGE_ITEM_PATH="networks:item";
    protected static String OLD_NETWORK_STORAGE_ITEM_PATH="networks-changed:item";
    protected static String NETWORK_BLUEPRINT_PATH="networks:ntw_blueprint";
    protected static String OLD_NETWORK_BLUEPRINT_PATH="networks-changed:blueprint";
    protected static String NETWORK_BLUEPRINT_ITEM_PATH="networks:output";
    protected static String OLD_NETWORK_BLUEPRINT_ITEM_PATH="networks-changed:output";
    protected static String NETWORK_MOVER_ITEM_PATH="networks:item_mover_item";
    protected static String NETWORK_STORAGE_AMOUNT_PATH="networks:amount";
    protected static String LOGITECH_SINGULARITY_ITEM_PATH="logitech:data";
    protected static String LOGITECH_SINGULARITY_PATH="logitech:sin_item";
    protected static String INFINTY_STORAGE_ITEM_PATH="infinityexpansion:item";
    protected static String FINALTECH_STORAGE_ITEM_NEW="finaltech-changed:item";
    protected static String FINALTECH_STORAGE_ITEM_OLD="finaltech:item";


    public static BukkitItemStack getNetworkStoraged(NbtCompound tag){
        try{
            if(tag!=null){
                if(tag.contains(NETWORK_STORAGE_PATH)){
                    NbtCompound storageNbt=tag.getCompound(NETWORK_STORAGE_PATH);
                    if(storageNbt!=null&&storageNbt.contains(NETWORK_STORAGE_ITEM_PATH)){
                        byte[] byteStream=storageNbt.getByteArray(NETWORK_STORAGE_ITEM_PATH);
                        BukkitItemStack stored= ItemStackHelper.DATATYPE_MOCKITEMSTACK.fromPrimitive(byteStream);
                        return stored;
                    }
                }else if(tag.contains(NETWORK_MOVER_ITEM_PATH)) {
                    byte[] byteStream=tag.getByteArray(NETWORK_MOVER_ITEM_PATH);
                    BukkitItemStack stored= ItemStackHelper.DATATYPE_MOCKITEMSTACK.fromPrimitive(byteStream);
                    return stored;
                } else if (tag.contains(OLD_NETWORK_STORAGE_PATH)) {
                    NbtCompound storageNbt=tag.getCompound(OLD_NETWORK_STORAGE_PATH);
                    if(storageNbt!=null&&storageNbt.contains(OLD_NETWORK_STORAGE_ITEM_PATH)){
                        byte[] byteStream=storageNbt.getByteArray(OLD_NETWORK_STORAGE_ITEM_PATH);
                        BukkitItemStack stored= ItemStackHelper.DATATYPE_MOCKITEMSTACK.fromPrimitive(byteStream);
                        return stored;
                    }
                }
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }
    public static BukkitItemStack getNetworkBlueprint(NbtCompound tag){
        try{
            if(tag!=null){
                if(tag.contains(NETWORK_BLUEPRINT_PATH)){
                    NbtCompound storageNbt=tag.getCompound(NETWORK_BLUEPRINT_PATH);
                    if(storageNbt!=null&&storageNbt.contains(NETWORK_BLUEPRINT_ITEM_PATH)){
                        byte[] byteStream=storageNbt.getByteArray(NETWORK_BLUEPRINT_ITEM_PATH);
                        BukkitItemStack stored= ItemStackHelper.DATATYPE_MOCKITEMSTACK.fromPrimitive(byteStream);
                        return stored;
                    }
                }else if(tag.contains(OLD_NETWORK_BLUEPRINT_ITEM_PATH)) {
                    NbtCompound storageNbt=tag.getCompound(OLD_NETWORK_BLUEPRINT_PATH);
                    if(storageNbt!=null&&storageNbt.contains(OLD_NETWORK_BLUEPRINT_ITEM_PATH)){
                        byte[] byteStream=storageNbt.getByteArray(OLD_NETWORK_BLUEPRINT_ITEM_PATH);
                        BukkitItemStack stored= ItemStackHelper.DATATYPE_MOCKITEMSTACK.fromPrimitive(byteStream);
                        return stored;
                    }
                }
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }
    public static BukkitItemStack getLogitechSingularity(NbtCompound tag){
        try{
            if(tag!=null){
                if(tag.contains(LOGITECH_SINGULARITY_PATH)){
                    NbtCompound storageNbt=tag.getCompound(LOGITECH_SINGULARITY_PATH);
                    if(storageNbt!=null&&storageNbt.contains(LOGITECH_SINGULARITY_ITEM_PATH)){
                        byte[] byteStream=storageNbt.getByteArray(LOGITECH_SINGULARITY_ITEM_PATH);
                        BukkitItemStack stored= ItemStackHelper.DATATYPE_MOCKITEMSTACK.fromPrimitive(byteStream);
                        return stored;
                    }
                }
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }
    public static BukkitItemStack getInfinityStorage(NbtCompound tag){
        try{
            if(tag!=null){
                if(tag.contains(INFINTY_STORAGE_ITEM_PATH)){
                    String config=tag.getString(INFINTY_STORAGE_ITEM_PATH);
                    return BukkitConfigDeserializor.deserializeItemFromString(config);
                }
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }
    public static BukkitItemStack getFinalTechStorage(NbtCompound tag){
        try{
            if(tag!=null){
                if(tag.contains(FINALTECH_STORAGE_ITEM_OLD)){
                    String config=tag.getString(FINALTECH_STORAGE_ITEM_OLD);
                    return BukkitConfigDeserializor.deserializeItemFromString(config);
                }else if(tag.contains(FINALTECH_STORAGE_ITEM_NEW)){
                    String config=tag.getString(FINALTECH_STORAGE_ITEM_NEW);
                    return BukkitConfigDeserializor.deserializeItemFromString(config);
                }
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }




    protected static final Text SLIMEFUN_MODID=Text.literal("Slimefun").formatted(Formatting.BLUE);
    public static Text modShow(){
        return SLIMEFUN_MODID.copy();
    }
    public static boolean copySfIdInHand(ClientPlayerEntity player,MinecraftClient client){
        if(player==null||client==null)return false;
        ItemStack heldItem=null;
        if(client.currentScreen instanceof HandledScreen<?> s){
            Pair<Integer,Integer> mouseCoord= ScreenUtils.getMouseCoord(client);
            Slot slot=HandledScreenAccess.of(s).reallyGetSlotAt(mouseCoord.getLeft(),mouseCoord.getRight());
            if(slot!=null){
                heldItem=slot.getStack();
            }
        }else{
            heldItem=player.getStackInHand(Hand.MAIN_HAND);
        }
        if(heldItem!=null){
            String sfid=getSfId(heldItem);

            if(sfid!=null){
                client.keyboard.setClipboard(sfid);
                HotKeys.SHARED_ARGUMENT.set(sfid);
                player.sendMessage(Text.literal("成功将Slimefun ID拷贝至你的剪切板和公共参数! 值: ").formatted(Formatting.GREEN).append(Text.literal(sfid).formatted(Formatting.WHITE)));

                return true;
            }else{
                player.sendMessage(Text.literal("该物品不是Slimefun物品,不能获取对应粘液ID!").formatted(Formatting.RED));
                return true;
            }
        }
        return false;
    }
    protected static String GCE_CHICKEN_PATH="geneticchickengineering:gce_pocket_chicken_dna";
    protected static char[] GCE_GENE_DISPLAY_L=new char[]{'b','c','d','f','s','w'};
    protected static char[] GCE_GENE_DISPLAY_U=new char[]{'B','C','D','F','S','W'};
    public static void handleGCEInfo(String sfid,ItemStack stack, List<Text> lores){
        if(sfid.startsWith("GCE_")){
            if(stack!=null&&stack.hasNbt()){
                try{
                    NbtCompound tag=stack.getNbt();
                    if((tag=getBukkitValues(tag))!=null  && tag.contains(GCE_CHICKEN_PATH)){
                        int[] dna=tag.getIntArray(GCE_CHICKEN_PATH);
                        int len=dna.length;
                        StringBuilder sb=new StringBuilder();
                        for(int i=0;i<6;i++){
                            if(len>i&& dna[i]==0||dna[i]==1||dna[i]==3){
                                sb.append(dna[i]%2==0?GCE_GENE_DISPLAY_L[i]:GCE_GENE_DISPLAY_U[i]).append(dna[i]/2==0?GCE_GENE_DISPLAY_L[i]:GCE_GENE_DISPLAY_U[i]);
                            }else{
                                sb.append("??");
                            }
                        }
                        lores.add(Text.literal("基因工程: ").formatted(Formatting.GRAY).append(Text.literal(sb.toString()).formatted(Formatting.DARK_PURPLE)));
                    }
                }catch (Throwable e){
                }
            }
        }
    }
    public static HashMap<String,String> dnaInfo = new HashMap<>(){{
        put("bbccddffSSWW","blackstone_chicken");
        put("bbccddffSSww","end_stone_chicken");
        put("BBCCddffssWW","redstone_chicken");
        put("BBCCddffssww","glowstone_dust_chicken");
        put("bbCCDDFFssWW","sugar_chicken");
        put("bbCCDDFFssww","cake_chicken");
        put("BBCCDDffSSWW","flint_chicken");
        put("BBCCDDffSSww","kelp_chicken");
        put("bbccddffssWW","diamond_chicken");
        put("bbccddffssww","netherite_chicken");
        put("bbccDDffSSWW","netherrack_chicken");
        put("bbccDDffSSww","quartz_chicken");
        put("BBCCDDffssWW","gunpowder_chicken");
        put("BBCCDDffssww","lead_dust_chicken");
        put("BBCCddFFSSWW","dirt_chicken");
        put("BBCCddFFSSww","oak_log_chicken");
        put("BBccddFFssWW","copper_dust_chicken");
        put("BBccddFFssww","nether_wart_chicken");
        put("bbCCDDffssWW","silver_dust_chicken");
        put("bbCCDDffssww","phantom_membrane_chicken");
        put("bbCCddFFSSWW","string_chicken");
        put("bbCCddFFSSww","gold_dust_chicken");
        put("BBccDDFFSSWW","cobblestone_chicken");
        put("BBccDDFFSSww","ice_chicken");
        put("bbCCddFFssWW","iron_dust_chicken");
        put("bbCCddFFssww","ender_pearl_chicken");
        put("BBCCddffSSWW","granite_chicken");
        put("BBCCddffSSww","cactus_chicken");
        put("BBccDDFFssWW","gravel_chicken");
        put("BBccDDFFssww","snowball_chicken");
        put("bbCCDDFFSSWW","bone_chicken");
        put("bbCCDDFFSSww","sponge_chicken");
        put("BBccddffssWW","sulfate_chicken");
        put("BBccddffssww","emerald_chicken");
        put("bbCCddffSSWW","iron_chicken");
        put("bbCCddffSSww","basalt_chicken");
        put("bbccDDFFssWW","glass_chicken");
        put("bbccDDFFssww","soul_sand_chicken");
        put("BBccDDffSSWW","andesite_chicken");
        put("BBccDDffSSww","tin_dust_chicken");
        put("bbCCddffssWW","ghast_tear_chicken");
        put("bbCCddffssww","experience_chicken");
        put("BBccDDffssWW","lava_chicken");
        put("BBccDDffssww","magma_cream_chicken");
        put("BBccddFFSSWW","diorite_chicken");
        put("BBccddFFSSww","magnesium_dust_chicken");
        put("bbCCDDffSSWW","leather_chicken");
        put("bbCCDDffSSww","zinc_dust_chicken");
        put("bbccDDffssWW","blaze_rod_chicken");
        put("bbccDDffssww","prismarine_shard_chicken");
        put("bbccddFFSSWW","gold_chicken");
        put("bbccddFFSSww","shroomlight_chicken");
        put("BBCCddFFssWW","clay_chicken");
        put("BBCCddFFssww","aluminum_dust_chicken");
        put("BBCCDDFFSSWW","feather_chicken");
        put("BBCCDDFFSSww","water_chicken");
        put("bbccddFFssWW","soul_soil_chicken");
        put("bbccddFFssww","prismarine_crystals_chicken");
        put("BBccddffSSWW","obsidian_chicken");
        put("BBccddffSSww","crying_obsidian_chicken");
        put("bbccDDFFSSWW","coal_chicken");
        put("bbccDDFFSSww","lapis_chicken");
        put("BBCCDDFFssWW","sand_chicken");
        put("BBCCDDFFssww","slime_ball_chicken");
    }};
    public static HashMap<String,ItemStack> dnaOutput = new HashMap<>(){{
        put("bbccddffSSWW",newItem("blackstone",null));
        put("bbccddffSSww",newItem("end_stone",null));
        put("BBCCddffssWW",newItem("redstone",null));
        put("BBCCddffssww",newItem("glowstone_dust",null));
        put("bbCCDDFFssWW",newItem("sugar",null));
        put("bbCCDDFFssww",newItem("cake",null));
        put("BBCCDDffSSWW",newItem("flint",null));
        put("BBCCDDffSSww",newItem("kelp",null));
        put("bbccddffssWW",newItem("diamond",null));
        put("bbccddffssww",newItem("netherite_ingot",null));
        put("bbccDDffSSWW",newItem("netherrack",null));
        put("bbccDDffSSww",newItem("quartz",null));
        put("BBCCDDffssWW",newItem("gunpowder",null));
        put("BBCCDDffssww",newItem("gunpowder","LEAD_DUST"));
        put("BBCCddFFSSWW",newItem("dirt",null));
        put("BBCCddFFSSww",newItem("oak_log",null));
        put("BBccddFFssWW",newItem("glowstone_dust","COPPER_DUST"));
        put("BBccddFFssww",newItem("nether_wart",null));
        put("bbCCDDffssWW",newItem("sugar","SILVER_DUST"));
        put("bbCCDDffssww",newItem("phantom_membrane",null));
        put("bbCCddFFSSWW",newItem("string",null));
        put("bbCCddFFSSww",newItem("glowstone_dust","GOLD_DUST"));
        put("BBccDDFFSSWW",newItem("cobblestone",null));
        put("BBccDDFFSSww",newItem("ice",null));
        put("bbCCddFFssWW",newItem("gunpowder","IRON_DUST"));
        put("bbCCddFFssww",newItem("ender_pearl",null));
        put("BBCCddffSSWW",newItem("granite",null));
        put("BBCCddffSSww",newItem("cactus",null));
        put("BBccDDFFssWW",newItem("gravel",null));
        put("BBccDDFFssww",newItem("snowball",null));
        put("bbCCDDFFSSWW",newItem("bone",null));
        put("bbCCDDFFSSww",newItem("sponge",null));
        put("BBccddffssWW",newItem("glowstone_dust","SULFATE"));
        put("BBccddffssww",newItem("emerald",null));
        put("bbCCddffSSWW",newItem("iron_ingot",null));
        put("bbCCddffSSww",newItem("basalt",null));
        put("bbccDDFFssWW",newItem("glass",null));
        put("bbccDDFFssww",newItem("soul_sand",null));
        put("BBccDDffSSWW",newItem("andesite",null));
        put("BBccDDffSSww",newItem("sugar","TIN_DUST"));
        put("bbCCddffssWW",newItem("ghast_tear",null));
        put("bbCCddffssww",newItem("experience_bottle",null));
        put("BBccDDffssWW",newItem("strider_spawn_egg","GCE_LAVA_EGG"));
        put("BBccDDffssww",newItem("magma_cream",null));
        put("BBccddFFSSWW",newItem("diorite",null));
        put("BBccddFFSSww",newItem("sugar","MAGNESIUM_DUST"));
        put("bbCCDDffSSWW",newItem("leather",null));
        put("bbCCDDffSSww",newItem("sugar","ZINC_DUST"));
        put("bbccDDffssWW",newItem("blaze_rod",null));
        put("bbccDDffssww",newItem("prismarine_shard",null));
        put("bbccddFFSSWW",newItem("gold_ingot",null));
        put("bbccddFFSSww",newItem("shroomlight",null));
        put("BBCCddFFssWW",newItem("clay",null));
        put("BBCCddFFssww",newItem("sugar","ALUMINUM_DUST"));
        put("BBCCDDFFSSWW",newItem("feather",null));
        put("BBCCDDFFSSww",newItem("turtle_spawn_egg","GCE_WATER_EGG"));
        put("bbccddFFssWW",newItem("soul_soil",null));
        put("bbccddFFssww",newItem("prismarine_crystals",null));
        put("BBccddffSSWW",newItem("obsidian",null));
        put("BBccddffSSww",newItem("crying_obsidian",null));
        put("bbccDDFFSSWW",newItem("coal",null));
        put("bbccDDFFSSww",newItem("lapis_lazuli",null));
        put("BBCCDDFFssWW",newItem("sand",null));
        put("BBCCDDFFssww",newItem("slime_ball",null));
    }};
    public static <T extends Object> List<T> ofNullableList(T... values){
        var re = Arrays.stream(values).filter(Objects::nonNull).toList();
        return re.isEmpty()?null:re;
    }
    public static String handlePureChickenDNAInfo(ItemStack item){
        NbtCompound tag=item.getNbt();

        String val = getSfId(tag);
        if(val!=null && val.startsWith("GCE_") &&( tag=getBukkitValues(tag))!=null && tag.contains(GCE_CHICKEN_PATH)){
            try{
                int[] dna=tag.getIntArray(GCE_CHICKEN_PATH);
                int len=dna.length;
                StringBuilder sb=new StringBuilder();
                for(int i=0;i<6;i++){
                    if(len>i&& dna[i]==0||dna[i]==1||dna[i]==3){
                        if(dna[i] == 0){
                            sb.append(GCE_GENE_DISPLAY_L[i]).append(GCE_GENE_DISPLAY_L[i]);
                        }else{
                            sb.append(GCE_GENE_DISPLAY_U[i]).append(GCE_GENE_DISPLAY_U[i]);
                        }
                    }else{
                        sb.append("??");
                    }
                }
                return sb.toString();
            }catch (Throwable e){
            }
        }
        return null;
    }
    public static ItemStack getOptionalChickenOutput(ItemStack item){
        String val = handlePureChickenDNAInfo(item);
        if(val != null){
            return dnaOutput.get(val).copy();
        }
        return null;
    }
    static{
        RenderMain.registerModelOverridePredicate((stack)->{
            String optionalChicken = handlePureChickenDNAInfo(stack);
            if(optionalChicken!=null ){
                String val = dnaInfo.get(optionalChicken);
                if(val != null){
                    return Optional.of( new Identifier("slimefunhelper","gce/"+val));
                }
            }
            return Optional.empty();
        });
    }
    protected static final String CLT_SEED_PATH="cultivation:seed_instance";
    protected static final String CLT_SEED_DROP_PATH="cultivation:drop_rate";
    protected static final String CLT_SEED_GROWTH_PATH="cultivation:growth_speed";
    protected static final String CLT_SEED_STRENGTH_PATH="cultivation:strength";
    protected static final HashMap<String,List<Item>> CLT_OUTPUT = new HashMap<>(){{
        put("CLT_PLANT_SCRAPPY",ofNullableList(Items.NETHERITE_SCRAP));
        put("CLT_PLANT_NETHERRACK",ofNullableList(Items.NETHERRACK));
        put("CLT_PLANT_WITHER",ofNullableList(Items.NETHER_STAR));
        put("CLT_PLANT_RAW_IRON",ofNullableList(Items.RAW_IRON));
        put("CLT_PLANT_ELDER_GUARDIAN",ofNullableList(Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS, Items.SPONGE));
        put("CLT_PLANT_ENDERMAN",ofNullableList(Items.ENDER_PEARL, Items.ENDER_EYE));
        put("CLT_PLANT_TERRA",ofNullableList(Items.BLACK_TERRACOTTA, Items.BLUE_TERRACOTTA, Items.BROWN_TERRACOTTA, Items.CYAN_TERRACOTTA, Items.GRAY_TERRACOTTA, Items.GREEN_TERRACOTTA, Items.LIGHT_BLUE_TERRACOTTA, Items.LIGHT_GRAY_TERRACOTTA, Items.LIME_TERRACOTTA, Items.MAGENTA_TERRACOTTA, Items.ORANGE_TERRACOTTA, Items.PINK_TERRACOTTA, Items.PURPLE_TERRACOTTA, Items.RED_TERRACOTTA, Items.WHITE_TERRACOTTA, Items.YELLOW_TERRACOTTA));
        put("CLT_PLANT_GLASS",ofNullableList(Items.GLASS));
        put("CLT_PLANT_SKELETON",ofNullableList(Items.BONE, Items.ARROW, Items.SKELETON_SKULL));
        put("CLT_PLANT_SPIDER",ofNullableList(Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE, Items.STRING));
        put("CLT_PLANT_GRAVEL",ofNullableList(Items.GRAVEL));
        put("CLT_PLANT_RAW_GOLD",ofNullableList(Items.RAW_GOLD));
        put("CLT_PLANT_WAXY",ofNullableList(Items.BLACK_CANDLE, Items.BLUE_CANDLE, Items.BROWN_CANDLE, Items.CYAN_CANDLE, Items.GRAY_CANDLE, Items.GREEN_CANDLE, Items.LIGHT_BLUE_CANDLE, Items.LIGHT_GRAY_CANDLE, Items.LIME_CANDLE, Items.MAGENTA_CANDLE, Items.ORANGE_CANDLE, Items.PINK_CANDLE, Items.PURPLE_CANDLE, Items.RED_CANDLE, Items.WHITE_CANDLE, Items.YELLOW_CANDLE));
        put("CLT_PLANT_CHICKEN",ofNullableList(Items.CHICKEN, Items.FEATHER, Items.EGG));
        put("CLT_PLANT_GHAST",ofNullableList(Items.GHAST_TEAR));
        put("CLT_PLANT_MUD",ofNullableList(Items.MUD));
        put("CLT_PLANT_DARK_GRASS",ofNullableList(Items.WARPED_NYLIUM, Items.CRIMSON_NYLIUM));
        put("CLT_PLANT_COBBLESTONE",ofNullableList(Items.COBBLESTONE));
        put("CLT_PLANT_REINFORCED",ofNullableList(Items.REINFORCED_DEEPSLATE));
        put("CLT_PLANT_GOAT",ofNullableList(Items.GOAT_HORN));
        put("CLT_PLANT_BLAZE",ofNullableList(Items.BLAZE_POWDER, Items.BLAZE_ROD));
        put("CLT_PLANT_COW",ofNullableList(Items.BEEF, Items.LEATHER));
        put("CLT_PLANT_DIAMOND",ofNullableList(Items.DIAMOND));
        put("CLT_PLANT_PIG",ofNullableList(Items.PORKCHOP));
        put("CLT_PLANT_MAGMA",ofNullableList(Items.MAGMA_BLOCK));
        put("CLT_PLANT_ECHO",ofNullableList(Items.ECHO_SHARD));
        put("CLT_PLANT_DIM_LIT",ofNullableList(Items.OCHRE_FROGLIGHT, Items.PEARLESCENT_FROGLIGHT, Items.VERDANT_FROGLIGHT));
        put("CLT_PLANT_FROG",ofNullableList(Items.FROGSPAWN));
        put("CLT_PLANT_WITHER_SKELETON",ofNullableList(Items.BONE, Items.WITHER_SKELETON_SKULL));
        put("CLT_PLANT_VINE",ofNullableList(Items.VINE));
        put("CLT_PLANT_BLACKSTONE",ofNullableList(Items.BLACKSTONE));
        put("CLT_PLANT_SQUID",ofNullableList(Items.INK_SAC));
        put("CLT_PLANT_FLOWER",ofNullableList(Items.CORNFLOWER, Items.LILAC, Items.LILY_OF_THE_VALLEY, Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET, Items.ORANGE_TULIP, Items.PINK_TULIP, Items.RED_TULIP, Items.WHITE_TULIP, Items.OXEYE_DAISY));
        put("CLT_PLANT_GLOWING_VINE",ofNullableList(Items.GLOW_LICHEN));
        put("CLT_PLANT_GLOW_SQUID",ofNullableList(Items.GLOW_INK_SAC));
        put("CLT_PLANT_STAINED",ofNullableList(Items.BLACK_STAINED_GLASS, Items.BLUE_STAINED_GLASS, Items.BROWN_STAINED_GLASS, Items.CYAN_STAINED_GLASS, Items.GRAY_STAINED_GLASS, Items.GREEN_STAINED_GLASS, Items.LIGHT_BLUE_STAINED_GLASS, Items.LIGHT_GRAY_STAINED_GLASS, Items.LIME_STAINED_GLASS, Items.MAGENTA_STAINED_GLASS, Items.ORANGE_STAINED_GLASS, Items.PINK_STAINED_GLASS, Items.PURPLE_STAINED_GLASS, Items.RED_STAINED_GLASS, Items.WHITE_STAINED_GLASS, Items.YELLOW_STAINED_GLASS));
        put("CLT_PLANT_RED_SAND",ofNullableList(Items.RED_SAND));
        put("CLT_PLANT_DUSTY",ofNullableList(Items.BLACK_CONCRETE_POWDER, Items.BLUE_CONCRETE_POWDER, Items.BROWN_CONCRETE_POWDER, Items.CYAN_CONCRETE_POWDER, Items.GRAY_CONCRETE_POWDER, Items.GREEN_CONCRETE_POWDER, Items.LIGHT_BLUE_CONCRETE_POWDER, Items.LIGHT_GRAY_CONCRETE_POWDER, Items.LIME_CONCRETE_POWDER, Items.MAGENTA_CONCRETE_POWDER, Items.ORANGE_CONCRETE_POWDER, Items.PINK_CONCRETE_POWDER, Items.PURPLE_CONCRETE_POWDER, Items.RED_CONCRETE_POWDER, Items.WHITE_CONCRETE_POWDER, Items.YELLOW_CONCRETE_POWDER));
        put("CLT_PLANT_PURPUR",ofNullableList(Items.PURPUR_BLOCK));
        put("CLT_PLANT_GLAZED",ofNullableList(Items.BLACK_GLAZED_TERRACOTTA, Items.BLUE_GLAZED_TERRACOTTA, Items.BROWN_GLAZED_TERRACOTTA, Items.CYAN_GLAZED_TERRACOTTA, Items.GRAY_GLAZED_TERRACOTTA, Items.GREEN_GLAZED_TERRACOTTA, Items.LIGHT_BLUE_GLAZED_TERRACOTTA, Items.LIGHT_GRAY_GLAZED_TERRACOTTA, Items.LIME_GLAZED_TERRACOTTA, Items.MAGENTA_GLAZED_TERRACOTTA, Items.ORANGE_GLAZED_TERRACOTTA, Items.PINK_GLAZED_TERRACOTTA, Items.PURPLE_GLAZED_TERRACOTTA, Items.RED_GLAZED_TERRACOTTA, Items.WHITE_GLAZED_TERRACOTTA, Items.YELLOW_GLAZED_TERRACOTTA));
        put("CLT_PLANT_DROWNED",ofNullableList(Items.ROTTEN_FLESH, Items.NAUTILUS_SHELL, Items.TRIDENT));
        put("CLT_PLANT_SAND",ofNullableList(Items.SAND));
        put("CLT_PLANT_VILLAGER",ofNullableList(Items.PAPER));
        put("CLT_PLANT_EMERALD",ofNullableList(Items.EMERALD));
        put("CLT_PLANT_GRASS",ofNullableList(Items.GRASS_BLOCK));
        put("CLT_PLANT_ZOMBIE",ofNullableList(Items.ROTTEN_FLESH, Items.ZOMBIE_HEAD));
        put("CLT_PLANT_DIRT",ofNullableList(Items.DIRT));
        put("CLT_PLANT_MOSS",ofNullableList(Items.MOSS_BLOCK));
        put("CLT_PLANT_MUSHROOM",ofNullableList(Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, Items.CRIMSON_FUNGUS, Items.WARPED_FUNGUS, Items.MYCELIUM));
        put("CLT_PLANT_SLIME",ofNullableList(Items.SLIME_BALL));
        put("CLT_PLANT_REDSTONE",ofNullableList(Items.REDSTONE));
        put("CLT_PLANT_WOOLLY",ofNullableList(Items.BLACK_WOOL, Items.BLUE_WOOL, Items.BROWN_WOOL, Items.CYAN_WOOL, Items.GRAY_WOOL, Items.GREEN_WOOL, Items.LIGHT_BLUE_WOOL, Items.LIGHT_GRAY_WOOL, Items.LIME_WOOL, Items.MAGENTA_WOOL, Items.ORANGE_WOOL, Items.PINK_WOOL, Items.PURPLE_WOOL, Items.RED_WOOL, Items.WHITE_WOOL, Items.YELLOW_WOOL));
        put("CLT_PLANT_BEE",ofNullableList(Items.HONEYCOMB, Items.HONEY_BOTTLE));
        put("CLT_PLANT_DARK_FLORA",ofNullableList(Items.WEEPING_VINES, Items.TWISTING_VINES));
        put("CLT_PLANT_RABBIT",ofNullableList(Items.RABBIT, Items.RABBIT_HIDE, Items.RABBIT_FOOT));
        put("CLT_PLANT_SHEEP",ofNullableList(Items.MUTTON, Items.WHITE_WOOL));
        put("CLT_PLANT_NETHER_QUARTZ",ofNullableList(Items.QUARTZ));
        put("CLT_PLANT_LAPIS",ofNullableList(Items.LAPIS_LAZULI));
        put("CLT_PLANT_COAL",ofNullableList(Items.COAL));
        put("CLT_PLANT_SAPLING",ofNullableList(Items.ACACIA_SAPLING, Items.BIRCH_SAPLING, Items.DARK_OAK_SAPLING, Items.JUNGLE_SAPLING, Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.MANGROVE_PROPAGULE));
        put("CLT_PLANT_CONCRETE",ofNullableList(Items.BLACK_CONCRETE, Items.BLUE_CONCRETE, Items.BROWN_CONCRETE, Items.CYAN_CONCRETE, Items.GRAY_CONCRETE, Items.GREEN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.LIME_CONCRETE, Items.MAGENTA_CONCRETE, Items.ORANGE_CONCRETE, Items.PINK_CONCRETE, Items.PURPLE_CONCRETE, Items.RED_CONCRETE, Items.WHITE_CONCRETE, Items.YELLOW_CONCRETE));
        put("CLT_PLANT_DEEPSLATE",ofNullableList(Items.DEEPSLATE));
        put("CLT_PLANT_FISH",ofNullableList(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH));
        put("CLT_PLANT_RAINBOW",ofNullableList(Items.BLACK_DYE, Items.BLUE_DYE, Items.BROWN_DYE, Items.CYAN_DYE, Items.GRAY_DYE, Items.GREEN_DYE, Items.LIGHT_BLUE_DYE, Items.LIGHT_GRAY_DYE, Items.LIME_DYE, Items.MAGENTA_DYE, Items.ORANGE_DYE, Items.PINK_DYE, Items.PURPLE_DYE, Items.RED_DYE, Items.WHITE_DYE, Items.YELLOW_DYE));
        put("CLT_PLANT_CLAY",ofNullableList(Items.CLAY));
        put("CLT_PLANT_GUARDIAN",ofNullableList(Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS));
        put("CLT_PLANT_SHULKER",ofNullableList(Items.SHULKER_SHELL));
        put("CLT_PLANT_END_STONE",ofNullableList(Items.END_STONE));
        put("CLT_PLANT_MAGMA_CUBE",ofNullableList(Items.MAGMA_CREAM));
        put("CLT_PLANT_SOUL",ofNullableList(Items.SOUL_SAND, Items.SOUL_SOIL, Items.GHAST_TEAR));
        put("CLT_PLANT_WITHER_ROSE",ofNullableList(Items.WITHER_ROSE));
        put("CLT_PLANT_BASALT",ofNullableList(Items.BASALT));
        put("CLT_PLANT_CREEPER",ofNullableList(Items.GUNPOWDER, Items.CREEPER_HEAD));
        put("CLT_PLANT_TURTLE",ofNullableList(Items.SCUTE, Items.SEAGRASS, Items.TURTLE_EGG));
        put("CLT_PLANT_PHANTOM",ofNullableList(Items.PHANTOM_MEMBRANE));
        put("CLT_PLANT_ENDER_DRAGON",ofNullableList(Items.DRAGON_BREATH, Items.DRAGON_HEAD, Items.DRAGON_EGG));
        put("CLT_PLANT_AMETHYST",ofNullableList(Items.AMETHYST_SHARD));
        put("CLT_PLANT_WITCH",ofNullableList(Items.REDSTONE, Items.GLOWSTONE));
        put("CLT_PLANT_RAW_COPPER",ofNullableList(Items.RAW_COPPER));
        put("CLT_PLANT_IGNEOUS",ofNullableList(Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.CALCITE, Items.TUFF, Items.DRIPSTONE_BLOCK));
    }};
    protected static final HashMap<String,List<ItemStack>> CLT_BUSH_OUT = new HashMap<>(){{
        put("CLT_BUSH_BAY_LEAF",ofNullableList(newItem("LILY_PAD","CLT_BAY_LEAF")));
        put("CLT_BUSH_RICE",ofNullableList(newItem("PLAYER_HEAD$cb70f2fb5ebf49f79ff3e873616863ae5d362fbbfc31aef2dfb93d6e17dbf2","CLT_RICE")));
        put("CLT_BUSH_SHALLOT",ofNullableList(newItem("PLAYER_HEAD$a6ecc46dc3dc85fcd57198176ee841f1a041b15f73ecb19fde62ee4315c4a6","CLT_SHALLOT")));
        put("CLT_BUSH_MARJORAM",ofNullableList(newItem("DARK_OAK_LEAVES","CLT_MARJORAM")));
        put("CLT_BUSH_STRAWBERRY",ofNullableList(newItem("PLAYER_HEAD$b9708d818be97dc7e2c3bb5c35663eb36269236e9bc98286f429dfdf375aa9","CLT_STRAWBERRY")));
        put("CLT_BUSH_KALE",ofNullableList(newItem("PLAYER_HEAD$1b913d9d6e306d953461d1f7468321868a9bad4df53d1a7dc64b0797628f0","CLT_KALE")));
        put("CLT_BUSH_RAPESEED",ofNullableList(newItem("MELON_SEEDS","CLT_RAPESEED")));
        put("CLT_BUSH_LAVENDER",ofNullableList(newItem("PURPLE_DYE","CLT_LAVENDER")));
        put("CLT_BUSH_DILL",ofNullableList(newItem("GRASS","CLT_DILL")));
        put("CLT_BUSH_JASMINE",ofNullableList(newItem("WHITE_TULIP","CLT_JASMINE")));
        put("CLT_BUSH_RHUBARB",ofNullableList(newItem("PLAYER_HEAD$c144a523b05063c81f006fdbd8d9b75249aa009b4c1f46db27dbbeafc4a8578","CLT_RHUBARB")));
        put("CLT_BUSH_CELERIAC",ofNullableList(newItem("PLAYER_HEAD$d0f61fe6d3c0e7f01434d38367b1a50db04f8cccbdbbda1806bf1198318dc7eb","CLT_CELERIAC")));
        put("CLT_BUSH_LETTUCE",ofNullableList(newItem("PLAYER_HEAD$2d52e883c6436ca7e764bda4b58afa53da6a649030f663953dce9fd6315f9fea","CLT_LETTUCE")));
        put("CLT_BUSH_SWEETCORN",ofNullableList(newItem("PLAYER_HEAD$d391dffbea2fc3f2ad78a623f49bf7e1121694112c3759feed4156fc2ba46c0","CLT_SWEETCORN")));
        put("CLT_BUSH_LICORICE",ofNullableList(newItem("HANGING_ROOTS","CLT_LICORICE")));
        put("CLT_BUSH_CABBAGE",ofNullableList(newItem("PLAYER_HEAD$58e59dc722e419fe064f0c7992a7897b73cc7c5205e19d067a174bcb018e9429","CLT_CABBAGE")));
        put("CLT_BUSH_BROCCOLI",ofNullableList(newItem("PLAYER_HEAD$65106f0cc4c12dcfe7736a1ffc92772f41b66e1b7682b1f47537787720567262","CLT_BROCCOLI")));
        put("CLT_BUSH_SPINACH",ofNullableList(newItem("KELP","CLT_SPINACH")));
        put("CLT_BUSH_MARROW",ofNullableList(newItem("PLAYER_HEAD$36ae076649ef22f60e8511831c68fd2b6ea63c32164dab33a8aebc18ff2a54c8","CLT_MARROW")));
        put("CLT_BUSH_CELERY",ofNullableList(newItem("BAMBOO","CLT_CELERY")));
        put("CLT_BUSH_CUCUMBER",ofNullableList(newItem("BAMBOO","CLT_CUCUMBER")));
        put("CLT_BUSH_CILANTRO",ofNullableList(newItem("MANGROVE_LEAVES","CLT_CILANTRO")));
        put("CLT_BUSH_MACE",ofNullableList(newItem("RED_DYE","CLT_MACE")));
        put("CLT_BUSH_CLOVE",ofNullableList(newItem("POPPY","CLT_CLOVE")));
        put("CLT_BUSH_ROSEMARY",ofNullableList(newItem("BIRCH_LEAVES","CLT_ROSEMARY")));
        put("CLT_BUSH_SOY_BEANS",ofNullableList(newItem("MELON_SEEDS","CLT_SOY_BEANS")));
        put("CLT_BUSH_STAR_ANISE",ofNullableList(newItem("NETHER_STAR","CLT_STAR_ANISE")));
        put("CLT_BUSH_RUTABAGA",ofNullableList(newItem("PLAYER_HEAD$b3661e4bf4c3e730a3aaa1053a3fc524dc03df67bf7a20979efdb2ad1a9e4084","CLT_RUTABAGA")));
        put("CLT_BUSH_PARSLEY",ofNullableList(newItem("ACACIA_LEAVES","CLT_PARSLEY")));
        put("CLT_BUSH_GREEN_BEANS",ofNullableList(newItem("FROGSPAWN","CLT_GREEN_BEANS")));
        put("CLT_BUSH_PEANUTS",ofNullableList(newItem("PLAYER_HEAD$cf823e05353ae9122150bc67a5df7de628f4d4c30b36bdae3c1c18582bfca776","CLT_PEANUT")));
        put("CLT_BUSH_HORSERADISH",ofNullableList(newItem("HANGING_ROOTS","CLT_HORSERADISH")));
        put("CLT_BUSH_CHILLI_PEPPER",ofNullableList(newItem("PLAYER_HEAD$a3ef14c40251844c3ae39b6028db86a9098df325e50b7a475972cd1ac918e9d5","CLT_CHILLY_PEPPER")));
        put("CLT_BUSH_THYME",ofNullableList(newItem("BIRCH_LEAVES","CLT_THYME")));
        put("CLT_BUSH_PARSNIP",ofNullableList(newItem("PLAYER_HEAD$d0f61fe6d3c0e7f01434d38367b1a50db04f8cccbdbbda1806bf1198318dc7eb","CLT_PARSNIP")));
        put("CLT_BUSH_MINT",ofNullableList(newItem("MANGROVE_LEAVES","CLT_MINT")));
        put("CLT_BUSH_NETTLES",ofNullableList(newItem("AZALEA_LEAVES","CLT_NETTLES")));
        put("CLT_BUSH_PINTO_BEANS",ofNullableList(newItem("MELON_SEEDS","CLT_PINTO_BEANS")));
        put("CLT_BUSH_LEEK",ofNullableList(newItem("PLAYER_HEAD$c144a523b05063c81f006fdbd8d9b75249aa009b4c1f46db27dbbeafc4a8578","CLT_LEEK")));
        put("CLT_BUSH_AVOCADO",ofNullableList(newItem("PLAYER_HEAD$6af2bf32bb8937a5aadfbf6d8dc56a21ef65f6884db67206280fa1e149f8c4b","CLT_AVOCADO")));
        put("CLT_BUSH_BLACK_BEANS",ofNullableList(newItem("MELON_SEEDS","CLT_BLACK_BEANS")));
        put("CLT_BUSH_TURNIP",ofNullableList(newItem("PLAYER_HEAD$bfac2d1d21aeb8d7a0c289733511d903df57924ad6bd61d00567a55af649bc0d","CLT_TURNIP")));
        put("CLT_BUSH_GINGER",ofNullableList(newItem("HANGING_ROOTS","CLT_GINGER")));
        put("CLT_BUSH_CHICORY",ofNullableList(newItem("BLUE_ORCHID","CLT_CHICORY")));
        put("CLT_BUSH_FENNEL",ofNullableList(newItem("OXEYE_DAISY","CLT_FENNEL")));
        put("CLT_BUSH_RUNNER_BEANS",ofNullableList(newItem("KELP","CLT_RUNNER_BEANS")));
        put("CLT_BUSH_OREGANO",ofNullableList(newItem("SPRUCE_LEAVES","CLT_OREGANO")));
        put("CLT_BUSH_WASABI",ofNullableList(newItem("HANGING_ROOTS","CLT_WASABI")));
        put("CLT_BUSH_CHICKPEAS",ofNullableList(newItem("BEETROOT_SEEDS","CLT_CHICKPEAS")));
        put("CLT_BUSH_CAULIFLOWER",ofNullableList(newItem("PLAYER_HEAD$14a6dedd99bb9af3f1b2f338d509a926606cddfdc351e018aad1c07015ad566d","CLT_CAULIFLOWER")));
        put("CLT_BUSH_CAYENNE_PEPPER",ofNullableList(newItem("PLAYER_HEAD$a3ef14c40251844c3ae39b6028db86a9098df325e50b7a475972cd1ac918e9d5","CLT_CAYENNE_PEPPER")));
        put("CLT_BUSH_ASPARAGUS",ofNullableList(newItem("PLAYER_HEAD$2ba5599e26cf2252d431a950b2078a0af2e45c60edff9d4fadf62c323df5411f","CLT_ASPARAGUS")));
        put("CLT_BUSH_AUBERGINE",ofNullableList(newItem("PLAYER_HEAD$521358c5b2e2526ae6aab91a5fb09198461a7cc4d860e8647d5e10fb6c87be67","CLT_AUBERGINE")));
        put("CLT_BUSH_OKRA",ofNullableList(newItem("PLAYER_HEAD$2d52e883c6436ca7e764bda4b58afa53da6a649030f663953dce9fd6315f9fea","CLT_OKRA")));
        put("CLT_BUSH_BASIL",ofNullableList(newItem("SMALL_DRIPLEAF","CLT_BASIL")));
        put("CLT_BUSH_COURGETTE",ofNullableList(newItem("BAMBOO","CLT_COURGETTE")));
        put("CLT_BUSH_RADICCHIO",ofNullableList(newItem("PLAYER_HEAD$cd56f448876ebfdaa88ca7f0137a6837f4d2ba14dd3d6615d82d617c39b39daf","CLT_RADICCHIO")));
        put("CLT_BUSH_PEPPER",ofNullableList(newItem("PLAYER_HEAD$d209d3d0d8daa4628b9b3e10a235e22089d76bffe156ddc5852e5fdc12a3d12c","CLT_PEPPER")));
        put("CLT_BUSH_ONION",ofNullableList(newItem("PLAYER_HEAD$a6ecc46dc3dc85fcd57198176ee841f1a041b15f73ecb19fde62ee4315c4a6","CLT_ONION")));
        put("CLT_BUSH_TARRAGON",ofNullableList(newItem("JUNGLE_LEAVES","CLT_TARRAGON")));
        put("CLT_BUSH_SWEET_POTATO",ofNullableList(newItem("BEETROOT","CLT_SWEET_POTATO")));
        put("CLT_BUSH_SHISO",ofNullableList(newItem("MANGROVE_LEAVES","CLT_SHISO")));
        put("CLT_BUSH_BRUSSELS_SPROUTS",ofNullableList(newItem("PLAYER_HEAD$1b913d9d6e306d953461d1f7468321868a9bad4df53d1a7dc64b0797628f0","CLT_BRUSSELS_SPROUTS")));
        put("CLT_BUSH_TOMATO",ofNullableList(newItem("PLAYER_HEAD$72df4e674951c138e7311127561fdbd27e2150716b02bb568747f8545fb20145","CLT_TOMATO")));
        put("CLT_BUSH_JALAPENO",ofNullableList(newItem("PLAYER_HEAD$a3ef14c40251844c3ae39b6028db86a9098df325e50b7a475972cd1ac918e9d5","CLT_JALAPENO")));
        put("CLT_BUSH_CURRY_LEAF",ofNullableList(newItem("KELP","CLT_CURRY_LEAF")));
        put("CLT_BUSH_TURMERIC",ofNullableList(newItem("YELLOW_DYE","CLT_TURMERIC")));
        put("CLT_BUSH_CUMIN",ofNullableList(newItem("DANDELION","CLT_CUMIN")));
        put("CLT_BUSH_GRAPE",ofNullableList(newItem("PLAYER_HEAD$e44c359f6f28c5fa6c5a09d8c57fa4174da4e8ae110e5f2cd7e93f2e76176cd5","CLT_GRAPE")));
        put("CLT_BUSH_CHIVES",ofNullableList(newItem("SEAGRASS","CLT_CHIVES")));
        put("CLT_BUSH_CINNAMON",ofNullableList(newItem("STICK","CLT_CINNAMON")));
        put("CLT_BUSH_ARTICHOKE",ofNullableList(newItem("PLAYER_HEAD$58e59dc722e419fe064f0c7992a7897b73cc7c5205e19d067a174bcb018e9429","CLT_ARTICHOKE")));
        put("CLT_BUSH_GARLIC",ofNullableList(newItem("PLAYER_HEAD$ca5b1539b698c217cb3b4163a00e336131043311b83b08de02f1a66505be5b29","CLT_GARLIC")));
        put("CLT_BUSH_PEA",ofNullableList(newItem("PLAYER_HEAD$96c15fb4e9a31191f0cb4da56fe60334dd46eb3a582111b4f8f27eddb760e2c","CLT_PEA")));
        put("CLT_BUSH_KAFFIR_LIME",ofNullableList(newItem("KELP","CLT_KAFFIR_LIME")));
        put("CLT_BUSH_MUSTARD",ofNullableList(newItem("PUMPKIN_SEEDS","CLT_MUSTARD_SEEDS")));
        put("CLT_BUSH_RADDISH",ofNullableList(newItem("PLAYER_HEAD$cd56f448876ebfdaa88ca7f0137a6837f4d2ba14dd3d6615d82d617c39b39daf","CLT_RADDISH")));
        put("CLT_BUSH_BELL_PEPPER",ofNullableList(newItem("PLAYER_HEAD$65f7810414a2cee2bc1de12ecef7a4c89fc9b38e9d0414a90991241a5863705f","CLT_BELL_PEPPER")));
        put("CLT_BUSH_JUNIPER_BERRY",ofNullableList(newItem("GLOW_BERRIES","CLT_JUNIPER_BERRY")));
        put("CLT_BUSH_SASSAFRAS",ofNullableList(newItem("OAK_LEAVES","CLT_SASSAFRAS")));
        put("CLT_BUSH_GREEN_ONION",ofNullableList(newItem("PLAYER_HEAD$c2dd5433db4fddebc4a77166735699400cb18d43672ab31326a83f0b7c2586cc","CLT_GREEN_ONION")));
        put("CLT_BUSH_VANILLA",ofNullableList(newItem("BLACK_DYE","CLT_VANILLA")));
        put("CLT_TREE_GREEN_APPLE",ofNullableList(newItem("PLAYER_HEAD$96c15fb4e9a31191f0cb4da56fe60334dd46eb3a582111b4f8f27eddb760e2c","CLT_GREEN_APPLE")));
        put("CLT_TREE_MANGO",ofNullableList(newItem("PLAYER_HEAD$e4895aa67247c3eb406fb905d3f6d35acd660c6f14a85bcf7bfb898b82646e70","CLT_MANGO")));
        put("CLT_TREE_HAZELNUT",ofNullableList(newItem("PLAYER_HEAD$678784703fa59cd153fcabe3ccd9d44c469a8d63e6d438626ad9ebc70707fc3","CLT_HAZELNUT")));
        put("CLT_TREE_PEACH",ofNullableList(newItem("PLAYER_HEAD$fadbbab3881aacba577e27bbee1fbe4b9a50e19f5a87f8d49b636054fa1788fc","CLT_PEACH")));
        put("CLT_TREE_PEAR",ofNullableList(newItem("PLAYER_HEAD$c68dd595bdc68e1a8dc84d789f21791edd053ba3bbedbfec2e7daa7243aea217","CLT_PEAR")));
        put("CLT_TREE_CHESTNUT",ofNullableList(newItem("PLAYER_HEAD$7aea6b06c058546247e6567009440140946ea53623829721bd46b3e6a0e5cce8","CLT_CHESTNUT")));
        put("CLT_TREE_PECAN",ofNullableList(newItem("PLAYER_HEAD$9813444497a48b3b68946961e49e13dd6b19467aeed1eb9d0e876bc878e0a734","CLT_PECAN")));
        put("CLT_TREE_LEMON",ofNullableList(newItem("PLAYER_HEAD$4378b582d19ccc55b023eb82eda271bac4744fa2006cf5e190246e2b4d5d","CLT_LEMON")));
        put("CLT_TREE_KIWI",ofNullableList(newItem("PLAYER_HEAD$4cc18ec4649f07d5a38a583d9271fd83a6f37318758e46ea87fc2b2d1afc2d9","CLT_KIWI")));
        put("CLT_TREE_LIME",ofNullableList(newItem("PLAYER_HEAD$4cc18ec4649f07d5a38a583d9271fd83a6f37318758e46ea87fc2b2d1afc2d9","CLT_LIME")));
        put("CLT_TREE_APRICOT",ofNullableList(newItem("PLAYER_HEAD$534104bb1442b4034cf32595a087b7a51d96ce5915c182d833eefa68e1cec1ff","CLT_APRICOT")));
        put("CLT_TREE_CHERRY",ofNullableList(newItem("PLAYER_HEAD$8b9b2383bae7b84fdc31b54179afb713a1c187b83e7a0c5e38470ae2a3e2a30f","CLT_CHERRY")));
        put("CLT_TREE_BANANA",ofNullableList(newItem("PLAYER_HEAD$20aaa1425d2b99383697d57193f27d872442bcb995508f42d19de4af1f8612","CLT_BANANA")));
        put("CLT_TREE_PINEAPPLE",ofNullableList(newItem("PLAYER_HEAD$57c5e925a949e55db2c25efaad64512eb6dab74affb2e9f304c385b4f4b30ba5","CLT_PINEAPPLE")));
        put("CLT_TREE_ORANGE",ofNullableList(newItem("PLAYER_HEAD$91b0fb313d90ddafd4fd7c95ef0d51b2ff9cc13013f1d318b11d87aa002fbaa0","CLT_ORANGE")));
    }};
    public static void handleCLTInfo(String sfid,ItemStack stack,List<Text> lores){
        if(sfid.startsWith("CLT_PLANT")){
            if(stack!=null&&stack.hasNbt()){
                MutableText info=Text.literal("农耕工艺: [").formatted(Formatting.GRAY);
                NbtCompound tag=getBukkitValues(stack);
                if(tag!=null){
                    try{
                        if(tag.contains(CLT_SEED_PATH)){
                            tag=tag.getCompound(CLT_SEED_PATH);
                            int level=tag.getInt(CLT_SEED_DROP_PATH);
                            int speed=tag.getInt(CLT_SEED_GROWTH_PATH);
                            int strength=tag.getInt(CLT_SEED_STRENGTH_PATH);
                            info.append(Text.literal("等级: ").formatted(Formatting.YELLOW));
                            info.append(Text.literal(String.valueOf(level)).formatted(Formatting.GRAY));
                            info.append(Text.literal(" 速率: ").formatted(Formatting.YELLOW));
                            info.append(Text.literal(String.valueOf(speed)).formatted(Formatting.GRAY));
                            info.append(Text.literal(" 强度: ").formatted(Formatting.YELLOW));
                            info.append(Text.literal(String.valueOf(strength)).formatted(Formatting.GRAY));
                        }else {
                            info.append(Text.literal("未初始化属性").formatted(Formatting.RED));
                        }
                    }catch (Throwable e){
                        info.append(Text.literal("数据错误").formatted(Formatting.RED));
                    }
                }
                info.append(Text.literal("]").formatted(Formatting.GRAY));
                lores.add(info);
            }
        }
    }
    public static Item handleCLTOutput(String sfid){
        var re = CLT_OUTPUT.get(sfid);
        if(re == null){
            return null;
        }else{
            return re.get(Math.abs(Tasks.getSecond())%re.size());
        }
    }
    public static ItemStack handleCLTBushOut(String id){
        var re = CLT_BUSH_OUT.get(id);

        if(re == null){
            return null;
        }else {
            return re.get(Math.abs(Tasks.getSecond())%re.size()).copy();
        }
    }
    public static ItemStack handleCLTInfo(ItemStack item){
        String sfid = getSfId(item);
        if( sfid != null && sfid.startsWith("CLT_")){
            Item optionalOut = handleCLTOutput(sfid);
            if(optionalOut != null){
                return new ItemStack(optionalOut);
            }
            ItemStack optionalBushOut = handleCLTBushOut(sfid);
            if(optionalBushOut != null){
                return optionalBushOut;
            }
        }
        return null;
    }
    public static final String ES_PREFIX = "ELECTRIC_SPAWNER_";
    public static final int ES_PREFIX_LEN = ES_PREFIX.length();
    public static ItemStack handleElectricSpawnerInfo(ItemStack item){
        String sfid = getSfId(item);
        if( sfid != null && sfid.startsWith(ES_PREFIX)){
            String entity = sfid.substring(ES_PREFIX_LEN);
            EntityType<?> entity1 = Registries.ENTITY_TYPE.getOrEmpty(new Identifier("minecraft",entity.toLowerCase(Locale.ROOT))).orElse(null);
            if(entity1!=null){
                return SpawnerRender.getRenderingEntityContent(entity1);
            }
        }
        return null;
    }

    static{
        RenderMain.registerContainerInfoPredicate((stack)->{
            NbtCompound tag = getBukkitValues(stack);

            if(tag!=null){
                BukkitItemStack stored;
                if((stored=getNetworkStoraged(tag))!=null){

                }
                else if ((stored=getNetworkBlueprint(tag))!=null){

                }
                else if ((stored=getLogitechSingularity(tag))!=null){

                }
                else if((stored=getInfinityStorage(tag))!=null){

                }
                else if((stored=getFinalTechStorage(tag))!=null){

                }else{
                    return null;
                }
                return ItemStackHelper.getAsNMItem(stored);
            }
            return null;
        });
        RenderMain.registerContainerInfoPredicate((stack)->{
            ItemStack item ;
            if((item = getOptionalChickenOutput(stack))!=null){

            }else if((item = handleCLTInfo(stack))!=null){

            }else if((item = handleElectricSpawnerInfo(stack)) != null){

            }
            else {
                return null;
            }
            return item;
        });
    }
}
