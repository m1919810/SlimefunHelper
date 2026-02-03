package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.combat.*;
import me.matl114.utils.*;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.List;

public class CombatTasks {
    public static void init(){

    }
    private static final MinecraftClient mc=MinecraftClient.getInstance();



    public static boolean isHoldingWeapon(ClientPlayerEntity player){
        ItemStack itemInHand = player.getStackInHand(Hand.MAIN_HAND);
        return itemInHand != null && isWeaponForMCPlayer(itemInHand);
    }
    public static boolean isWeaponForMCPlayer(ItemStack itemStack){

        var attr = itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attr != null && !attr.modifiers().isEmpty())return true;
        var ench = itemStack.get(DataComponentTypes.ENCHANTMENTS);

        if(ench != null &&( (ItemStackUtils.getEnchantmentLevel(ench, Enchantments.SHARPNESS) > 0) || (ItemStackUtils.getEnchantmentLevel(ench, Enchantments.SMITE) > 0))){
            return true;
        }
        return false;
    }

    public static boolean notSuitableForAttack(ItemStack item){
        return item.isEmpty() || (!ItemStackUtils.hasInPatch(item, DataComponentTypes.ATTRIBUTE_MODIFIERS)
            && (!VItem.getInstance().isWeapon(item)));
    }


    @ApiMethod
    public static List<Entity> getBowAimableEntitiesForPlayer(){
        return getTargetSelector().getAimableEntities(true);
    }




    @ApiMethod
    public static boolean attackEntity(PlayerEntity player,Entity target){
        return attack.attackEntity(target);
    }




    //todo add Auto crystal
    //return whether the attack will execute delay
    //todo: add attack target render, render the attackTarget if attack is on, refresh every two ticks



    //todo fixfixfixfixfix
    public static Vec2f calculatePitchYawPredict(float velocity, Vec3d extraVector, Vec3d targetVec) {
        double extraVectorLen = extraVector.length();
        final float g = 0.05f;
        if (extraVectorLen > 10 || velocity > 10){
            //tpBow case
            Vec2f vec2f = EntityUtils.rotationToPitchYaw(targetVec.normalize());
            return new Vec2f(vec2f.x, EntityUtils.getSafeYaw(mc.player, vec2f.y));
        }
        //ordinary case
        double hDistance0 = targetVec.horizontalLength();
        double hDistanceSq = hDistance0 * hDistance0;
        float velocitySq = velocity * velocity;
        float velocityPow4 = velocitySq * velocitySq;
        //fix: hDistance
        // 调整目标高度：y_adjusted = y - (h * deltaY / velocity)
        double adjustedY = targetVec.y - (hDistance0 * extraVector.y / velocity);
        Vec3d vecNorm = targetVec.normalize();
        // 代入修正后的y计算仰角
        Vec2f safeSolution = new Vec2f( (float) -Math.toDegrees(Math.atan(
            (velocitySq - Math.sqrt(
                velocityPow4 - g * (g * hDistanceSq + 2 * adjustedY * velocitySq)
            )) / (g * hDistance0)
        )), EntityUtils.getSafeYaw (mc.player, (float)Math.toDegrees(Math.atan2( -vecNorm.x, vecNorm.z)))) ;
        if(extraVectorLen < 1e-4){
            return safeSolution;
        }
        final double tolerance = 1e-4;
        final int maxIter = 30;

        // 计算目标水平距离和方向
        double hDistance = Math.sqrt(targetVec.x * targetVec.x + targetVec.z * targetVec.z);
        Vec3d hDir = (hDistance > 1e-4) ?
            new Vec3d(targetVec.x / hDistance, 0, targetVec.z / hDistance) :
            new Vec3d(1, 0, 0); // 避免除零

        if (hDistance < 1e-4) {
            // 垂直射击情况
            return safeSolution;
        }

        // 初始化迭代变量
        double pitchRad = 0; // 初始俯仰角（弧度）
        double yawRad = 0;   // 偏航角（弧度）
        double ex = extraVector.x;
        double ez = extraVector.z;
        double ey = extraVector.y;
        double hx = hDir.x;
        double hz = hDir.z;

        boolean converged = false;

        // 迭代求解
        for (int i = 0; i < maxIter; i++) {
            double cosPitch = Math.cos(pitchRad);
            double sinPitch = Math.sin(pitchRad);

            // 检查水平速度是否有效
            if (Math.abs(velocity * cosPitch) < 1e-5) {
                break; // 垂直发射情况
            }

            // 求解 λ (水平速度大小)
            double B = ex * hx + ez * hz;
            double C = (ex * ex + ez * ez) - (velocity * cosPitch) * (velocity * cosPitch);
            double discriminant = B * B - C;

            if (discriminant < 0) {
                break; // 无实数解
            }

            double lambda = B + Math.sqrt(discriminant); // 取正根
            if (lambda <= 1e-5) {
                break; // 无效解
            }

            // 求解偏航角 θ_y
            double cosYaw = (lambda * hx - ex) / (velocity * cosPitch);
            double sinYaw = (lambda * hz - ez) / (velocity * cosPitch);

            // 归一化处理
            double norm = Math.sqrt(cosYaw * cosYaw + sinYaw * sinYaw);
            if (norm < 1e-5) {
                break;
            }
            cosYaw /= norm;
            sinYaw /= norm;
            yawRad = Math.atan2(sinYaw, cosYaw);

            // 求解俯仰角 θ_p
            double t = hDistance / lambda;
            double u = (targetVec.y + 0.5 * g * t * t) * lambda / hDistance;
            u = (u - ey) / velocity;

            if (Math.abs(u) > 1.0) {
                break; // 超出可行域
            }

            double newPitchRad = Math.asin(u);

            // 检查收敛
            if (Math.abs(newPitchRad - pitchRad) < tolerance) {
                converged = true;
                pitchRad = newPitchRad;
                break;
            }
            pitchRad = newPitchRad;
        }

        // 返回有效解或安全解
        if (converged) {
            return new Vec2f(

                (float) -Math.toDegrees(pitchRad)  ,     // 转 Minecraft 俯仰角
                EntityUtils.getSafeYaw(mc.player, (float) (Math.toDegrees(yawRad) - 90f)) // 转 Minecraft 偏航角
            );
        } else {
            return safeSolution;
        }
    }
    //todo: fix alllll of them


;
    //道具锁人 使用弓箭相同的配置

    //todo: 自动搭路

    public static final ModuleGroup moduleManager = new ModuleGroup("Combat");
    @Getter
    public static CombatExtra combatExtra;
    @Getter
    public static TargetSelector targetSelector;
    @Getter
    public static PositionPredict positionPredict;
    @Getter
    public static Attack attack;
    @Getter
    public static AttackArua attackArua;
    @Getter
    public static BowEnhance bowEnhance;
    @Getter
    public static ProjectileEnhance projectileEnhance;
    private static void initModules(ModuleManager m){
        combatExtra = new CombatExtra()
            .register(m);
        targetSelector = new TargetSelector()
            .register(m);
        positionPredict = new PositionPredict()
            .register(m);
        attack = new Attack()
            .register(m);
        attackArua = new AttackArua()
            .register(m);
        bowEnhance = new BowEnhance()
            .register(m);
        projectileEnhance = new ProjectileEnhance()
            .register(m);
    }
    static {
        moduleManager.registerFactories(CombatTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
