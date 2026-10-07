package me.matl114.accessors.access;

import java.util.Optional;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public interface RenderLayerAccess {
    public Optional<Identifier> getRenderTexture();

    static RenderLayerAccess of(RenderLayer layer) {
        return (RenderLayerAccess) layer;
    }
}
