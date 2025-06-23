package me.matl114.bukkitUtiils;

import net.minecraft.item.Item;


public class BukkitItemFactory {
    public BukkitItemFactory() {

    }
    public BukkitMetaItem getItemMeta(Item material) {
        return new BukkitMetaItem(material);
    }
    public boolean equals(BukkitMetaItem meta1, BukkitMetaItem meta2){
        return meta1!=null?meta1.equals(meta2):meta2==null;
    }
    public BukkitMetaItem asMetaFor(BukkitMetaItem meta,Item material) {
        if(meta != null){
            meta.item=material;
        }
        return meta;
    }

}
