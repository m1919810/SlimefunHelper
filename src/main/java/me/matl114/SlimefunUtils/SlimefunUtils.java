package me.matl114.SlimefunUtils;

import me.matl114.BukkitUtiils.BukkitItemStack;
import me.matl114.BukkitUtiils.ItemStackHelper;
import me.matl114.SlimefunHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Set;

public class SlimefunUtils {
    protected static String BUKKIT_NAMESPACE="PublicBukkitValues";
    protected static String SLIMEFUN_ID_PATH="slimefun:slimefun_item";
    protected static String NETWORK_STORAGE_PATH="networks:quantum_storage";
    protected static String NETWORK_STORAGE_ITEM_PATH="networks:item";
    protected static String NETWORK_BLUEPRINT_PATH="networks:ntw_blueprint";
    protected static String NETWORK_BLUEPRINT_ITEM_PATH="networks:output";
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
                    return ItemStackHelper.serializeFromString(config);
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
                    return ItemStackHelper.serializeFromString(config);
                }else if(tag.contains(FINALTECH_STORAGE_ITEM_NEW)){
                    String config=tag.getString(FINALTECH_STORAGE_ITEM_NEW);
                    return ItemStackHelper.serializeFromString(config);
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
            return ItemStackHelper.getAsDisplay(stored);
        }
        else {
            return null;
        }
    }
}
