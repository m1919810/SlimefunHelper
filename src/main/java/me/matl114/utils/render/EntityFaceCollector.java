package me.matl114.utils.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class EntityFaceCollector extends EntityModelCollector {
    private final List<IndexEntry<CapturedQuad>> quads = new ArrayList<>();

    public EntityFaceCollector() {
        super();
    }

    @Override
    protected void collectQuad(CapturedQuad capturedQuad, int color) {

        quads.add(new IndexEntry<>(color, capturedQuad));
    }

    @Override
    protected void renderCollected(MatrixStack matrices) {
        if (!quads.isEmpty()) {
            Map<Identifier, List<IndexEntry<CapturedQuad>>> texturedQuads = new LinkedHashMap<>();
            List<IndexEntry<CapturedQuad>> solidQuads = new ArrayList<>();
            for (IndexEntry<CapturedQuad> entry : quads) {
                if (entry.val().texture() == null) {
                    solidQuads.add(entry);
                } else {
                    texturedQuads
                            .computeIfAbsent(entry.val().texture(), ignored -> new ArrayList<>())
                            .add(entry);
                }
            }

            for (Map.Entry<Identifier, List<IndexEntry<CapturedQuad>>> group : texturedQuads.entrySet()) {
                VRender.getInstance().createEntityHighlightLayer(group.getKey(), (operation, vertexConsumer) -> {
                    for (IndexEntry<CapturedQuad> entry : group.getValue()) {
                        CapturedQuad capturedQuad = entry.val();
                        Vec3d[] vertices = capturedQuad.vertices();
                        Quad quad = new Quad(vertices[0], vertices[1], vertices[2], vertices[3]);
                        operation.drawTexturedQuad(
                                matrices, vertexConsumer, quad, capturedQuad.uv(), ColorQuad.of(entry.index()));
                    }
                });
            }

            if (!solidQuads.isEmpty()) {
                VRender.getInstance()
                        .createQuadsLayer(
                                (operation, vertexConsumer) -> {
                                    for (IndexEntry<CapturedQuad> entry : solidQuads) {
                                        CapturedQuad capturedQuad = entry.val();
                                        Vec3d[] vertices = capturedQuad.vertices();
                                        operation.drawQuad(
                                                matrices,
                                                vertexConsumer,
                                                new Quad(vertices[0], vertices[1], vertices[2], vertices[3]),
                                                ColorQuad.of(entry.index()));
                                    }
                                },
                                false);
            }
        }
    }

    @Override
    protected void clearCollected() {
        quads.clear();
    }
}
