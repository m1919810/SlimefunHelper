package me.matl114.utils.render;

import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.util.math.MatrixStack;

public final class EntityBoundaryCollector extends EntityModelCollector {
    @Override
    public void render2D(VDrawContext context, float partialTick) {
        renderQuads2D(
                partialTick,
                (quads, camera, projection, color) -> maskRenderer.draw(quads, camera, projection, 0, color));
    }

    @Override
    protected void collectQuad(CapturedQuad quad, int color) {}

    @Override
    protected void renderCollected(MatrixStack matrices) {}

    @Override
    protected void clearCollected() {}
}
