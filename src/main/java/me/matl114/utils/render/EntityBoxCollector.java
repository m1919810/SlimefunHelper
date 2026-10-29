package me.matl114.utils.render;

import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

public final class EntityBoxCollector extends RenderCollector.Impl<Entity> {
    private final RenderCollector<Box> boxCollector;

    public EntityBoxCollector(boolean drawOutline, boolean drawSolid, boolean drawTraceLine) {
        boxCollector = RenderCollectors.createBoxCollector(drawOutline, drawSolid, drawTraceLine);
    }

    @Override
    public void clear() {
        super.clear();
        boxCollector.clear();
    }

    @Override
    public void render3D(MatrixStack matrices, float partialTick) {
        try {
            submitLerpedBoxes(partialTick);
            boxCollector.render3D(matrices, partialTick);
        } finally {
            boxCollector.clear();
        }
    }

    @Override
    public void render2D(VDrawContext vDrawContext, float partialTick) {
        try {
            submitLerpedBoxes(partialTick);
            boxCollector.render2D(vDrawContext, partialTick);
        } finally {
            boxCollector.clear();
        }
    }

    private void submitLerpedBoxes(float partialTick) {
        for (IndexEntry<Entity> entry : entries) {
            boxCollector.submit(RenderUtils.getLerpedBox(entry.val(), partialTick), entry.index());
        }
    }
}
