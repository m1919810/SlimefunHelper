package me.matl114.accessors.events;

import net.minecraft.client.render.item.ItemRenderState;

public interface ItemRenderStateAccess {
    public ItemRenderState getAttachedRenderState();

    public void setAttachedRenderState(ItemRenderState state);

    public static ItemRenderStateAccess of(ItemRenderState state) {
        return (ItemRenderStateAccess) state;
    }
}
