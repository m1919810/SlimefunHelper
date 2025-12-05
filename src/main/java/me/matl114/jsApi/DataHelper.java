package me.matl114.jsApi;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class DataHelper {
    public static double getVecX(Vec3d vec3d){
        return vec3d.x;
    }

    public static double getVecY(Vec3d vec3d){
        return vec3d.y;
    }

    public static double getVecZ(Vec3d vec3d){
        return vec3d.z;
    }

    public static BlockPos alignToBlock(Vec3d vec3d){
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
