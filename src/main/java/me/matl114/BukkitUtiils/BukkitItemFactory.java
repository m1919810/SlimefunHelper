package me.matl114.BukkitUtiils;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

public class BukkitItemFactory {
    public BukkitItemFactory() {

    }
    public ItemMeta getItemMeta(Material material) {
        return new BukkitMetaItem(material);
    }
    public boolean equals(ItemMeta meta1, ItemMeta meta2){
        return meta1!=null?meta1.equals(meta2):meta2==null;
    }
    public ItemMeta asMetaFor(ItemMeta meta,Material material) {
        if(meta instanceof BukkitMetaItem bmi){
            bmi.item=material;
        }
        return meta;
    }

}
