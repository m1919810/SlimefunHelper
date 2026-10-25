package me.matl114.versioned.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class OutlineRenderer {
    public void render(Iterable<? extends Quad> quads, int color) {
        draw(quads, color, true);
    }

    public void renderDeferred(Iterable<? extends Quad> quads, OrderedRenderCommandQueue queue, int color) {
        Map<RenderLayer, List<Quad>> quadsByLayer = new LinkedHashMap<>();
        for (Quad quad : quads) {
            quadsByLayer
                    .computeIfAbsent(quad.layer(), ignored -> new ArrayList<>())
                    .add(quad);
        }
        for (Map.Entry<RenderLayer, List<Quad>> group : quadsByLayer.entrySet()) {
            List<Quad> layerQuads = List.copyOf(group.getValue());
            queue.submitCustom(new MatrixStack(), group.getKey(), (entry, vertexConsumer) -> {
                draw(layerQuads, color, false);
            });
        }
    }

    private void draw(Iterable<? extends Quad> quads, int color, boolean flush) {
        OutlineVertexConsumerProvider vertexConsumers = Render_v1_21_11.getOutlineVCP();
        vertexConsumers.setColor(color);
        MatrixStack matrices = new MatrixStack();
        MatrixStack.Entry matrix = matrices.peek();
        for (Quad quad : quads) {
            VertexConsumer consumer = vertexConsumers.getBuffer(quad.layer());
            Vec3d[] vertices = quad.vertices();
            for (int index = 0; index < vertices.length; index++) {
                Vec3d vertex = vertices[index];
                consumer.vertex(matrix, (float) vertex.x, (float) vertex.y, (float) vertex.z)
                        .texture(quad.u(index), quad.v(index))
                        .color(color);
            }
        }
        if (flush) {
            vertexConsumers.draw();
        }
    }

    public interface Quad {
        RenderLayer layer();

        Vec3d[] vertices();

        float u(int index);

        float v(int index);
    }
}
