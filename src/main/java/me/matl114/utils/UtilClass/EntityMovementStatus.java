package me.matl114.utils.UtilClass;

import lombok.experimental.Accessors;
import me.matl114.utils.EntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.gen.Accessor;


public class EntityMovementStatus<T extends Entity>{
    public EntityMovementStatus(T entity){
        this.entity = entity;
        onGround = entity.isOnGround();
        horizontalCollision = entity.horizontalCollision;
        verticalCollision = entity.verticalCollision;
        groundCollision = entity.groundCollision;
        pos = entity.getPos();
        pitch = entity.getPitch();
        yaw = entity.getYaw();
        vec = entity.getVelocity();
        speed = entity.speed;
        horizontalSpeed = entity.horizontalSpeed;
        distanceTraveled = entity.distanceTraveled;
        sprinting = entity.isSprinting();
    }
    public T entity;
    public boolean onGround ;
    public boolean horizontalCollision ;
    public boolean verticalCollision ;
    public boolean groundCollision;
    public Vec3d pos ;
    public float pitch;
    public float yaw;
    public Vec3d vec ;
    public float speed ;
    public float horizontalSpeed ;
    public float distanceTraveled ;
    public boolean sprinting ;
    public void restore(){
        this.entity.horizontalCollision = horizontalCollision;
        this.entity.verticalCollision = verticalCollision;
        this.entity.groundCollision = groundCollision;

        this.restorePosRot();
        this.restoreOnGround();

        this.entity.setVelocity(vec);
        this.entity.speed = speed;
        this.entity.horizontalSpeed = horizontalSpeed;
        this.entity.distanceTraveled = distanceTraveled;
        this.entity.setSprinting(sprinting);
    }
    public void restoreOnGround(){
        this.entity.setOnGround(onGround);
    }
    public void restorePosRot(){
        this.restoreRotation();
        this.restorePos();
    }
    public void restoreRotation(){
        EntityUtils.setEntityPitchSafe(this.entity, pitch);
        EntityUtils.setEntityYawSafe(this.entity, yaw);
    }
    public void restorePos(){
        this.entity.setPosition(pos);
    }
}