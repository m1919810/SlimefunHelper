package me.matl114.BukkitUtiils;

import me.matl114.SlimefunUtils.Debug;
import me.matl114.SlimefunUtils.SlimefunUtils;
import me.matl114.Utils.ItemStackUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ItemStackHelper {
    public static ConfigurationSerializableDataType<org.bukkit.inventory.ItemStack> DATATYPE_ITEMSTACK=new ConfigurationSerializableDataType(org.bukkit.inventory.ItemStack.class);
    public static ConfigurationSerializableDataType<BukkitItemStack> DATATYPE_MOCKITEMSTACK=new ConfigurationSerializableDataType(BukkitItemStack.class);
    public static HashMap<Material, Item> MATERIAL_ITEM=new HashMap<>();
    public static ItemStack STACK_FORBIDDEN=new ItemStack(Items.BARRIER,1);
    public static void init(){
        Debug.info("ItemStackHelper enabled");
    }
    static {
        for(Material mat : Material.values()) {
            NamespacedKey key;
            if(mat.isLegacy()){
                key=new NamespacedKey("minecraft", mat.name().replace("LEGACY_","").toLowerCase());
            }else{
                key=mat.getKey();
            }
            if(mat.isItem()){
                Item item= Registries.ITEM.get(fromNamespace(key));
                if(item!=null){
                    MATERIAL_ITEM.put(mat, item);
                }else {
                    Debug.info("Material " + mat.toString() + " not found");
                }
            }
        }
    }

    public static Identifier fromNamespace(NamespacedKey key) {
        return new Identifier(key.getNamespace(), key.getKey());
    }
    public static ItemStack getAsNMItem(BukkitItemStack itemStack){
        try{
            ItemStack stack=new ItemStack(MATERIAL_ITEM.get(itemStack.getType()));
            stack.setCount(itemStack.getAmount());
            if(itemStack.hasItemMeta()){
                ItemMeta meta=itemStack.getItemMeta();

                if(meta.hasDisplayName()||meta.hasLore()){
                    ItemStackUtils.setDisplay(stack,ItemStackHelper.fromJsonToDisplay(meta.getDisplayName(),meta.getLore()));
                }
                if(meta.hasEnchants()){
                    ItemStackUtils.setEnchantment(stack, ItemStackHelper.fromEnchantsToList(getEnchantmentKeys(meta.getEnchants())));
                }
                if(BukkitMetaType.ENCHANT_BOOK.isType(meta)){
                    Map<?,?> e=(Map<?,?>)BukkitMetaType.ENCHANT_BOOK.getAttr(meta,"stored-enchants");
                   ItemStackUtils.setStoredEnchantment(stack, ItemStackHelper.fromEnchantsToList(getEnchantmentKeys(e)));
                }
                if(BukkitMetaType.SKULL.isType(meta)){
                    if(BukkitMetaType.SKULL.getAttr(meta,"skull-owner") instanceof BukkitPlayerProfile bp){
                        bp.addGameProfile(stack);
                    }
                }
                PersistentDataContainer container=meta.getPersistentDataContainer();
                if(container!=null&& container instanceof BukkitPersistentDataContainer bpdc){
                    stack.getOrCreateNbt().put("PublicBukkitValues",bpdc.toCompound());
                }
                if(meta.hasCustomModelData()){
                    SlimefunUtils.setCustomModelData(stack, meta.getCustomModelData());
                }

            }
//            Debug.info("get stack as display");
//            Debug.info(stack);
//            Debug.info(stack.hasNbt()?stack.getNbt():"null");
            return stack;

        }catch (Throwable e) {
            Debug.info("error in ItemConvertion");
            Debug.info(e);
            return null;
        }
    }

    public static NbtCompound fromJsonToDisplay(Object rawJsonName,Object rawJsonLore){
        if(rawJsonName==null&&rawJsonLore==null){
            return null;
        }
//        Debug.info("rawJsonName="+rawJsonName);
//        Debug.info("rawJsonNameClass="+rawJsonName.getClass());
//        Debug.info("rawJsonLore="+rawJsonLore);
//        Debug.info("rawJsonLoreClass="+rawJsonLore.getClass());

        NbtCompound compound=new NbtCompound();
        if(rawJsonName!=null){
            compound.putString("Name",rawJsonName.toString());

        }
        if(rawJsonLore!=null){
            NbtList lores=new NbtList();
            for (Object lore:(List)rawJsonLore){
                lores.add(NbtString.of((String)lore) );
            }
            compound.put("Lore",lores);
        }
        return compound;

    }
    public static Map<Enchantment, Integer> buildEnchantments(Map<?, ?> ench) {
        if (ench == null) {
            return null;
        }

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<Enchantment, Integer>(ench.size());
        for (Map.Entry<?, ?> entry : ench.entrySet()) {
            // Doctor older enchants
            String enchantKey = entry.getKey().toString();
            if (enchantKey.equals("SWEEPING")) {
                enchantKey = "SWEEPING_EDGE";
            }

            Enchantment enchantment = Enchantment.getByName(enchantKey);
            if(enchantment==null){
                try{
                    Field field = Enchantment.class.getField(enchantKey);
                    field.setAccessible(true);
                    enchantment=(Enchantment) field.get(null);
                }catch (Throwable e){

                }
            }
            if ((enchantment != null) && (entry.getValue() instanceof Integer)) {
                enchantments.put(enchantment, (Integer) entry.getValue());
            }
        }

        return enchantments;
    }
    public static Map<String, Integer> getEnchantmentKeys(Map<?, ?> ench) {
        if (ench == null) {
            return null;
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>(ench.size());
        for (Map.Entry<?, ?> entry : ench.entrySet()) {
            // Doctor older enchants
            String enchantKey = entry.getKey().toString();
            if (enchantKey.equals("SWEEPING")) {
                enchantKey = "SWEEPING_EDGE";
            }

            Enchantment enchantment = Enchantment.getByName(enchantKey);
            if(enchantment==null){
                try{
                    Field field = Enchantment.class.getField(enchantKey);
                    field.setAccessible(true);
                    enchantment=(Enchantment) field.get(null);
                }catch (Throwable e){

                }
            }
            if(entry.getValue() instanceof Integer it){
                if ((enchantment != null) ) {
                    enchantments.put(enchantment.getKey().toString(),it);
                }else{
                    enchantments.put("minecraft:"+enchantKey.toLowerCase(),it);
                }
            }
        }

        return enchantments;
    }
    public static NbtList fromEnchantsToList(Map<String, Integer> enchants){
        if(enchants==null||enchants.isEmpty()){
            return null;
        }
        ;
        NbtList list=new NbtList();
        for(Map.Entry<String, Integer> entry:enchants.entrySet()){
            String name = entry.getKey();
            Integer level = entry.getValue();
            NbtCompound enchantment= new NbtCompound();
            enchantment.putString("id",name);
            enchantment.putInt("lvl",level);
            list.add(enchantment);
        }

        return list;
    }
    public static ItemStack getAsNMItem(org.bukkit.inventory.ItemStack itemStack){
        try{
            ItemStack stack=new ItemStack(MATERIAL_ITEM.get(itemStack.getType()));
            stack.setCount(itemStack.getAmount());
            if(itemStack.hasItemMeta()){
                ItemMeta meta=itemStack.getItemMeta();
                if(meta.hasCustomModelData()){
                    SlimefunUtils.setCustomModelData(stack, meta.getCustomModelData());
                }
//                if(meta.hasDisplayName()){
//
//                }

            }
        }catch (Throwable e) {
            return null;
        }
        return null;
    }

}
