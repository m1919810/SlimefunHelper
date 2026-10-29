package me.matl114.versioned.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;

public final class OutlineRenderer {
    public void render(Iterable<? extends Quad> quads, int color) {
        draw(quads, color);
    }

    private void draw(Iterable<? extends Quad> quads, int color) {
        OutlineVertexConsumerProvider vertexConsumers = MinecraftClient.getInstance().getBufferBuilders().getOutlineVertexConsumers();
        vertexConsumers.setColor(
                ColorHelper.Argb.getRed(color),
                ColorHelper.Argb.getGreen(color),
                ColorHelper.Argb.getBlue(color),
                ColorHelper.Argb.getAlpha(color));
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
        vertexConsumers.draw();
    }

    public interface Quad {
        RenderLayer layer();

        Vec3d[] vertices();

        float u(int index);

        float v(int index);
    }
}
