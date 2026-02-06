package me.matl114.versioned.impl;

import me.matl114.versioned.api.VItem;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.*;

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
    public CustomModelDataComponent createModelData(int cmd) {
        return new CustomModelDataComponent(cmd);
    }

}
