package me.matl114.versioned.accessors;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LayeredDrawer;
import net.minecraft.client.render.RenderTickCounter;

public interface LayeredDrawerAccess {
    public void setPostRenderTask(BiConsumer<DrawContext, RenderTickCounter> runnable);

    public static LayeredDrawerAccess of(LayeredDrawer drawer) {
        return (LayeredDrawerAccess) drawer;
    }
}
