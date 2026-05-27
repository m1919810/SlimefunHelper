package me.matl114.accessors.gui;

import net.minecraft.client.gui.render.state.GuiRenderState;

public interface GuiRendererStateAccess {
    public void setLayerToDepth();

    public static GuiRendererStateAccess of(GuiRenderState state) {
        return (GuiRendererStateAccess) state;
    }
}
