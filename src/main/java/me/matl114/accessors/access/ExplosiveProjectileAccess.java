package me.matl114.accessors.access;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;

public interface ExplosiveProjectileAccess {
    public float getDragCommon();
    public float getDragMult();


    static ExplosiveProjectileAccess of(ExplosiveProjectileEntity entity){
        return (ExplosiveProjectileAccess) entity;
    }

}
