package me.matl114.utils.render;

import me.matl114.versioned.api.VDrawContext;
import me.matl114.versioned.impl.Render_v1_21_11;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public final class EntityOutlineRenderer extends EntityModelCollector {
    public EntityOutlineRenderer() {
        super(Render_v1_21_11::getOutlineVCP);
    }

    @Override
    protected void configureRenderState(EntityRenderState state, int color) {
        state.outlineColor = color;
    }

    @Override
    protected void collectQuad(CapturedQuad quad, int color) {}

    @Override
    protected void renderCollected(MatrixStack matrices) {}

    @Override
    protected void clearCollected() {}

    @Override
    public void render2D(VDrawContext context, float partialTick) {}
}
