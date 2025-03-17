package me.matl114.utils;

import me.matl114.bukkitUtiils.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Locale;

public class ItemStackUtils {
    public static NbtCompound getDisplay(ItemStack stack){
        if(stack.hasNbt()){
            if(stack.getNbt().contains("display")){
                return stack.getNbt().getCompound("display");
            }
        }
        return null;
    }
    public static void setDisplay(ItemStack stack,NbtCompound display){
        stack.getOrCreateNbt().put("display", display);
    }

    public static NbtCompound getEnchantment(ItemStack stack){
        if(stack.hasNbt()){
            if(stack.getNbt().contains("Enchantments")){
                return stack.getNbt().getCompound("Enchantments");
            }
        }
        return null;
    }
    public static void setEnchantment(ItemStack stack, NbtList enchantments){
        stack.getOrCreateNbt().put("Enchantments", enchantments);
    }
    public static void setStoredEnchantment(ItemStack stack, NbtList enchantments){
        stack.getOrCreateNbt().put("StoredEnchantments", enchantments);
    }
    public static ItemStack getCleanedItem(ItemStack stack){
        return getCleanedItem(stack, true);
    }
    public static ItemStack getCleanedItem(ItemStack stack,boolean keepDur){
        return getCleanedItem(stack,keepDur,true);
    }
    public static ItemStack getCleanedItem(ItemStack stack,boolean keepDur,boolean keepEnchant){
        return getCleanedItem(stack,true,keepDur,keepEnchant);
    }
    public static ItemStack getCleanedItem(ItemStack stack,boolean keepNBT,boolean keepDur,boolean keepEnchant){
        return getCleanedItem(stack, -999, keepNBT, keepDur, keepEnchant);
    }
    public static ItemStack getCleanedItem(ItemStack stack,int setAmount,boolean keepNBT,boolean keepDur,boolean keepEnchant){
        ItemStack cleaned=stack.getItem().getDefaultStack();

        if(!keepNBT){
            if(setAmount!=-999){
                cleaned.setCount(setAmount);
            }
            return cleaned;
        }
        ItemStack stackCopy=stack.copy();
        if(setAmount!=-999){
            stack.setCount(setAmount);
        }
        if(!keepDur){
            stackCopy.setDamage(cleaned.getDamage());
        }
        if(!keepEnchant){
            stackCopy.removeSubNbt("Enchantments");
            stackCopy.removeSubNbt("StoredEnchantments");
        }

        return stackCopy;
    }
    public static boolean isSimilarItemStack(ItemStack stack1,ItemStack stack2){
        return ItemStack.canCombine(stack1,stack2);
    }
    public static NbtCompound getStoredBlockState(ItemStack stack){
        if(stack.hasNbt()){
            var nbt = stack.getNbt();
            if(nbt != null && nbt.contains("BlockStateTag")){
                return nbt.getCompound("BlockStateTag");
            }
        }
        return null;
    }
    protected static String BUKKIT_NAMESPACE="PublicBukkitValues";
    protected static String SLIMEFUN_ID_PATH="slimefun:slimefun_item";
    protected static boolean isGrassOrShortGrass = Registries.ITEM.get(new Identifier("minecraft","grass")) != Items.AIR;
    public static ItemStack newItem(String type,String id){
        String[] typedString = type.split("[$]");
        String typedStr = typedString[0].toLowerCase(Locale.ROOT);
        if( "grass".equals(typedStr) || "short_grass".equals(typedStr) ){
            typedStr = isGrassOrShortGrass?"grass":"short_grass";
        }
        Item typed = Registries.ITEM.get(new Identifier("minecraft", typedStr));

        ItemStack stacked = new ItemStack(typed);
        if(typedString.length == 2 ){
            if(typed == Items.PLAYER_HEAD){
                stacked.getOrCreateNbt().put("SkullOwner", ItemStackHelper.buildPlayerHead(typedString[1]));
            }
        }
        if(id!=null && !"null".equals(id)){
            getOrCreateBukkitValues(stacked).putString(SLIMEFUN_ID_PATH,id);
        }
        return stacked.isEmpty() ? null: stacked;
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

    public static void setCustomModelData(ItemStack stack,int customModelData){
        stack.getOrCreateNbt().putInt("CustomModelData",customModelData);
    }


}
