package me.matl114.utils.render;

import java.util.ArrayList;
import java.util.List;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class EntityFrameCollector extends EntityModelCollector {
    private final List<IndexEntry<RenderElements.Line>> lines = new ArrayList<>();

    public EntityFrameCollector() {
        super();
    }

    @Override
    protected void collectQuad(CapturedQuad capturedQuad, int color) {
        Vec3d[] vertices = capturedQuad.vertices();
        for (int index = 0; index < 4; index++) {
            lines.add(new IndexEntry<>(color, new RenderElements.Line(vertices[index], vertices[(index + 1) & 3])));
        }
    }

    @Override
    protected void renderCollected(MatrixStack matrices) {
        if (lines.isEmpty()) return;
        VRender.getInstance().createLinesLayer((operation, vertexConsumer) -> {
            for (IndexEntry<RenderElements.Line> entry : lines) {
                RenderElements.Line line = entry.val();
                operation.drawLine(
                        matrices,
                        vertexConsumer,
                        new Vec3d(line.x0(), line.y0(), line.z0()),
                        new Vec3d(line.x1(), line.y1(), line.z1()),
                        entry.index());
            }
        });
    }

    @Override
    protected void clearCollected() {
        lines.clear();
    }
}
