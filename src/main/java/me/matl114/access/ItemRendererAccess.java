package me.matl114.access;

import net.minecraft.client.render.item.ItemRenderer;

public interface ItemRendererAccess {
    static ItemRendererAccess of(ItemRenderer itemRenderer) {
        return (ItemRendererAccess) itemRenderer;
    }
    public void printInfo();

}
