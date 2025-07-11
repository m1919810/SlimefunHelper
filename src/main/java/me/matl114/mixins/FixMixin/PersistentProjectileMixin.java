package me.matl114.mixins.FixMixin;

import me.matl114.hackUtils.RenderTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileMixin extends ProjectileEntity  {
    public PersistentProjectileMixin(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }
    @Unique
    private int update = 0;


    @Inject(method = "setVelocityClient", at = @At("RETURN"))
    public void updateVelocityFirst(double x, double y, double z, CallbackInfo ci){
        super.setVelocityClient(x, y, z);
        //接近0, 忽略
        if(this.getVelocity().lengthSquared() < 1e-10)return;
        if(update < 3){
            update += 1;
            //防止初始脏数据
            if(update == 3){
                if(((Object)this) instanceof TridentEntity tridentEntity){
                    //what
                }else {
                    RenderTasks.calArrowTrace((PersistentProjectileEntity)(Object) this );
                }
            }
        }
    }
}
