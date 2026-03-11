package me.matl114.accessors.access;

import net.minecraft.entity.projectile.FireworkRocketEntity;

public interface FireworkRocketEntityAccess {
    public boolean isFallFlyingAccelerator();

    public int getLiveTicks();

    static FireworkRocketEntityAccess of(FireworkRocketEntity fireworkRocketEntity) {
        return (FireworkRocketEntityAccess) fireworkRocketEntity;
    }
}
