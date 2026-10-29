package me.matl114.utils.render;

import me.matl114.versioned.api.VDrawContext;

public final class EntityBoundaryCollector extends EntityOutlineCollector {
    @Override
    public void render2D(VDrawContext context, float partialTick) {
        renderQuads2D(
                partialTick,
                (quads, camera, projection, color) -> maskRenderer.draw(quads, camera, projection, 0, color));
    }
}
