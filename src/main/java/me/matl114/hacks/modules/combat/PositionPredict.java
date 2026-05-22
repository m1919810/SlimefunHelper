package me.matl114.hacks.modules.combat;

import java.util.Locale;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.entity.EntityMovementStatus;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class PositionPredict extends BaseModule {
    public final ModulePath attack = makePath(Configs.COMBAT_CONFIG, "attack");
    public final ModulePath attBot = makePath(Configs.COMBAT_CONFIG, "att-bot");

    public PositionPredict() {}

    public final IntRef attackPredictTick = intBuilder(attack.add("pos-predict-tick"))
            .defaultValue(2)
            .build();

    public final FlagRef enableNoShield = builder(attBot.add("exact-tp-anti-shield"), Boolean.class)
            .defaultValue(false)
            .build();

    public final EnumRef<PredictMode> predictMode = builder(attack.add("pos-predict-mode"), PredictMode.class)
            .defaultValue(PredictMode.QUADRATIC)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public Vec3d predictPosition(Entity entity) {
        return EntityInternalAccess.of(entity)
                .predictPosition(attackPredictTick.get(), predictMode.get().ordinal());
    }

    public Vec3d getExactAttackPosition(Entity target) {
        if (mc.player == null) return null;
        if (target instanceof ShulkerEntity) {
            // consider wtf shit , this entity collides with player
            // consider all collisions use bounding box not directions
            Vec3d vec3 = target.getPos();
            //            BlockPos posAt = BlockPos.ofFloored(vec3);
            Box boundingBox = target.getBoundingBox();
            for (Direction dir : Direction.values()) {

                Vec3d testPos =
                        switch (dir) {
                            case UP -> vec3.withAxis(Direction.Axis.Y, boundingBox.maxY + 0.1);
                            case DOWN -> vec3.withAxis(Direction.Axis.Y, boundingBox.minY - 2);
                            case NORTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.minZ - 0.5);
                            case SOUTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.maxZ + 0.5);
                            case EAST -> vec3.withAxis(Direction.Axis.X, boundingBox.maxX + 0.5);
                            case WEST -> vec3.withAxis(Direction.Axis.X, boundingBox.minX - 0.5);
                        };

                if (!MovTasks.ENGIN.checkEnvironmentCollision(mc.player, testPos, true)) {
                    return testPos;
                }
            }
            return null;
        } else {
            boolean considerAntiShield = considerAntiShield(target);
            Vec3d deltaMovments;
            if (considerAntiShield) {
                deltaMovments = target.getRotationVector().normalize().multiply(-0.2);
            } else if (target instanceof PlayerEntity playerEntity) {
                Vec3d predictedPosition = predictPosition(playerEntity);
                deltaMovments = predictedPosition.subtract(target.getPos());
            } else {
                Vec3d targetFacing = mc.player.getPos().subtract(target.getPos());
                Vec3d targetFacingHorizontal = new Vec3d(targetFacing.x, 0.0d, targetFacing.z);
                double multiply = 0.5;
                deltaMovments = targetFacingHorizontal.normalize().multiply(multiply);
            }

            Vec3d targetPos = target.getPos();
            Vec3d actualMove = MovTasks.ENGIN.simulateMovement(mc.player, targetPos, deltaMovments);
            return targetPos.add(actualMove);
        }
    }

    public Vec3d predictAimPositionForEntity(Entity entity, float finalVelocity) {
        Vec3d estimatedDelta = entity.getPos().subtract(mc.player.getPos());
        double estimateSpeed = estimatedDelta.length() / (finalVelocity);
        int estimateTick;
        if (estimateSpeed < 2.0) {
            estimateTick = 0;
        } else if (estimateSpeed > 20.0) {
            estimateTick = 20;
        } else {
            estimateTick = (int) (estimateSpeed - 2.0D);
        }

        return entity.getEyePos()
                .subtract(entity.getPos())
                .multiply(0.75)
                .add(EntityInternalAccess.of(entity)
                        .predictPosition(
                                (attackPredictTick.get() + estimateTick),
                                predictMode.get().ordinal()));
    }

    public boolean considerAntiShield(Entity target) {
        return enableNoShield.get()
                && target instanceof LivingEntity livingEntity
                && livingEntity.isUsingItem()
                && livingEntity.getActiveItem().getItem() instanceof ShieldItem;
    }

    public Vec3d predictPlayerMove(PlayerInputUtils.Input input) {
        EntityMovementStatus<Entity> entityMovementStatus = new EntityMovementStatus<>(mc.player);
        if (mc.player.isFallFlying()) {
            return mc.player.getVelocity();
        } else if (mc.player.isInFluid()) {
            return mc.player.getVelocity();
        } else {
            return entityMovementStatus.calculateLastMoveVelocity(input.forwardSpeed(), input.sidewaysSpeed());
        }
    }

    public enum PredictMode implements ConfigEnum {
        NO_PREDICT,
        LINEAR,
        QUADRATIC,
        PREDICTOR_NV;

        @Override
        public Text getDisplay() {
            return Text.translatable("configenum.predict-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }
}
