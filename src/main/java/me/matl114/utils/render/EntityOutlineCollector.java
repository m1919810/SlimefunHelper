package me.matl114.utils.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class EntityOutlineCollector extends EntityModelCollector {
    private static final double GEOMETRY_EPSILON = 1.0E-5;

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

        boolean[] contained = findContainedQuads();
        Map<RenderElements.Line, List<Integer>> edgeOwners = new LinkedHashMap<>();
        for (int quadIndex = 0; quadIndex < quads.size(); quadIndex++) {
            if (contained[quadIndex]) continue;

            Vec3d[] vertices = quads.get(quadIndex).val().vertices();
            for (int index = 0; index < 4; index++) {
                RenderElements.Line line = new RenderElements.Line(vertices[index], vertices[(index + 1) & 3]);
                edgeOwners.computeIfAbsent(line, ignored -> new ArrayList<>()).add(quadIndex);
            }
        }

        if (edgeOwners.isEmpty()) return;
        VRender.getInstance().createLinesLayer((operation, vertexConsumer) -> {
            for (Map.Entry<RenderElements.Line, List<Integer>> entry : edgeOwners.entrySet()) {
                List<Integer> owners = entry.getValue();
                if (isCoplanarSharedEdge(owners)) continue;

                RenderElements.Line line = entry.getKey();
                int color = quads.get(owners.getFirst()).index();
                operation.drawLine(
                        matrices,
                        vertexConsumer,
                        new Vec3d(line.x0(), line.y0(), line.z0()),
                        new Vec3d(line.x1(), line.y1(), line.z1()),
                        color);
            }
        });
    }

    private boolean[] findContainedQuads() {
        boolean[] contained = new boolean[quads.size()];
        for (int outerIndex = 0; outerIndex < quads.size(); outerIndex++) {
            CapturedQuad outer = quads.get(outerIndex).val();
            for (int innerIndex = 0; innerIndex < quads.size(); innerIndex++) {
                if (outerIndex == innerIndex) continue;

                CapturedQuad inner = quads.get(innerIndex).val();
                if (!samePlane(outer, inner) || !containsQuad(outer, inner)) continue;

                boolean sameBounds = containsQuad(inner, outer);
                if (!sameBounds || outerIndex < innerIndex) {
                    contained[innerIndex] = true;
                }
            }
        }
        return contained;
    }

    private boolean isCoplanarSharedEdge(List<Integer> owners) {
        for (int first = 0; first < owners.size(); first++) {
            CapturedQuad firstQuad = quads.get(owners.get(first)).val();
            for (int second = first + 1; second < owners.size(); second++) {
                CapturedQuad secondQuad = quads.get(owners.get(second)).val();
                if (samePlane(firstQuad, secondQuad)) return true;
            }
        }
        return false;
    }

    private static boolean samePlane(CapturedQuad first, CapturedQuad second) {
        Vec3d firstNormal = faceNormal(first);
        Vec3d secondNormal = faceNormal(second);
        if (firstNormal == null || secondNormal == null) return false;
        if (firstNormal.crossProduct(secondNormal).lengthSquared() > GEOMETRY_EPSILON * GEOMETRY_EPSILON) return false;

        Vec3d[] firstVertices = first.vertices();
        Vec3d[] secondVertices = second.vertices();
        return Math.abs(secondVertices[0].subtract(firstVertices[0]).dotProduct(firstNormal)) <= GEOMETRY_EPSILON;
    }

    private static boolean containsQuad(CapturedQuad outer, CapturedQuad inner) {
        Vec3d[] outerVertices = outer.vertices();
        Vec3d[] innerVertices = inner.vertices();
        Vec3d normal = faceNormal(outer);
        if (normal == null) return false;

        for (Vec3d point : innerVertices) {
            if (Math.abs(point.subtract(outerVertices[0]).dotProduct(normal)) > GEOMETRY_EPSILON) {
                return false;
            }

            for (int index = 0; index < 4; index++) {
                Vec3d start = outerVertices[index];
                Vec3d edge = outerVertices[(index + 1) & 3].subtract(start);
                double side = edge.crossProduct(point.subtract(start)).dotProduct(normal);
                if (side < -GEOMETRY_EPSILON * Math.max(edge.length(), 1.0)) return false;
            }
        }
        return true;
    }

    private static Vec3d faceNormal(CapturedQuad quad) {
        Vec3d[] vertices = quad.vertices();
        Vec3d normal = vertices[1].subtract(vertices[0]).crossProduct(vertices[2].subtract(vertices[0]));
        double lengthSquared = normal.lengthSquared();
        if (lengthSquared <= GEOMETRY_EPSILON * GEOMETRY_EPSILON) return null;
        return normal.multiply(1.0 / Math.sqrt(lengthSquared));
    }

    @Override
    protected void clearCollected() {
        quads.clear();
    }
}
