package me.matl114.utils.render;

import java.util.Iterator;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
public class Triangle implements Iterable<Vec3d> {
    Vec3d vec3d1;
    Vec3d vec3d2;
    Vec3d vec3d3;

    @NotNull
    @Override
    public Iterator<Vec3d> iterator() {
        return Stream.of(vec3d1, vec3d2, vec3d3).iterator();
    }

    public Vec3d get(int index) {
        return switch (index % 3) {
            case 0 -> vec3d1;
            case 1 -> vec3d2;
            case 2 -> vec3d3;
            default -> throw new IndexOutOfBoundsException();
        };
    }
}
