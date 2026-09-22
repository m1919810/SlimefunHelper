package me.matl114.accessors.access;

import java.util.OptionalInt;
import net.minecraft.entity.projectile.ProjectileEntity;

public interface ProjectileAccess {
    OptionalInt getOwnerEid();

    static ProjectileAccess of(ProjectileEntity projectile) {
        return (ProjectileAccess) projectile;
    }
}
