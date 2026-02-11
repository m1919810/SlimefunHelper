package me.matl114.mixins.access;

import me.matl114.accessors.access.ExplosiveProjectileAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(ExplosiveProjectileEntity.class)
public abstract class ExplosiveProjectileMixin extends ProjectileEntity implements ExplosiveProjectileAccess {

    @Shadow
    protected abstract float getDrag();

    @Shadow
    protected abstract float getDragInWater();

    @Unique
    public float getDragMult() {
        return this.isTouchingWater() ? getDragInWater() : getDrag();
    }

    @Override
    public float getDragCommon() {
        return getDrag();
    }

    public ExplosiveProjectileMixin(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }
}
