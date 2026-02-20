package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;
import org.joml.Vector3f;

@ApiMethod
public class DataHelper {
    public static Vec3d createVec(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    public static Object createPos3d(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    public static Vec3d createVec(Object pos3d) {
        if (pos3d instanceof Vec3d) {
            return (Vec3d) pos3d;
        } else if (pos3d instanceof Vec3i pos) {
            return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        } else if (pos3d instanceof Vector3d pos3D) {
            return new Vec3d(pos3D.x, pos3D.y, pos3D.z);
        } else if (pos3d instanceof Vector3f pos3D) {
            return new Vec3d(pos3D.x, pos3D.y, pos3D.z);
        } else {
            Object cast = JsMacrosBridge.getInstance().forceUnwrap(pos3d, Object.class);
            if (cast instanceof Vec3d vec3d) {
                return vec3d;
            } else if (cast instanceof Vec3i vec3i) {
                return Vec3d.of(vec3i);
            } else {
                throw new IllegalArgumentException("Unsupported vec3 type: " + pos3d.getClass());
            }
        }
    }

    public static Object createPos3d(Object pos) {
        return JsMacrosBridge.getInstance().wrap(createVec(pos));
    }

    public static BlockPos createBlockPos(double x, double y, double z) {
        return new BlockPos((int) x, (int) y, (int) z);
    }

    public static BlockPos createBlockPos(Object pos3d) {
        return pos3d instanceof BlockPos pp ? pp : BlockPos.ofFloored(createVec(pos3d));
    }

    public static double getVecX(Vec3d vec3d) {
        return vec3d.x;
    }

    public static double getVecY(Vec3d vec3d) {
        return vec3d.y;
    }

    public static double getVecZ(Vec3d vec3d) {
        return vec3d.z;
    }

    public static BlockPos vecToBlockPos(Vec3d vec3d) {
        return BlockPos.ofFloored(vec3d);
    }

    public static Identifier namespacedKey(String id) {
        return Identifier.tryParse(id);
    }

    public static String getIdNamespace(Identifier id) {
        return id.getNamespace();
    }

    public static String getIdKey(Identifier id) {
        return id.getPath();
    }

    public static double squaredDistance(Object vec1, Object vec2) {
        Vec3d vec3d1 = createVec(vec1);
        Vec3d vec3d2 = createVec(vec2);
        return vec3d1.squaredDistanceTo(vec3d2);
    }

    public static Vec3d normalize(Object vec1) {
        return createVec(vec1).normalize();
    }
}
