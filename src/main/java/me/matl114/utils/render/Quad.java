package me.matl114.utils.render;

import java.util.Iterator;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

@Data
@AllArgsConstructor
public class Quad implements Iterable<Vec3d> {
    Vec3d vec3d1;
    Vec3d vec3d2;
    Vec3d vec3d3;
    Vec3d vec3d4;

    @NotNull
    @Override
    public Iterator<Vec3d> iterator() {
        return Stream.of(vec3d1, vec3d2, vec3d3, vec3d4).iterator();
    }

    /**
     * create a quick rectangular for drawing
     * the return sequence is in
     *   2 -> 3
     *   \    \
     *   1 <- 4
     * @return
     */
    public static Quad rectangularXY(Vec3d min, Vec3d max) {
        return new Quad(min, new Vec3d(min.x, max.y, min.z), max, new Vec3d(max.x, min.y, max.z));
    }
    /**
     * create a quick rectangular for drawing
     * the return sequence is in
     *   2 -> 3
     *   \    \
     *   1 <- 4
     * @return
     */
    public static Quad rectangularXY(int x1, int y1, int x2, int y2, int z) {
        return new Quad(new Vec3d(x1, y1, z), new Vec3d(x1, y2, z), new Vec3d(x2, y2, z), new Vec3d(x2, y1, z));
    }

    /**
     * drawing a picture in the world,
     * pictures are rendered in X+Y+, but in world, x+ y -  is natural
     * so we have to change the sequence of vectors
     * the return sequence is in
     *     1 <- 4
     *     \    \
     *     2 -> 3
     * @return
     */
    public static Quad textureXY(Vec3d min, Vec3d max) {
        return new Quad(new Vec3d(max.x, min.y, max.z), min, new Vec3d(min.x, max.y, min.z), max);
    }
    /**
     * drawing a picture in the world,
     * pictures are rendered in X+Y+, but in world, x+ y -  is natural
     * so we have to change the sequence of vectors
     * the return sequence is in
     *     1 <- 4
     *     \    \
     *     2 -> 3
     * @return
     */
    public static Quad textureXY(int x1, int y1, int x2, int y2, int z) {
        return new Quad(new Vec3d(x1, y2, z), new Vec3d(x1, y1, z), new Vec3d(x2, y1, z), new Vec3d(x2, y2, z));
    }

    public Vec3d get(int index) {
        return switch (index & 3) {
            case 0 -> vec3d1;
            case 1 -> vec3d2;
            case 2 -> vec3d3;
            case 3 -> vec3d4;
            default -> throw new IndexOutOfBoundsException();
        };
    }
}
