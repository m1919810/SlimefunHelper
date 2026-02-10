package me.matl114.accessors.access;

import me.matl114.accessors.events.EntityAccess;
import net.minecraft.entity.LivingEntity;

public interface LivingEntityAccess<T extends LivingEntity> extends EntityAccess<T> {
    public void setJumpingCooldown(int cooldown);

    static <T extends LivingEntity> LivingEntityAccess<T> of(T val) {
        return (LivingEntityAccess<T>) val;
    }

    float getJumpUpwardSpeed(float strength);
}
