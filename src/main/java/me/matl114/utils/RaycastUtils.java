package me.matl114.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class RaycastUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static boolean raycastAnyBlock(Entity e, Vec3d from, Vec3d to){
        BlockHitResult bResult = mc.world.raycast(new RaycastContext(from, to,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, e));
        return bResult != null && bResult.getType() != HitResult.Type.MISS;
    }
    public static boolean raycastHitAnyEntity(Entity e, Vec3d from, Vec3d to){
        var re = ProjectileUtil.raycast(e, from, to, new Box(from, to), es -> !es.isSpectator() && es.canHit(), 16384);
        return re != null && re.getType() != HitResult.Type.MISS;
    }
    public static boolean raycastHitAnyEntityExceptPlayer(Entity e, Vec3d from, Vec3d to){
        var re = ProjectileUtil.raycast(e, from, to, new Box(from, to), es -> !es.isSpectator() && es.canHit() && es != mc.player, 16384);
        return re != null && re.getType() != HitResult.Type.MISS;
    }
}
