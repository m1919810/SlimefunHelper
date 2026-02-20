package com.jsmacrosce.jsmacros.client.api.helper.inventory;

import com.jsmacrosce.jsmacros.core.helpers.BaseHelper;
import net.minecraft.item.ItemStack;

public class ItemStackHelper extends BaseHelper<ItemStack> {
    public ItemStackHelper(ItemStack base) {
        super(base);
    }

    public boolean isEmpty() {
        return base.isEmpty();
    }
}
