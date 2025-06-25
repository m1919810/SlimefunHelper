package me.matl114.bukkitUtiils;

import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;


import javax.annotation.Nonnull;

import static me.matl114.utils.ItemStackUtils.*;

import java.util.*;
import java.util.regex.Pattern;

public class ItemStackHelper {
    public static ConfigurationSerializableDataType<BukkitItemStack> DATATYPE_MOCKITEMSTACK=new ConfigurationSerializableDataType(BukkitItemStack.class);
   // public static ItemStack STACK_FORBIDDEN=new ItemStack(Items.BARRIER,1);
    public static void init(){
        Debug.info("ItemStackHelper enabled");
    }


    public static ItemStack getAsDisplayItem(BukkitItemStack itemStack){
        try{
            ItemStack stack=new ItemStack(itemStack.getType());
            stack.setCount(itemStack.getAmount());
            if(itemStack.hasItemMeta()){
                BukkitMetaItem meta=itemStack.getItemMeta();
                if(meta.hasDisplayName()){
                    ItemStackUtils.setCustomName(stack, ItemStackUtils.jsonRawToText(meta.getDisplayName()));
                }
                if(meta.hasLore()){
                    ItemStackUtils.setLore(stack, meta.getLore().stream().map(ItemStackUtils::jsonRawToText).toList());
                }
                if(meta.hasEnchants()){

                    ItemStackUtils.setEnchantmentGlow(stack);
                }
                if(BukkitMetaType.ENCHANT_BOOK.isType(meta)){
                    ItemStackUtils.setEnchantmentGlow(stack);
                }
                if(BukkitMetaType.SKULL.isType(meta)){
                    if(BukkitMetaType.SKULL.getAttr(meta,"skull-owner") instanceof BukkitPlayerProfile bp){
                        bp.addGameProfile(stack);
                    }
                }
                BukkitPersistentDataContainer container=meta.getPersistentDataContainer();
                if(container!=null){
                    updateCustomData(stack, nbtCompound ->nbtCompound.put("PublicBukkitValues",container.toCompound()));
                }
                if(meta.hasCustomModelData()){
                    setCustomModelData(stack, meta.getCustomModelData());
                }

            }
//            Debug.info("get stack as display");
//            Debug.info(stack);
//            Debug.info(stack.hasNbt()?stack.getNbt():"null");
            return stack;

        }catch (Throwable e) {
            Debug.info("error in ItemConvertion");
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

    public static final Pattern HEXADECIMAL = Pattern.compile("[A-Fa-f\\d]+");
    public static NbtCompound buildPlayerHead(String hash){
        BukkitPlayerProfile.PlayerSkin skin = BukkitPlayerProfile.fromHashCode(hash);
        BukkitPlayerProfile profile = skin.getProfile();
        profile.name = "CS-CoreLib";
        //GameProfile profile = new GameProfile(uid, "CS-CoreLib");
        return profile.writeGameProfile(new NbtCompound());

    }

    public static String getHashFromProfile(ProfileComponent profileComponent){
        var pps= profileComponent.properties().get("textures");
        if(pps == null || pps.isEmpty())return null;
        Property ppt = Iterables.getFirst(pps,null);
        if(ppt == null)return null;
        JsonObject jsonObject = BukkitPlayerProfile.PlayerSkin.decodePropertyValue(ppt.value());
        if(jsonObject != null){
            if(jsonObject.get( "textures") instanceof JsonObject object1){
                if(object1.get("SKIN") instanceof JsonObject object2){
                    if(object2.get("url") instanceof JsonPrimitive primitive){
                        String url = primitive.getAsString();
                        String[] parts=url.split("/");
                        return parts[parts.length-1];
                    }
                }
            }
        }
        return null;
    }
    @Nonnull
    public static PropertyMap buildPropertyMap(PropertyMap oldMap, String hash){
        try{
            Property property = BukkitPlayerProfile.encodeUrlToProperty(BukkitPlayerProfile.fromHashToUrl(hash), BukkitPlayerProfile.PlayerTextures.SkinModel.CLASSIC, null);
            PropertyMap map1 = new PropertyMap();
            map1.putAll(oldMap);
            map1.removeAll("textures");
            map1.put("textures", property);
            return map1;
        }catch (Throwable e){
            return oldMap;
        }

    }

    public static ProfileComponent buildPlayerHeadProfileCSCoreLib(String hash){
        try{
            BukkitPlayerProfile.PlayerSkin skin = BukkitPlayerProfile.fromHashCode(hash);
            BukkitPlayerProfile profile = skin.getProfile();
            profile.name = "CS-CoreLib";
            //GameProfile profile = new GameProfile(uid, "CS-CoreLib");
            return profile.createGameProfile();
        }catch (Throwable e){
            return null;
        }

    }

}
