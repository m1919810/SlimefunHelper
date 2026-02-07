package me.matl114.versioned.impl;

import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VItem;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;

public class ItemUtils_v1_21_1 implements VItem {
    @Override
    public boolean canGlide(ItemStack stack) {
        return stack.getItem() instanceof ElytraItem;
    }

    @Override
    public boolean isSpear(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isWeapon(ItemStack stack) {
        if(stack.getItem() instanceof MaceItem) {
            return true;
        }else if(stack.getItem() instanceof ToolItem tool){
            if(tool instanceof AxeItem){
                return true;
            }else if(tool instanceof MiningToolItem){
                return false;
            }else{
                return true;
            }
        }else{
            return false;
        }
    }

    @Override
    public boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof ToolItem;
    }

    @Override
    public boolean isShield(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem;
    }

    @Override
    public ItemStack fromNbt(NbtCompound tag) {
        return ItemStack.fromNbtOrEmpty(ItemStackUtils.registry(), tag);
    }

    @Override
    public NbtCompound toNbt(ItemStack tag) {
        return (NbtCompound) tag.encodeAllowEmpty(ItemStackUtils.registry());
    }

    @Override
    public CustomModelDataComponent createModelData(int cmd) {
        return new CustomModelDataComponent(cmd);
    }

}
