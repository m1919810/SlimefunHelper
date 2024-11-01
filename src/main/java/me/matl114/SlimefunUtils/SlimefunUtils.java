package me.matl114.SlimefunUtils;

import me.matl114.BukkitUtiils.BukkitConfigDeserializor;
import me.matl114.BukkitUtiils.BukkitItemStack;
import me.matl114.BukkitUtiils.ItemStackHelper;
import me.matl114.SlimefunHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashMap;
import java.util.List;
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



    public static ItemStack getStorageContent(ItemStack stack){

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
                return null;
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
        ItemStack heldItem=player.getStackInHand(Hand.MAIN_HAND);
        if(heldItem!=null){
            String sfid=getSfId(heldItem);
            if(sfid!=null){
                client.keyboard.setClipboard(sfid);
                player.sendMessage(Text.literal("成功将Slimefun ID拷贝至你的剪切板! 值: ").formatted(Formatting.GREEN).append(Text.literal(sfid).formatted(Formatting.WHITE)));
                return true;
            }else{
                player.sendMessage(Text.literal("该物品不是Slimefun物品,不能获取对应粘液ID!").formatted(Formatting.RED));
                return false;
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
                    if((tag=getBukkitValues(tag))!=null){
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
