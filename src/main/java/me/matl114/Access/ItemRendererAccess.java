package me.matl114.Access;

import net.minecraft.client.render.item.ItemRenderer;

public interface ItemRendererAccess {
    static ItemRendererAccess of(ItemRenderer itemRenderer) {
        return (ItemRendererAccess) itemRenderer;
    }
    public void printInfo();

}
