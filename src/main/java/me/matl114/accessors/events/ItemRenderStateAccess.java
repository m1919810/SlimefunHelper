package me.matl114.accessors.events;

import java.util.List;
import me.matl114.events.model.GuiModel;
import net.minecraft.client.render.item.ItemRenderState;

public interface ItemRenderStateAccess {
    public List<GuiModel.Entry> getAttachedRenderState();

    public void clearAttachedRenderState();

    public static ItemRenderStateAccess of(ItemRenderState state) {
        return (ItemRenderStateAccess) state;
    }
}
