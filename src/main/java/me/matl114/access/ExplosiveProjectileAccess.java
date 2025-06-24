package me.matl114.access;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;

public interface ExplosiveProjectileAccess {
    public float getDragCommon();
    public float getDragMult();


    static ExplosiveProjectileAccess of(ExplosiveProjectileEntity entity){
        return (ExplosiveProjectileAccess) entity;
    }

}
