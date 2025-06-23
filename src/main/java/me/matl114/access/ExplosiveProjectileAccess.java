package me.matl114.access;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;

public interface ExplosiveProjectileAccess {
    public float getDragCommon();
    public float getDragMult();
    public void initPower(double x, double y, double z);
    @Nullable
    public Vec3d getPower();
    static ExplosiveProjectileAccess of(ExplosiveProjectileEntity entity){
        return (ExplosiveProjectileAccess) entity;
    }
    static boolean powerNotZero(Vec3d power){
        return power !=null && power.lengthSquared() > 0.0000001;
    }
    static boolean powerDifferent(Vec3d power1, Vec3d power2){
        return power1.squaredDistanceTo(power2) > 0.001;
    }
}
