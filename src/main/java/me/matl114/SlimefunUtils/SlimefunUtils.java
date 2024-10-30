package me.matl114.SlimefunUtils;

import me.matl114.SlimefunHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public class SlimefunUtils {
    protected static String BUKKIT_NAMESPACE="PublicBukkitValues";
    protected static String SLIMEFUN_ID_PATH="slimefun:slimefun_item";
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
}
