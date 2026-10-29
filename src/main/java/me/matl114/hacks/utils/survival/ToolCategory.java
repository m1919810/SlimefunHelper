package me.matl114.hacks.utils.survival;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.tag.ItemTags;

public enum ToolCategory {
    SWORD,
    AXE,
    MACE,
    HELMET,
    CHEST_PLATE,
    LEGGINGS,
    BOOTS,
    TOOL;

    public static ToolCategory of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        var entry = item.getRegistryEntry();
        if (item instanceof MaceItem) {
            return MACE;
        }
        if (entry.isIn(ItemTags.SWORDS)) {
            return SWORD;
        }
        if (entry.isIn(ItemTags.AXES)) {
            return AXE;
        }
        if (entry.isIn(ItemTags.HEAD_ARMOR)) {
            return HELMET;
        }
        if (entry.isIn(ItemTags.CHEST_ARMOR)) {
            return CHEST_PLATE;
        }
        if (entry.isIn(ItemTags.LEG_ARMOR)) {
            return LEGGINGS;
        }
        if (entry.isIn(ItemTags.FOOT_ARMOR)) {
            return BOOTS;
        }
        if (entry.isIn(ItemTags.PICKAXES) || entry.isIn(ItemTags.SHOVELS) || entry.isIn(ItemTags.HOES)) {
            return TOOL;
        }
        return null;
    }
}
