package xyz.wagyourtail.jsmacros.client.api.helper.inventory;

import net.minecraft.item.ItemStack;
import xyz.wagyourtail.jsmacros.core.helpers.BaseHelper;

public class ItemStackHelper extends BaseHelper<ItemStack> {
    public ItemStackHelper(ItemStack base) {
        super(base);
    }

    public boolean isEmpty() {
        return base.isEmpty();
    }
}
