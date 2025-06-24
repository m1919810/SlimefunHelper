package me.matl114.hackUtils;

import com.google.common.util.concurrent.AtomicDouble;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CombatTasks {
    public static void init(){

    }
    private static final MinecraftClient mc=MinecraftClient.getInstance();
    private static final Config.StringRef COMBAT_WHITELISTED= Configs.COMBAT_CONFIG.getString(Configs.ATTACK_WHITELISTED);
    private static final HashSet<EntityType<?>> WHITELISTED_ENTITIES = new HashSet<>();
    private static final AtomicBoolean attackHostile=new AtomicBoolean(true);
    private static final AtomicDouble attackRange=Configs.COMBAT_CONFIG.getDouble(Configs.ATTACK_RANGE);
    private static final Config.StringRef COMBAT_FRIEND = Configs.COMBAT_CONFIG.getString(Configs.ATTACK_PLAYER_FRIENDLIST);
    private static Predicate<String> COMBAT_FRIEND_PATTERN = (str)->true;
    private static final AtomicBoolean attackNamed=Configs.COMBAT_CONFIG.getBoolean(Configs.ATTACK_NAMED);

    public static double getAttackRange(){
        double d=attackRange.doubleValue();
        return  d;
    }
    private static void updateWhitelist(String value){
        EntityUtils.parseEntityWhiteList(value,WHITELISTED_ENTITIES);
        if(Pattern.matches(value,"hostile")){
            attackHostile.set(true);
        }else {
            attackHostile.set(false);
        }
    }
    private static boolean passWhitelistCheck(Entity e){
        return WHITELISTED_ENTITIES.contains(e.getType()) ||(attackHostile.get() &&
                    (
                            e instanceof Angerable angerable && mc.player.getUuid().equals(angerable.getAngryAt())
                    )
                );
    }
    private static boolean passExtraCheck(Entity e){
        if(e instanceof PlayerEntity player){
            String name = player.getGameProfile().getName();
            boolean match = COMBAT_FRIEND_PATTERN.test(name);
            if(match){
                return false;
            }
            boolean noMatch = COMBAT_FRIEND_PATTERN.test("!"+name);
            if(noMatch){
                return true;
            }
        }else{
            if(!attackNamed.get() && e.hasCustomName()){
                return false;
            }
        }
        return true;
    }
    private static boolean isAttackable(Entity e){
        return e!=null&&e!=mc.player&&( (e instanceof LivingEntity && ((LivingEntity)e).getHealth() > 0)
                || e instanceof EndCrystalEntity
                || e instanceof ShulkerBulletEntity)&&passWhitelistCheck(e)&&passExtraCheck(e);
    }
    private static boolean isRangeAttackable(Entity e){
        return e.getBoundingBox().squaredMagnitude(mc.player.getEyePos())< MathUtils.s2(getAttackRange());
    }
    private static boolean notSuitableForAttack(Item item){
        return (item instanceof MiningToolItem && !(item instanceof AxeItem))||(!(item instanceof ToolItem));
    }
    private static boolean isAttackablePlayerOrElse(Entity e){
        if(e instanceof PlayerEntity){
            return true;
        }else {
            return true;
        }
    }
    public static List<Entity> getAttackableEntitiesForPlayer(){
        return StreamSupport.stream( mc.world.getEntities().spliterator(),true).filter(CombatTasks::isAttackable).filter(CombatTasks::isRangeAttackable).filter(CombatTasks::isAttackablePlayerOrElse)
                .collect(Collectors.toCollection(ArrayList::new));
    }
    public static void attackEntity(PlayerEntity player,Entity target){
        mc.interactionManager.attackEntity(player,target);
    }
    //todo add friend name matcher/{

    //todo add Auto crystal
    public static void autoAttackBest(boolean force){
        PlayerEntity player=mc.player;
        if(player!=null&&mc.world!=null){
            if(!force && mc.crosshairTarget.getType()== HitResult.Type.BLOCK&& notSuitableForAttack( mc.player.getMainHandStack().getItem()) ){
                //stop if player only want to mine a block
                return ;
            }
            List<Entity> targets= getAttackableEntitiesForPlayer();
            Vec3d pos=player.getEyePos().add(player.getRotationVec(1.0f).multiply(2.5));
            //Debug.info(pos);
            targets.sort(Comparator.comparingDouble( e-> e.getPos().squaredDistanceTo(pos)));
            if(!targets.isEmpty()) {
                //Debug.info("attack!");
                Entity target=targets.get(0);
                attackEntity(player,target);
            }
        }
    }
    public static boolean passCriticalPredicate(PlayerEntity player){
        boolean bl3 = player.getAttackCooldownProgress(0.5f) > 0.9f && player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS) && !player.hasVehicle() ;
        bl3 = bl3 && !player.isSprinting();
        return bl3;
    }
    public static boolean isHoldingWeapon(ClientPlayerEntity player){
        ItemStack itemInHand = player.getStackInHand(Hand.MAIN_HAND);
        return itemInHand != null && !itemInHand.getItem().getAttributeModifiers().modifiers().isEmpty();
    }

    private static final AtomicBoolean DO_INTERVEL_WEAPON = Configs.COMBAT_CONFIG.getBoolean(Configs.AUTOATTACK_DO_INTERVEL_WEAPON);
    private static final AtomicBoolean DO_INTERVEL_HAND = Configs.COMBAT_CONFIG.getBoolean(Configs.AUTOATTACK_DO_INTERVEL_HAND);
    private static final AtomicInteger MAX_ONCE_ATTACK = Configs.COMBAT_CONFIG.getInt(Configs.AUTOATTACK_ONCE_MAX);
    public static void handleAutoAttack(ClientPlayerEntity player){
        boolean holdingWeapon = isHoldingWeapon(player);
        if((holdingWeapon && DO_INTERVEL_WEAPON.get())||(!holdingWeapon&&DO_INTERVEL_HAND.get())){
            if(player.getAttackCooldownProgress(0.5F)>0.98){
                //ready for attack
                //force attack
                autoAttackBest(true);
            }
        }else{
            //attack! attack! attack!
            List<Entity> targets= getAttackableEntitiesForPlayer();
            //Debug.info(pos);
            int max = MAX_ONCE_ATTACK.get();
            if(!targets.isEmpty()) {
                //Debug.info("attack!");
                for (Entity target : targets) {
                    attackEntity(player,target);
                    if( -- max <= 0){
                        return;
                    }
                }
            }
        }
    }

    static {
        updateWhitelist(COMBAT_WHITELISTED.get());
        COMBAT_WHITELISTED.addUpdateListener(CombatTasks::updateWhitelist);
        COMBAT_FRIEND.addUpdateListener(str->COMBAT_FRIEND_PATTERN = Pattern.compile(str).asMatchPredicate());

    }

}
