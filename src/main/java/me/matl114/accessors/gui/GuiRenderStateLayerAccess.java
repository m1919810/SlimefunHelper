package me.matl114.accessors.gui;

import net.minecraft.client.gui.render.state.GuiRenderState;

public interface GuiRenderStateLayerAccess {
    public int getDepth();

    public void setDepth(int depth);

    public static GuiRenderStateLayerAccess of(GuiRenderState.Layer layer) {
        return (GuiRenderStateLayerAccess) layer;
    }
}
