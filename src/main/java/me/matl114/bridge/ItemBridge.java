package me.matl114.bridge;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;

public class ItemBridge {
    public static void init(){

    }
    public static Item TESTITEM ;
    static {
        TESTITEM = Items.register("myitem", Item::new);

    }
}
