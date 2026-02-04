package me.matl114.hacks.modules.combat;

import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

public class PositionPredict extends BaseModule {
    public static final String[] ATTACK_POS_PREDICT_TICK = {"attack", "pos-predict-tick"};

    public static final String[] COMBAT_PREDICT_MODE = {"attack", "pos-predict-mode"};

    public static final String[] COMBAT_EXACT_ATTACK_SHIELD = {"att-bot", "exact-tp-anti-shield"};

    public PositionPredict() {

    }

    public final IntRef attackPredictTick = builder(Configs.COMBAT_CONFIG, ATTACK_POS_PREDICT_TICK, IntRef.TYPE)
        .defaultValue(2)
        .build();

    public final FlagRef enableNoShield = builder(Configs.COMBAT_CONFIG, COMBAT_EXACT_ATTACK_SHIELD, Boolean.class)
        .defaultValue(false)
        .build();

    public final EnumRef<PredictMode> predictMode = builder(Configs.COMBAT_CONFIG, COMBAT_PREDICT_MODE, PredictMode.class)
        .defaultValue(PredictMode.QUADRATIC)
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public Vec3d predictPosition(Entity entity) {
        return EntityInternalAccess.of(entity).predictPosition(attackPredictTick.get(), predictMode.get().ordinal());
    }

    public Vec3d getExactAttackPosition(Entity target){
        if(mc.player == null)return null;
        if(target instanceof ShulkerEntity){
            //consider wtf shit , this entity collides with player
            //consider all collisions use bounding box not directions
            Vec3d vec3 = target.getPos();
//            BlockPos posAt = BlockPos.ofFloored(vec3);
            Box boundingBox = target.getBoundingBox();
            for (Direction dir : Direction.values()){

                Vec3d testPos = switch (dir){
                    case UP -> vec3.withAxis(Direction.Axis.Y, boundingBox.maxY + 0.1);
                    case DOWN -> vec3.withAxis(Direction.Axis.Y, boundingBox.minY - 2);
                    case NORTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.minZ - 0.5);
                    case SOUTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.maxZ + 0.5);
                    case EAST -> vec3.withAxis(Direction.Axis.X, boundingBox.maxX + 0.5);
                    case WEST -> vec3.withAxis(Direction.Axis.X, boundingBox.minX - 0.5);
                };

                if(!MovTasks.ENGIN.checkEnvironmentCollision(mc.player, testPos)){
                    return testPos;
                }
            }
            return null;
        } else {
            //fixme use player facing when considerShield
            boolean considerAntiShield = considerAntiShield(target);
            Vec3d deltaMovments;
            //todo: how to combine shielding and predicting
            if(considerAntiShield){
                deltaMovments = target.getRotationVector().normalize().multiply(-0.2);
            } else if(target instanceof PlayerEntity playerEntity){
                Vec3d predictedPosition = predictPosition(playerEntity);
                deltaMovments = predictedPosition.subtract(target.getPos());
            } else{
                Vec3d targetFacing = mc.player.getPos().subtract(target.getPos());
                Vec3d targetFacingHorizontal = new Vec3d(targetFacing.x, 0.0d, targetFacing.z);
                double multiply =  0.5;
                deltaMovments = targetFacingHorizontal.normalize().multiply(multiply);
            }

            Vec3d targetPos = target.getPos();
            Vec3d actualMove =  MovTasks.ENGIN.simulateMovement(mc.player, targetPos, deltaMovments);
            return targetPos.add(actualMove);
        }

    }

    public Vec3d predictAimPositionForEntity(Entity entity, float finalVelocity){
        Vec3d estimatedDelta = entity.getPos().subtract(mc.player.getPos());
        double estimateSpeed =  estimatedDelta.length() / (finalVelocity);
        int estimateTick ;
        if(estimateSpeed < 2.0){
            estimateTick = 0;
        }else if(estimateSpeed > 20.0){
            estimateTick = 20;
        }else{
            estimateTick = (int) (estimateSpeed - 2.0D);
        }

        return entity.getEyePos().subtract(entity.getPos()).multiply(0.75).add(
            EntityInternalAccess.of(entity).predictPosition((attackPredictTick.get() + estimateTick), predictMode.get().ordinal())
        );
    }

    public boolean considerAntiShield(Entity target){
        return enableNoShield.get() && target instanceof LivingEntity livingEntity && livingEntity.isUsingItem() && livingEntity.getActiveItem().getItem() instanceof ShieldItem;
    }

    public enum PredictMode implements ConfigEnum {
        NO_PREDICT,
        LINEAR,
        QUADRATIC,
        PREDICTOR_NV;

        @Override
        public Text getDisplay(){
            return Text.translatable("configenum.predict-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }
}
