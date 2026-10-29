package me.matl114.utils.render;

import java.util.ArrayList;
import java.util.List;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public class EntityOutlineCollector extends EntityModelCollector {
    private final List<IndexEntry<CapturedQuad>> quads = new ArrayList<>();

    public EntityOutlineCollector() {
        super();
    }

    @Override
    protected void collectQuad(CapturedQuad capturedQuad, int color) {
        if (capturedQuad.vertices().length == 4) {
            quads.add(new IndexEntry<>(color, capturedQuad));
        }
    }

    @Override
    protected void renderCollected(MatrixStack matrices) {
        if (quads.isEmpty()) return;

        VRender.getInstance().createLinesLayer((operation, vertexConsumer) -> {
            for (IndexEntry<CapturedQuad> entry : quads) {
                Vec3d[] vertices = entry.val().vertices();
                for (int index = 0; index < 4; index++) {
                    operation.drawLine(
                            matrices, vertexConsumer, vertices[index], vertices[(index + 1) & 3], entry.index());
                }
            }
        });
    }

    @Override
    protected void clearCollected() {
        quads.clear();
    }
}
