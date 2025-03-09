package me.matl114.SlimefunUtils;

import me.matl114.Access.HandledScreenAccess;
import me.matl114.BukkitUtiils.BukkitConfigDeserializor;
import me.matl114.BukkitUtiils.BukkitItemStack;
import me.matl114.BukkitUtiils.ItemStackHelper;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunHelper;
import me.matl114.Utils.RenderUtils;
import me.matl114.Utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SlimefunUtils {
    protected static String BUKKIT_NAMESPACE="PublicBukkitValues";
    protected static String SLIMEFUN_ID_PATH="slimefun:slimefun_item";
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
    public static ItemStack newItem(String type,String id){
        Item typed = Registries.ITEM.get(new Identifier("minecraft",type));
        ItemStack stacked = new ItemStack(typed);
        if(id!=null && !"null".equals(id)){
            getOrCreateBukkitValues(stacked).putString(SLIMEFUN_ID_PATH,id);
        }
        return stacked;
    }
    public static String getSfId(NbtCompound nbt){
        NbtCompound bukkitValues=getBukkitValues(nbt);
        if(bukkitValues==null)return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }
    public static String getSfId(ItemStack stack) {
        NbtCompound bukkitValues=getBukkitValues(stack);
        if(bukkitValues==null)return null;
        return getSfIdFromBukkitValues(bukkitValues);
    }
    public static NbtCompound getBukkitValues(ItemStack stack) {
        if(stack.hasNbt()){
            return getBukkitValues(stack.getNbt());
        }else return null;
    }
    public static NbtCompound getOrCreateBukkitValues(ItemStack stack) {
        return stack.getOrCreateSubNbt(BUKKIT_NAMESPACE);
    }
    public static NbtCompound getBukkitValues(NbtCompound tag) {
        if(tag!=null&&!tag.isEmpty()&&tag.contains(BUKKIT_NAMESPACE)){
            return tag.getCompound(BUKKIT_NAMESPACE);
        }else return null;
    }
    public static String getSfIdFromBukkitValues(NbtCompound bukkitValues) {
        if(bukkitValues!=null&&!bukkitValues.isEmpty()&&bukkitValues.contains(SLIMEFUN_ID_PATH)){
            return bukkitValues.getString(SLIMEFUN_ID_PATH);
        }else return null;
    }
    public static Identifier getNamespaceKey(String id) {
        return new Identifier(SlimefunHelper.MOD_ID, id);
    }
    public static void setCustomModelData(ItemStack stack,int customModelData){
        stack.getOrCreateNbt().putInt("CustomModelData",customModelData);
    }
    public static void setPersistentDataContainer(ItemStack stack,PersistentDataContainer persistentDataContainer){
        if (!persistentDataContainer.isEmpty()) {
            NbtCompound bukkitCustomCompound = getOrCreateBukkitValues(stack);
            Set<NamespacedKey> rawPublicMap = persistentDataContainer.getKeys();

//            for (NamespacedKey namespacedKey : rawPublicMap) {
//                bukkitCustomCompound.put(namespacedKey.toString(), persistentDataContainer.get(namespacedKey));
//            }
            //itemTag.put(BUKKIT_CUSTOM_TAG.NBT, bukkitCustomCompound);
        }
    }
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



    public static ItemStack getContainedItemInfo(ItemStack stack){

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
                
            }
            else{
                ItemStack item ;
                if((item = getOptionalChickenOutput(stack))!=null){

                }else {
                    return null;
                }
                return item;
            }
            return ItemStackHelper.getAsNMItem(stored);
        }
        else {
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
        RenderUtils.registerModelOverridePredicate((stack)->{
            String optionalChicken = handlePureChickenDNAInfo(stack);
            if(optionalChicken!=null ){
                String val = dnaInfo.get(optionalChicken);
                if(val != null){
                    return Optional.of( new ModelIdentifier("slimefunhelper","gce/"+val,"inventory"));
                }
            }
            return Optional.empty();
        });
    }
    protected static final String CLT_SEED_PATH="cultivation:seed_instance";
    protected static final String CLT_SEED_DROP_PATH="cultivation:drop_rate";
    protected static final String CLT_SEED_GROWTH_PATH="cultivation:growth_speed";
    protected static final String CLT_SEED_STRENGTH_PATH="cultivation:strength";
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
}
