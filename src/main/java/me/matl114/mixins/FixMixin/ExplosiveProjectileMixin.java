package me.matl114.mixins.FixMixin;

import me.matl114.access.ExplosiveProjectileAccess;
import me.matl114.hackUtils.EntityTasks;
import me.matl114.hackUtils.RenderTasks;
import me.matl114.utils.MathUtils;
import me.matl114.utils.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(ExplosiveProjectileEntity.class)
public abstract class ExplosiveProjectileMixin  extends ProjectileEntity implements ExplosiveProjectileAccess {
    @Unique
    Vec3d lastVelocity;
    @Unique
    public Vec3d power;
    @Unique
    boolean updatePower = false;
    @Unique
    public void initPower(double x, double y, double z){
        power = new Vec3d(x, y, z);
    }
    @Shadow
    protected abstract float getDrag();
    @Shadow
    protected abstract float getDragInWater();

    @Shadow public double powerX;

    @Unique
    public float getDragMult(){
        return this.isTouchingWater()? getDragInWater(): getDrag();
    }

    @Override
    public float getDragCommon() {
        return getDrag();
    }

    @Override
    @Unique
    public Vec3d getPower(){
        return power;
    }

    public ExplosiveProjectileMixin(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }
    @Override
    public void setVelocityClient(double x, double y, double z){
        boolean update =!updatePower;
        if(lastVelocity != null && power != null){
            double newX ;
            double newY;
            double newZ;
            var h = getDragMult();
            newX = (x/h - lastVelocity.x);
            newY = (y/h - lastVelocity.y);
            newZ = (z/h - lastVelocity.z);
            //update |= MathUtils.squareSum(newX-power.x, newY-power.y, newZ -power.z)>0.001;
            power = new Vec3d(newX, newY, newZ);
            updatePower = true;
        }
        super.setVelocityClient(x, y, z);
        lastVelocity = this.getVelocity();
        if(update){
            RenderTasks.calPoweredProjectileTrace((ExplosiveProjectileEntity)(Object) this);
        }
    }


}
