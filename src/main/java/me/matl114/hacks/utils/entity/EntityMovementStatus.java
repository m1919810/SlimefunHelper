package me.matl114.hacks.utils.entity;

import me.matl114.hacks.modules.combat.CombatExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.PlayerInputUtils;
import me.matl114.hooks.ViaFabricPlusHooks;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class EntityMovementStatus<T extends Entity> implements Cloneable {
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

    @Override
    @SuppressWarnings("unchecked")
    public EntityMovementStatus<T> clone() {
        try {
            return (EntityMovementStatus<T>) super.clone();
        } catch (CloneNotSupportedException exception) {
            throw new AssertionError(exception);
        }
    }

    /** Advances this snapshot by one tick without moving or updating the source entity. */
    public EntityMovementStatus<T> move0(PlayerInputUtils.Input input) {
        if (entity instanceof PlayerEntity && entity.hasVehicle()) return this;
        if (entity instanceof LivingEntity && !(entity instanceof ClientPlayerEntity)) {
            pos = CombatExtra.INSTANCE
                    .getTracker(entity)
                    .getNextInterpolatedPosition(entity, 1, CombatExtra.INSTANCE.maySkipInterpolationTick());
            return this;
        }
        if (!(entity instanceof ClientPlayerEntity)) {
            Vec3d tracked = entity.getTrackedPosition().getPos();
            pos = tracked.equals(Vec3d.ZERO) ? entity.getPos() : tracked;
            return this;
        }
        Vec3d movement = vec;
        boolean legacyVelocityThreshold =
                ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 4);
        boolean stopHorizontal = movement.horizontalLengthSquared() < 9.0E-6;
        double x = (legacyVelocityThreshold ? Math.abs(movement.x) < 0.003 : stopHorizontal) ? 0.0 : movement.x;
        double z = (legacyVelocityThreshold ? Math.abs(movement.z) < 0.003 : stopHorizontal) ? 0.0 : movement.z;
        movement = new Vec3d(x, Math.abs(movement.y) < 0.003 ? 0.0 : movement.y, z);
        if (entity instanceof ClientPlayerEntity) {
            if (input.sneak() || input.backward() || input.forwardSpeed() == 0) {
                sprinting = false;
            } else if (input.sprint()) {
                sprinting = true;
            }
        }
        float slipperiness = onGround
                ? entity.getEntityWorld()
                        .getBlockState(entity.getVelocityAffectingPos())
                        .getBlock()
                        .getSlipperiness()
                : 1.0F;
        boolean gliding = entity instanceof LivingEntity living && living.isFallFlying();
        boolean flying = entity instanceof ClientPlayerEntity player && player.getAbilities().flying;
        boolean inFluid = entity instanceof LivingEntity living && living.isInFluid();
        if (flying) {
            int verticalInput = input.upwardSpeed();
            if (verticalInput != 0 && entity instanceof ClientPlayerEntity player) {
                movement =
                        movement.add(0.0, verticalInput * player.getAbilities().getFlySpeed() * 3.0F, 0.0);
            }
        }
        if (entity instanceof PlayerEntity player && player.isSwimming()) {
            double lookY = Vec3d.fromPolar(pitch, yaw).y;
            double factor = lookY < -0.2 ? 0.085 : 0.06;
            if (lookY <= 0.0 || input.jump()) {
                movement = movement.add(0.0, (lookY - movement.y) * factor, 0.0);
            }
        }
        if (gliding) {
            movement = calculateGlidingVelocity();
        } else if (entity instanceof LivingEntity living) {
            boolean canJumpFromGround = !inFluid
                    || entity.isTouchingWater() && entity.getFluidHeight(FluidTags.WATER) <= entity.getSwimHeight()
                    || entity.isInLava() && entity.getFluidHeight(FluidTags.LAVA) <= entity.getSwimHeight();
            if (input.jump() && onGround && canJumpFromGround) {
                float jump = (float) living.getAttributeValue(EntityAttributes.JUMP_STRENGTH)
                        + living.getJumpBoostVelocityModifier();
                movement = new Vec3d(movement.x, Math.max(jump, movement.y), movement.z);
                if (sprinting) {
                    float angle = yaw * 0.017453292F;
                    movement = movement.add(-MathHelper.sin(angle) * 0.2, 0.0, MathHelper.cos(angle) * 0.2);
                }
            }
            movement = inFluid
                    ? calculateFluidMoveVelocity(input, movement)
                    : calculateLastMoveVelocity(input.forwardSpeed(), input.sidewaysSpeed(), 0.0F, movement);
        }

        if (entity instanceof PlayerEntity player && pos.equals(entity.getPos())) {
            movement = player.adjustMovementForSneaking(movement, MovementType.SELF);
        }
        Box box = entity.getBoundingBox().offset(pos.subtract(entity.getPos()));
        Vec3d adjusted = entity.noClip
                ? movement
                : pos.equals(entity.getPos())
                        ? entity.adjustMovementForCollisions(movement)
                        : Entity.adjustMovementForCollisions(
                                entity,
                                movement,
                                box,
                                entity.getEntityWorld(),
                                entity.getEntityWorld().getEntityCollisions(entity, box.stretch(movement)));
        pos = pos.add(adjusted);
        horizontalCollision = movement.x != adjusted.x || movement.z != adjusted.z;
        verticalCollision = movement.y != adjusted.y;
        groundCollision = verticalCollision && movement.y < 0.0;
        onGround = groundCollision || (onGround && movement.y == 0.0 && !verticalCollision);

        if (!gliding && entity instanceof LivingEntity living && !inFluid) {
            double gravity = entity.getFinalGravity();
            if (movement.y <= 0.0 && living.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                gravity = Math.min(gravity, 0.01);
            }
            double nextY = movement.y;
            var levitation = living.getStatusEffect(StatusEffects.LEVITATION);
            if (levitation != null) {
                nextY += (0.05 * (levitation.getAmplifier() + 1) - nextY) * 0.2;
            } else if (!flying && !entity.getEntityWorld().isChunkLoaded(entity.getBlockPos())) {
                nextY = pos.y > entity.getEntityWorld().getBottomY() ? -0.1 : 0.0;
            } else if (!flying) {
                nextY -= gravity;
            }
            nextY = flying ? movement.y * 0.6 : nextY * 0.98;
            vec = new Vec3d(movement.x * slipperiness * 0.91F, nextY, movement.z * slipperiness * 0.91F);
        } else if (inFluid && entity instanceof LivingEntity living) {
            if (entity.isTouchingWater()) {
                float drag = sprinting ? 0.9F : 0.8F;
                float efficiency = (float) living.getAttributeValue(EntityAttributes.WATER_MOVEMENT_EFFICIENCY);
                if (!onGround) efficiency *= 0.5F;
                drag += (0.54600006F - drag) * efficiency;
                if (living.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) drag = 0.96F;
                vec = movement.multiply(drag, 0.800000011920929, drag);
                vec = living.applyFluidMovingSpeed(entity.getFinalGravity(), movement.y <= 0.0, vec);
            } else {
                vec = living.applyFluidMovingSpeed(
                        entity.getFinalGravity(), movement.y <= 0.0, movement.multiply(0.5, 0.8, 0.5));
            }
        } else {
            vec = movement;
        }
        if (horizontalCollision) {
            vec = new Vec3d(movement.x != adjusted.x ? 0.0 : vec.x, vec.y, movement.z != adjusted.z ? 0.0 : vec.z);
        }
        if (verticalCollision) vec = new Vec3d(vec.x, 0.0, vec.z);
        return this;
    }

    private Vec3d calculateGlidingVelocity() {
        Vec3d direction = Vec3d.fromPolar(pitch, yaw);
        float angle = pitch * 0.017453292F;
        double horizontalDirection = direction.horizontalLength();
        double horizontalSpeed = vec.horizontalLength();
        double gravity = entity.getFinalGravity();
        if (vec.y <= 0.0
                && entity instanceof LivingEntity living
                && living.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            gravity = Math.min(gravity, 0.01);
        }
        double cosineSquared = MathHelper.square(Math.cos(angle));
        Vec3d result = vec.add(0.0, gravity * (-1.0 + cosineSquared * 0.75), 0.0);
        if (result.y < 0.0 && horizontalDirection > 0.0) {
            double lift = result.y * -0.1 * cosineSquared;
            result = result.add(
                    direction.x * lift / horizontalDirection, lift, direction.z * lift / horizontalDirection);
        }
        if (angle < 0.0F && horizontalDirection > 0.0) {
            double lift = horizontalSpeed * -MathHelper.sin(angle) * 0.04;
            result = result.add(
                    -direction.x * lift / horizontalDirection, lift * 3.2, -direction.z * lift / horizontalDirection);
        }
        if (horizontalDirection > 0.0) {
            result = result.add(
                    (direction.x / horizontalDirection * horizontalSpeed - result.x) * 0.1,
                    0.0,
                    (direction.z / horizontalDirection * horizontalSpeed - result.z) * 0.1);
        }
        return result.multiply(0.9900000095367432, 0.9800000190734863, 0.9900000095367432);
    }

    private Vec3d calculateFluidMoveVelocity(PlayerInputUtils.Input input, Vec3d currentVelocity) {
        Vec2f direction = movementInputVector(input.sidewaysSpeed(), input.forwardSpeed());
        direction = EntityUtils.applyMovementFactors(entity, direction);
        float speed = 0.02F;
        if (entity.isTouchingWater() && entity instanceof LivingEntity living) {
            float efficiency = (float) living.getAttributeValue(EntityAttributes.WATER_MOVEMENT_EFFICIENCY);
            if (!onGround) efficiency *= 0.5F;
            speed += (living.getMovementSpeed() - speed) * efficiency;
        }
        Vec3d velocity = currentVelocity.add(
                EntityUtils.movementInputToVelocity(new Vec3d(direction.x, 0.0, direction.y), speed, yaw));
        if (input.jump()
                && (!onGround
                        || entity.isTouchingWater() && entity.getFluidHeight(FluidTags.WATER) > entity.getSwimHeight()
                        || entity.isInLava() && entity.getFluidHeight(FluidTags.LAVA) > entity.getSwimHeight())) {
            velocity = velocity.add(0.0, 0.04, 0.0);
        }
        return velocity;
    }

    private static Vec2f movementInputVector(int sideward, int forward) {
        Vec2f input = new Vec2f(sideward, forward);
        return ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 4) ? input : input.normalize();
    }

    public void restore() {
        this.entity.horizontalCollision = horizontalCollision;
        this.entity.verticalCollision = verticalCollision;
        this.entity.groundCollision = groundCollision;
        this.entity.collidedSoftly = collidedSoftly;
        this.restorePosRot();
        this.restoreOnGround();
        this.restoreVelocity();
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

    public void restoreVelocity() {
        this.entity.setVelocity(vec);
    }

    public Vec3d calculateLastMoveVelocity(int forward, int sideward) {
        return calculateLastMoveVelocity(
                forward, sideward, entity instanceof LivingEntity living ? living.upwardSpeed : 0, vec);
    }

    private Vec3d calculateLastMoveVelocity(int forward, int sideward, float upward, Vec3d currentVelocity) {
        if (this.entity instanceof LivingEntity livingEntity) {
            Vec2f vec2f = movementInputVector(sideward, forward);
            vec2f = EntityUtils.applyMovementFactors(this.entity, vec2f);
            Vec3d vec3d2 = new Vec3d(vec2f.x, upward, vec2f.y);
            float f = onGround
                    ? this.entity
                            .getEntityWorld()
                            .getBlockState(this.entity.getVelocityAffectingPos())
                            .getBlock()
                            .getSlipperiness()
                    : 1.0F;
            float speed = livingEntity.getMovementSpeed(f);
            if (sprinting != livingEntity.isSprinting()) {
                speed *= sprinting ? 1.3F : 1.0F / 1.3F;
            }
            Vec3d more = EntityUtils.movementInputToVelocity(vec3d2, speed, yaw);
            Vec3d velocity = currentVelocity.add(more);
            velocity = applyClimbingSpeed0(livingEntity, velocity);
            return velocity;
        } else {
            return this.entity.getVelocity();
        }
    }

    private static Vec3d applyClimbingSpeed0(LivingEntity living, Vec3d motion) {
        if (!living.isClimbing()) return motion;
        double x = MathHelper.clamp(motion.x, -0.15000000596046448, 0.15000000596046448);
        double y = Math.max(motion.y, -0.15000000596046448);
        double z = MathHelper.clamp(motion.z, -0.15000000596046448, 0.15000000596046448);
        if (y < 0.0
                && living instanceof PlayerEntity
                && living.isHoldingOntoLadder()
                && !living.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)) {
            y = 0.0;
        }
        return new Vec3d(x, y, z);
    }
}
