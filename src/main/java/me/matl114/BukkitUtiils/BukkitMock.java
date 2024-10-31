package me.matl114.BukkitUtiils;

import org.bukkit.configuration.serialization.ConfigurationSerialization;

public class BukkitMock {
    public static BukkitItemFactory ITEM_FACTORY_INSTANCE=new BukkitItemFactory();
    public static void init(){

    }
    static {
        ConfigurationSerialization.registerClass(BukkitMetaItem.class,"ItemMeta");
        ConfigurationSerialization.registerClass(BukkitMetaItem.class,"org.bukkit.craftbukkit.inventory.CraftMetaItem");
        ConfigurationSerialization.registerClass(BukkitItemStack.class,"ItemStack");
        ConfigurationSerialization.registerClass(BukkitItemStack.class,"org.bukkit.inventory.ItemStack");
        ConfigurationSerialization.registerClass(BukkitOfflineplayer.class,"OfflinePlayer");
        ConfigurationSerialization.registerClass(BukkitPlayerProfile.class,"PlayerProfile");
    }
    public static BukkitItemFactory getItemFactory() {
        return ITEM_FACTORY_INSTANCE;
    }
}
