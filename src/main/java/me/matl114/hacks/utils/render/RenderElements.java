package me.matl114.hacks.utils.render;

import java.util.List;
import me.matl114.versioned.api.VRender;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class RenderElements {
    private static final int POSITION_FLAG = VRender.createTextPositionFlag(0, 1);

    private static double normalizeZero(double value) {
        return value == 0.0D ? 0.0D : value;
    }

    private static int comparePoint(double x0, double y0, double z0, double x1, double y1, double z1) {
        int cmp = Double.compare(x0, x1);
        if (cmp != 0) return cmp;
        cmp = Double.compare(y0, y1);
        if (cmp != 0) return cmp;
        return Double.compare(z0, z1);
    }

    private static boolean isFlat(double min, double max) {
        return Double.compare(normalizeZero(min), normalizeZero(max)) == 0;
    }

    public static record Text(net.minecraft.text.Text text, Vec3d position, int offSetFlag, float scale) {
        public Text(net.minecraft.text.Text text, Vec3d position) {
            this(text, position, POSITION_FLAG, 1.0F);
        }

        public Text(net.minecraft.text.Text text, Vec3d position, float scale) {
            this(text, position, POSITION_FLAG, scale);
        }
    }

    public static record Line(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Line(Vec3d from, Vec3d to) {
            this(from.x, from.y, from.z, to.x, to.y, to.z);
        }

        public Line {
            x0 = normalizeZero(x0);
            y0 = normalizeZero(y0);
            z0 = normalizeZero(z0);
            x1 = normalizeZero(x1);
            y1 = normalizeZero(y1);
            z1 = normalizeZero(z1);
            if (comparePoint(x1, y1, z1, x0, y0, z0) < 0) {
                double tx = x0;
                double ty = y0;
                double tz = z0;
                x0 = x1;
                y0 = y1;
                z0 = z1;
                x1 = tx;
                y1 = ty;
                z1 = tz;
            }
        }

        public Line offset(Vec3d delta) {
            return new Line(x0 + delta.x, y0 + delta.y, z0 + delta.z, x1 + delta.x, y1 + delta.y, z1 + delta.z);
        }
    }

    public static record Quad(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Quad(Vec3d min, Vec3d max) {
            this(min.x, min.y, min.z, max.x, max.y, max.z);
        }

        public Quad(Box box, Direction direction) {
            this(
                    switch (direction) {
                        case WEST -> box.getMinPos().x;
                        case EAST -> box.getMaxPos().x;
                        default -> box.getMinPos().x;
                    },
                    switch (direction) {
                        case DOWN -> box.getMinPos().y;
                        case UP -> box.getMaxPos().y;
                        default -> box.getMinPos().y;
                    },
                    switch (direction) {
                        case NORTH -> box.getMinPos().z;
                        case SOUTH -> box.getMaxPos().z;
                        default -> box.getMinPos().z;
                    },
                    switch (direction) {
                        case WEST -> box.getMinPos().x;
                        case EAST -> box.getMaxPos().x;
                        default -> box.getMaxPos().x;
                    },
                    switch (direction) {
                        case DOWN -> box.getMinPos().y;
                        case UP -> box.getMaxPos().y;
                        default -> box.getMaxPos().y;
                    },
                    switch (direction) {
                        case NORTH -> box.getMinPos().z;
                        case SOUTH -> box.getMaxPos().z;
                        default -> box.getMaxPos().z;
                    });
        }

        public Quad {
            minX = normalizeZero(Math.min(minX, maxX));
            minY = normalizeZero(Math.min(minY, maxY));
            minZ = normalizeZero(Math.min(minZ, maxZ));
            maxX = normalizeZero(Math.max(minX, maxX));
            maxY = normalizeZero(Math.max(minY, maxY));
            maxZ = normalizeZero(Math.max(minZ, maxZ));
        }

        public Quad offset(Vec3d delta) {
            return new Quad(
                    minX + delta.x, minY + delta.y, minZ + delta.z, maxX + delta.x, maxY + delta.y, maxZ + delta.z);
        }

        public me.matl114.utils.render.Quad toRenderQuad() {
            if (isFlat(minX, maxX)) {
                double x = minX;
                return new me.matl114.utils.render.Quad(
                        new Vec3d(x, minY, minZ),
                        new Vec3d(x, maxY, minZ),
                        new Vec3d(x, maxY, maxZ),
                        new Vec3d(x, minY, maxZ));
            }
            if (isFlat(minY, maxY)) {
                double y = minY;
                return new me.matl114.utils.render.Quad(
                        new Vec3d(minX, y, minZ),
                        new Vec3d(maxX, y, minZ),
                        new Vec3d(maxX, y, maxZ),
                        new Vec3d(minX, y, maxZ));
            }
            if (isFlat(minZ, maxZ)) {
                double z = minZ;
                return new me.matl114.utils.render.Quad(
                        new Vec3d(minX, minY, z),
                        new Vec3d(maxX, minY, z),
                        new Vec3d(maxX, maxY, z),
                        new Vec3d(minX, maxY, z));
            }
            throw new IllegalStateException("Quad is not a face");
        }
    }

    public static List<Line> boxOutline(Box box) {
        Vec3d min = box.getMinPos();
        Vec3d max = box.getMaxPos();
        boolean flatX = isFlat(min.x, max.x);
        boolean flatY = isFlat(min.y, max.y);
        boolean flatZ = isFlat(min.z, max.z);
        int flatCount = (flatX ? 1 : 0) + (flatY ? 1 : 0) + (flatZ ? 1 : 0);

        if (flatCount >= 2) {
            if (!flatX) {
                return List.of(new Line(min, max));
            }
            if (!flatY) {
                return List.of(new Line(min, max));
            }
            if (!flatZ) {
                return List.of(new Line(min, max));
            }
            return List.of();
        }

        if (flatCount == 1) {
            if (flatX) {
                return List.of(
                        new Line(new Vec3d(min.x, min.y, min.z), new Vec3d(min.x, max.y, min.z)),
                        new Line(new Vec3d(min.x, max.y, min.z), new Vec3d(min.x, max.y, max.z)),
                        new Line(new Vec3d(min.x, max.y, max.z), new Vec3d(min.x, min.y, max.z)),
                        new Line(new Vec3d(min.x, min.y, max.z), new Vec3d(min.x, min.y, min.z)));
            }
            if (flatY) {
                return List.of(
                        new Line(new Vec3d(min.x, min.y, min.z), new Vec3d(max.x, min.y, min.z)),
                        new Line(new Vec3d(max.x, min.y, min.z), new Vec3d(max.x, min.y, max.z)),
                        new Line(new Vec3d(max.x, min.y, max.z), new Vec3d(min.x, min.y, max.z)),
                        new Line(new Vec3d(min.x, min.y, max.z), new Vec3d(min.x, min.y, min.z)));
            }
            return List.of(
                    new Line(new Vec3d(min.x, min.y, min.z), new Vec3d(max.x, min.y, min.z)),
                    new Line(new Vec3d(max.x, min.y, min.z), new Vec3d(max.x, max.y, min.z)),
                    new Line(new Vec3d(max.x, max.y, min.z), new Vec3d(min.x, max.y, min.z)),
                    new Line(new Vec3d(min.x, max.y, min.z), new Vec3d(min.x, min.y, min.z)));
        }

        return List.of(
                new Line(new Vec3d(min.x, min.y, min.z), new Vec3d(max.x, min.y, min.z)),
                new Line(new Vec3d(max.x, min.y, min.z), new Vec3d(max.x, min.y, max.z)),
                new Line(new Vec3d(max.x, min.y, max.z), new Vec3d(min.x, min.y, max.z)),
                new Line(new Vec3d(min.x, min.y, max.z), new Vec3d(min.x, min.y, min.z)),
                new Line(new Vec3d(min.x, max.y, min.z), new Vec3d(max.x, max.y, min.z)),
                new Line(new Vec3d(max.x, max.y, min.z), new Vec3d(max.x, max.y, max.z)),
                new Line(new Vec3d(max.x, max.y, max.z), new Vec3d(min.x, max.y, max.z)),
                new Line(new Vec3d(min.x, max.y, max.z), new Vec3d(min.x, max.y, min.z)),
                new Line(new Vec3d(min.x, min.y, min.z), new Vec3d(min.x, max.y, min.z)),
                new Line(new Vec3d(max.x, min.y, min.z), new Vec3d(max.x, max.y, min.z)),
                new Line(new Vec3d(max.x, min.y, max.z), new Vec3d(max.x, max.y, max.z)),
                new Line(new Vec3d(min.x, min.y, max.z), new Vec3d(min.x, max.y, max.z)));
    }

    public static List<Quad> boxFaces(Box box) {
        Vec3d min = box.getMinPos();
        Vec3d max = box.getMaxPos();
        boolean flatX = isFlat(min.x, max.x);
        boolean flatY = isFlat(min.y, max.y);
        boolean flatZ = isFlat(min.z, max.z);
        int flatCount = (flatX ? 1 : 0) + (flatY ? 1 : 0) + (flatZ ? 1 : 0);

        if (flatCount >= 2) {
            return List.of();
        }
        if (flatCount == 1) {
            if (flatX) {
                return List.of(new Quad(box, Direction.WEST));
            }
            if (flatY) {
                return List.of(new Quad(box, Direction.DOWN));
            }
            return List.of(new Quad(box, Direction.NORTH));
        }

        return List.of(
                new Quad(box, Direction.DOWN),
                new Quad(box, Direction.UP),
                new Quad(box, Direction.NORTH),
                new Quad(box, Direction.SOUTH),
                new Quad(box, Direction.WEST),
                new Quad(box, Direction.EAST));
    }
}
