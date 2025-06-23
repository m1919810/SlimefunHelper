package me.matl114.bukkitUtiils;


import java.util.Arrays;
import java.util.HashSet;

public enum BukkitMetaType {
    ENCHANT_BOOK("stored-enchants"),
    SKULL("skull-owner");
    HashSet<String> moreAttributes=new HashSet<>();
    public boolean isType(BukkitMetaItem meta0){
        if(meta0 != null){
            for (String attr:moreAttributes){
                if(meta0.attributes.containsKey(attr)){
                    return true;
                }
            }return false;
        }else return false;
    }
    public Object getAttr(BukkitMetaItem meta, String key){
        if(moreAttributes.contains(key)){
            return ((BukkitMetaItem)meta).attributes.get(key);
        }else{
            throw new AssertionError("key not in type Attribute record");
        }

    }
     BukkitMetaType(String... attrs){
        moreAttributes.addAll(Arrays.stream(attrs).toList());

    }
}
