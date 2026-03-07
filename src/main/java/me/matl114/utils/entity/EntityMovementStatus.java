package me.matl114.utils.entity;

import me.matl114.utils.EntityUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseEffectsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.MathHelper;
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
    }

    public T entity;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean groundCollision;
    public Vec3d pos;
    public float pitch;
    public float yaw;
    public Vec3d vec;
    public float speed;
    public float horizontalSpeed;
    public float distanceTraveled;
    public boolean sprinting;

    public void restore() {
        this.entity.horizontalCollision = horizontalCollision;
        this.entity.verticalCollision = verticalCollision;
        this.entity.groundCollision = groundCollision;

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
        EntityUtils.setEntityYawSafe(this.entity, yaw);
    }

    public void restorePos() {
        this.entity.setPosition(pos);
    }

    public Vec3d calculateLastMoveVelocity(int forward, int sideward, boolean jump) {
        if (this.entity instanceof LivingEntity livingEntity) {
            Vec2f vec2f = new Vec2f(sideward, forward).normalize();
            vec2f = applyMovementFactors(vec2f);
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

    private Vec2f applyMovementFactors(Vec2f vec2f) {
        if (vec2f.lengthSquared() == 0) {
            return vec2f;
        }
        if (this.entity instanceof ClientPlayerEntity p) {
            vec2f = vec2f.multiply(0.98F);
            if (p.isUsingItem() && !p.hasVehicle()) {
                vec2f = vec2f.multiply(p.getActiveItem()
                        .getOrDefault(DataComponentTypes.USE_EFFECTS, UseEffectsComponent.DEFAULT)
                        .speedMultiplier());
            }
            if (p.shouldSlowDown()) {
                float f = (float) p.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
                vec2f = vec2f.multiply(f);
            }
            float f = vec2f.length();
            vec2f = vec2f.multiply(1.0F / f);
            float g = getDirectionalMovementSpeedMultiplier(vec2f);
            float h = Math.min(f * g, 1.0F);
            return vec2f.multiply(h);
        } else {
            return vec2f;
        }
    }

    private static float getDirectionalMovementSpeedMultiplier(Vec2f vec) {
        float f = Math.abs(vec.x);
        float g = Math.abs(vec.y);
        float h = g > f ? f / g : g / f;
        return MathHelper.sqrt(1.0F + MathHelper.square(h));
    }
}
