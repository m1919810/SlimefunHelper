package me.matl114.hacks.utils.entity;

import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.utils.EntityUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class EntityMovementStatus<T extends Entity> {
    public EntityMovementStatus(T entity) {
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
        distanceTraveled = entity.distanceTraveled;
        sprinting = entity.isSprinting();
        touchingWater = entity.isTouchingWater();
        collidedSoftly = entity.collidedSoftly;
        submergedInWater = entity.isSubmergedInWater();
        inPowderSnow = entity.inPowderSnow;
    }

    public T entity;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean groundCollision;
    public boolean collidedSoftly;
    public Vec3d pos;
    public float pitch;
    public float yaw;
    public Vec3d vec;
    public float speed;
    public float distanceTraveled;
    public boolean sprinting;
    public boolean touchingWater;
    public boolean submergedInWater;
    public boolean inPowderSnow;

    public void restore() {
        this.entity.horizontalCollision = horizontalCollision;
        this.entity.verticalCollision = verticalCollision;
        this.entity.groundCollision = groundCollision;
        this.entity.collidedSoftly = collidedSoftly;
        this.restorePosRot();
        this.restoreOnGround();

        this.entity.setVelocity(vec);
        this.entity.speed = speed;
        this.entity.distanceTraveled = distanceTraveled;
        this.entity.setSprinting(sprinting);
    }

    public void restoreOnGround() {
        this.entity.setOnGround(onGround);
    }

    public void restorePosRot() {
        this.restoreRotation();
        this.restorePos();
    }

    public void restoreRotation() {
        EntityUtils.setEntityPitchSafe(this.entity, pitch);
        if (this.entity instanceof ClientPlayerEntity clientPlayer) {
            PlayerStateManager.setPlayerYawSafe(clientPlayer, yaw);
        } else {
            EntityUtils.setEntityYawSafe(this.entity, yaw);
        }
    }

    public void restorePos() {
        this.entity.setPosition(pos);
        entity.touchingWater = touchingWater;
        entity.submergedInWater = submergedInWater;
        entity.inPowderSnow = inPowderSnow;
    }

    public Vec3d calculateLastMoveVelocity(int forward, int sideward) {
        if (this.entity instanceof LivingEntity livingEntity) {
            Vec2f vec2f = new Vec2f(sideward, forward).normalize();
            vec2f = EntityUtils.applyMovementFactors(this.entity, vec2f);
            Vec3d vec3d2 = new Vec3d(vec2f.x, this.entity instanceof LivingEntity lv ? lv.upwardSpeed : 0.0F, vec2f.y);
            float f = this.entity.isOnGround()
                    ? this.entity
                            .getEntityWorld()
                            .getBlockState(this.entity.getVelocityAffectingPos())
                            .getBlock()
                            .getSlipperiness()
                    : 1.0F;
            float speed = livingEntity.getMovementSpeed(f);
            Vec3d more = EntityUtils.movementInputToVelocity(vec3d2, speed, yaw);
            Vec3d velocity = this.vec.add(more);
            velocity = livingEntity.applyClimbingSpeed(velocity);
            return velocity;
        } else {
            return this.entity.getVelocity();
        }
    }
}
