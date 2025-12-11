package me.matl114.jsApi;

import me.matl114.utils.ApiMethod;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@ApiMethod
public class DataHelper {
    public static Vec3d createVec(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    public static double getVecX(Vec3d vec3d){
        return vec3d.x;
    }

    public static double getVecY(Vec3d vec3d){
        return vec3d.y;
    }

    public static double getVecZ(Vec3d vec3d){
        return vec3d.z;
    }

    public static BlockPos vecToBlockPos(Vec3d vec3d){
        return BlockPos.ofFloored(vec3d);
    }

    public static Identifier namespacedKey(String id){
        return Identifier.tryParse(id);
    }

    public static String getIdNamespace(Identifier id){
        return id.getNamespace();
    }
    public static String getIdKey(Identifier id){
        return id.getPath();
    }
}
