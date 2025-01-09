package me.matl114.HackUtils;

import com.google.common.util.concurrent.AtomicDouble;
import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.Configs;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.Utils.EntityUtils;
import me.matl114.Utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CombatTasks {
    public static void init(){

    }
    private static final MinecraftClient mc=MinecraftClient.getInstance();
    private static final Config.StringRef COMBAT_WHITELISTED= Configs.COMBAT_CONFIG.getString(Configs.AUTOATTACK_WHITELISTED);
    private static final HashSet<EntityType<?>> WHITELISTED_ENTITIES = new HashSet<>();
    private static final AtomicBoolean attackHostile=new AtomicBoolean(true);
    private static final AtomicDouble attackRange=Configs.COMBAT_CONFIG.getDouble(Configs.ATTACK_RANGE);

    public static double getAttackRange(){
        double d=attackRange.doubleValue();
        return d<=3?mc.interactionManager.getReachDistance() : d;
    }
    private static void updateWhitelist(String value){
        EntityUtils.parseEntityWhiteList(value,WHITELISTED_ENTITIES);
        if(Pattern.matches(value,"hostile")){
            attackHostile.set(true);
        }else {
            attackHostile.set(false);
        }
    }
    private static boolean isAttackable(Entity e){
        return e!=null&&e!=mc.player&&(e instanceof LivingEntity && ((LivingEntity)e).getHealth() > 0
                || e instanceof EndCrystalEntity
                || e instanceof ShulkerBulletEntity)&&WHITELISTED_ENTITIES.contains(e.getType());
    }
    private static boolean isRangeAttackable(Entity e){
        return mc.player.squaredDistanceTo(e)< MathUtils.s2(getAttackRange());
    }
    private static boolean notSuitableForAttack(Item item){
        return item instanceof MiningToolItem && !(item instanceof AxeItem);
    }
    private static boolean isAttackablePlayerOrElse(Entity e){
        if(e instanceof PlayerEntity player){
            return true;
        }else {
            return true;
        }
    }
    //todo add friend name matcher/
    //todo add Auto crystal
    public static void autoAttackBest(){
        PlayerEntity player=mc.player;
        if(player!=null&&mc.world!=null){
            if(mc.crosshairTarget.getType()== HitResult.Type.BLOCK&& notSuitableForAttack( mc.player.getMainHandStack().getItem()) ){
                //stop if player only want to mine a block
                return ;
            }
            List<Entity> targets= StreamSupport.stream( mc.world.getEntities().spliterator(),true).filter(CombatTasks::isAttackable).filter(CombatTasks::isRangeAttackable).filter(CombatTasks::isAttackablePlayerOrElse)
                    .collect(Collectors.toCollection(ArrayList::new));
            Vec3d pos=player.getEyePos().add(player.getRotationVec(1.0f).multiply(3.0));
            Debug.info(pos);
            targets.sort(Comparator.comparingDouble( e-> e.getPos().squaredDistanceTo(pos)));
            if(!targets.isEmpty()) {
                Entity target=targets.get(0);
                mc.interactionManager.attackEntity(player,target);
            }
        }
    }
    static {
        updateWhitelist(COMBAT_WHITELISTED.get());
        COMBAT_WHITELISTED.addUpdateListener(CombatTasks::updateWhitelist);
    }

}
