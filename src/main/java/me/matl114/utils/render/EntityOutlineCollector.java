package me.matl114.utils.render;

import java.util.LinkedHashMap;
import java.util.Map;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class EntityOutlineCollector extends EntityModelCollector {
    private final Map<RenderElements.Line, Integer> lines = new LinkedHashMap<>();

    public EntityOutlineCollector() {
        super();
    }

    @Override
    protected void collectQuad(CapturedQuad capturedQuad, int color) {
        Vec3d[] vertices = capturedQuad.vertices();
        for (int index = 0; index < 4; index++) {
            RenderElements.Line line = new RenderElements.Line(vertices[index], vertices[(index + 1) & 3]);
            if (lines.containsKey(line)) {
                lines.remove(line);
            } else {
                lines.put(line, color);
            }
        }
    }

    @Override
    protected void renderCollected(MatrixStack matrices) {
        if (lines.isEmpty()) return;
        VRender.getInstance().createLinesLayer((operation, vertexConsumer) -> {
            for (Map.Entry<RenderElements.Line, Integer> entry : lines.entrySet()) {
                RenderElements.Line line = entry.getKey();
                operation.drawLine(
                        matrices,
                        vertexConsumer,
                        new Vec3d(line.x0(), line.y0(), line.z0()),
                        new Vec3d(line.x1(), line.y1(), line.z1()),
                        entry.getValue());
            }
        });
    }

    @Override
    protected void clearCollected() {
        lines.clear();
    }
}
