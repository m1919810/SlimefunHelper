package me.matl114.registry;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class ItemRegistry {
    public static void init() {}

    public static Item TESTITEM;

    static {
        TESTITEM = Items.register("myitem", Item::new);
    }
}
