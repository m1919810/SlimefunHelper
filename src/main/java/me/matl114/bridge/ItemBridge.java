package me.matl114.bridge;

import me.matl114.bukkit.BukkitItemStackUtils;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ItemBridge {
    public static void init() {}

    public static Item TESTITEM;

    static {
        TESTITEM = Items.register("myitem", (settings -> {
            Item item = new Item(settings.component(DataComponentTypes.MAX_STACK_SIZE, 11)
                    .component(
                            DataComponentTypes.PROFILE,
                            BukkitItemStackUtils.buildPlayerHeadProfileCSCoreLib(
                                    "669c95e7451629add7845233131da5543157b8df52afadec1a586a0e549ee777"))
                    .food(new FoodComponent(0, 0, true)));
            if (item.getComponents() instanceof ComponentMap.Builder.SimpleComponentMap map0) {
                map0.map().put(DataComponentTypes.ITEM_MODEL, Identifier.tryParse("minecraft:player_head"));
                map0.map().put(DataComponentTypes.ITEM_NAME, Text.literal("zc_zyro的龟头"));
            }
            ;
            return item;
        }));
    }
}
