package me.matl114.access;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public interface LivingEntityAccess<T extends LivingEntity> extends EntityAccess<T>{
    public void setJumpingCooldown(int cooldown);

    static <T extends LivingEntity> LivingEntityAccess<T> of(T val){
        return (LivingEntityAccess<T>) val;
    }

    float getJumpUpwardSpeed(float strength);


}
