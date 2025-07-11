package me.matl114.hackUtils;

import com.google.common.util.concurrent.AtomicDouble;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.access.EntityAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.UtilClass.ProgressWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
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
    private static final AtomicBoolean legalMode = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_LEGAL_MOD);
    public static double getAttackRange(){

        double d=attackRange.doubleValue();
        return Math.min(d, mc.player.getEntityInteractionRange() + 1.0d);
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
    private static boolean notSuitableForAttack(ItemStack item){
        return item.isEmpty() ||(!ItemStackUtils.hasInPatch(item, DataComponentTypes.ATTRIBUTE_MODIFIERS)
            && notSuitableForAttack(item.getItem()));
    }
    private static boolean notSuitableForAttack(Item item){
        return ((item instanceof MiningToolItem && !(item instanceof AxeItem))||
            //非重锤 非工具
            (!(item instanceof MaceItem) &&!(item instanceof ToolItem))) ;
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
    private static final Random attackOffsetRand = new Random();

    private static void handlePlayerTickUpdate(Entity player){
        if(player == mc.player){
            handleMCPlayerUpdate();
        }
    }
    private static void handleMCPlayerUpdate(){
        for (var entry: delayedPlayerStateUpdateTasks){
            entry.run();
        }
        delayedPlayerStateUpdateTasks.clear();;
    }
    private static final Deque<Runnable> delayedPlayerStateUpdateTasks = new ArrayDeque<>();

    private static boolean attackEntity(PlayerEntity player,Entity target){
        //already targeted at
        if(mc.crosshairTarget instanceof EntityHitResult entity && entity.getEntity() == target){
           //already actioned in caller
            return false;
        }
        if(legalMode.get()){
            Vec3d vec3d = mc.player.getPos();
            if(false){
                EntityAccess.of(mc.player).addTickWrapper(
                    new ProgressWrapper<ClientPlayerEntity>() {
                        float pitch ;
                        float yaw;
                        @Override
                        public void preProgress(ClientPlayerEntity args) {
                            //step back our position
                            pitch = args.getPitch();
                            yaw = args.getYaw();
                            Vec3d eyePos = target.getEyePos();
                            Vec3d targetPos = target.getPos();
                            double percentage = attackOffsetRand.nextDouble(0.75d, 0.95d);
                            Vec3d attackOffsetted = targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
                            attackOffsetted.add(
                                attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                attackOffsetRand.nextDouble(-0.05d, 0.05d)
                            );
                            Vec3d cacheDirection = attackOffsetted.subtract(args.getEyePos()).normalize();


                            EntityUtils.setEntityRotationSafe(args, cacheDirection);
                            double rotatePercentage= attackOffsetRand.nextDouble(0.4, 0.9);
                            double deltaYaw = MathHelper.clamp((args.getYaw() - yaw + 540.0f ) % 360 - 180, -30, 30);
                            args.setPitch(pitch);
                            args.setYaw((float) (yaw + rotatePercentage * deltaYaw));
                        }

                        @Override
                        public void postProgress(ClientPlayerEntity args) {
//                        args.setPitch(pitch);
//                        args.setYaw(yaw);
                            float pitch = args.getPitch();
                            float yaw = args.getYaw();

                            AntiGrimTasks.addPostTransactionAction((ch)->{
                                mc.gameRenderer.updateCrosshairTarget(1.0f);
                                if(mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY){
                                    mc.interactionManager.attackEntity(args, ((EntityHitResult)mc.crosshairTarget).getEntity());

                                }
                                args.swingHand(Hand.MAIN_HAND);
                                //blocking shield
                                handleShieldPredict(pitch, yaw  );
                            });

//                            EntityUtils.setEntityYawSafe(args, this.yaw);
//                            args.setPitch(this.pitch);


//                        if(tick == 2){
//                            args.setPitch(pitch);
//                            args.setYaw(yaw);
//                        }

                        }

                        @Override
                        public boolean stillWrap(ClientPlayerEntity args) {
                            return false;
                        }
                    }
                    );
                return true;
            }
            if(true){
                //提前转向 下个tick就有正确的velocity了
                EntityAccess.of(mc.player).addTickWrapper(
                    new ProgressWrapper<ClientPlayerEntity>() {
                        float pitch ;
                        float yaw;
                        @Override
                        public void preProgress(ClientPlayerEntity args) {
                            //step back our position
                            pitch = args.getPitch();
                            yaw = args.getYaw();
                            Vec3d eyePos = target.getEyePos();
                            Vec3d targetPos = target.getPos();
                            double percentage = attackOffsetRand.nextDouble(0.75d, 0.95d);
                            Vec3d attackOffsetted = targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
                            attackOffsetted.add(
                                attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                attackOffsetRand.nextDouble(-0.05d, 0.05d)
                            );
                            Vec3d cacheDirection = attackOffsetted.subtract(args.getEyePos()).normalize();


                            EntityUtils.setEntityRotationSafe(args, cacheDirection);
                        }

                        @Override
                        public void postProgress(ClientPlayerEntity args) {
//                        args.setPitch(pitch);
//                        args.setYaw(yaw);
                            float pitch = args.getPitch();
                            float yaw = args.getYaw();

                            AntiGrimTasks.addPostTransactionAction((ch)->{
                                mc.interactionManager.attackEntity(args,target);
                                args.swingHand(Hand.MAIN_HAND);
                                //blocking shield
                                handleShieldPredict(pitch, yaw  );
                            });
                            EntityUtils.setEntityYawSafe(args, this.yaw);
                            args.setPitch(this.pitch);


//                        if(tick == 2){
//                            args.setPitch(pitch);
//                            args.setYaw(yaw);
//                        }

                        }

                        @Override
                        public boolean stillWrap(ClientPlayerEntity args) {
                            return false;
                        }
                    }
                );
                //can not try, they control the packets movement
              //  mc.world.tickEntity(mc.player);
                return true;
            }
            if (true){

                //提前转向 下个tick就有正确的velocity了
                float pitch = mc.player.getPitch();
                float yaw = mc.player.getYaw();
                delayedPlayerStateUpdateTasks.addLast(()->{
                    Vec3d eyePos = target.getEyePos();
                    Vec3d targetPos = target.getPos();
                    double percentage = attackOffsetRand.nextDouble(0.75d, 0.95d);
                    Vec3d attackOffsetted = targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
                    attackOffsetted.add(
                        attackOffsetRand.nextDouble(-0.05d, 0.05d),
                        attackOffsetRand.nextDouble(-0.05d, 0.05d),
                        attackOffsetRand.nextDouble(-0.05d, 0.05d)
                    );
                    Debug.info(attackOffsetted.subtract(mc.player.getEyePos()).length());
                    Vec3d cacheDirection = attackOffsetted.subtract(mc.player.getEyePos()).normalize();
//                            if(tick == 0) {
//                                args.setPosition(vec3d);
//                                pitch = args.getPitch(1.0f);
//                                yaw = args.getYaw(1.0f);
//                            }

                    Debug.info("send ", pitch, yaw);
                    EntityUtils.setEntityRotation(mc.player, cacheDirection);
                });
                ClientPlayerAccess.of(mc.player).addMovementPacketWrapper(
                    new ProgressWrapper<ClientPlayerEntity>() {
                        @Override
                        public void preProgress(ClientPlayerEntity args) {
                            //step back our position

                        }

                        @Override
                        public void postProgress(ClientPlayerEntity args) {
//                        args.setPitch(pitch);
//                        args.setYaw(yaw);

                                mc.interactionManager.attackEntity(args,target);
                                args.swingHand(Hand.MAIN_HAND);
//                            args.setYaw(yaw);
//                            args.setPitch(pitch);
                                args.setYaw(yaw);
                                args.setPitch(pitch);

//
//                        if(tick == 2){
//                            args.setPitch(pitch);
//                            args.setYaw(yaw);
//                        }

                        }

                        @Override
                        public boolean stillWrap(ClientPlayerEntity args) {
                            return false;
                        }
                    }
                );
                return true;
            }
            ClientPlayerAccess.of(mc.player).addMovementPacketWrapper(
                new ProgressWrapper<ClientPlayerEntity>() {
                    float pitch;
                    float yaw;
                    Vec3d cacheDirection;
                    int tick =0;
                    @Override
                    public void preProgress(ClientPlayerEntity args) {
                        //step back our position


                        Vec3d eyePos = target.getEyePos();
                        Vec3d targetPos = target.getPos();
                        double percentage = attackOffsetRand.nextDouble(0.75d, 0.95d);
                        Vec3d attackOffsetted = targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
                        attackOffsetted.add(
                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                            attackOffsetRand.nextDouble(-0.05d, 0.05d)
                        );
                        Debug.info(attackOffsetted.subtract(args.getEyePos()).length());
                        cacheDirection = attackOffsetted.subtract(args.getEyePos()).normalize();
                        if(tick == 0) {
                            args.setPosition(vec3d);
                            pitch = args.getPitch(1.0f);
                            yaw = args.getYaw(1.0f);
                        }
                        Debug.info("send ", pitch, yaw);
                        EntityUtils.setEntityRotation(args, cacheDirection);

                        tick += 1;

                    }

                    @Override
                    public void postProgress(ClientPlayerEntity args) {
//                        args.setPitch(pitch);
//                        args.setYaw(yaw);
                        if(tick == 1){
                            mc.interactionManager.attackEntity(args,target);
                            args.swingHand(Hand.MAIN_HAND);
//                            args.setYaw(yaw);
//                            args.setPitch(pitch);
                            args.setYaw(yaw);
                            args.setPitch(pitch);
                        }
//
//                        if(tick == 2){
//                            args.setPitch(pitch);
//                            args.setYaw(yaw);
//                        }

                    }

                    @Override
                    public boolean stillWrap(ClientPlayerEntity args) {
                        return false;
                    }
                }
            );
            return true;
        }else {
            mc.interactionManager.attackEntity(player,target);
            mc.player.swingHand(Hand.MAIN_HAND);
            handleShieldPredict(mc.player.getPitch(), mc.player.getYaw());
            //next, can continue
            return false;
        }
    }
    //todo add friend name matcher/{

    //todo add Auto crystal
    //return whether the attack will execute delay
    public static boolean autoAttackBest(boolean force){
        PlayerEntity player=mc.player;
        if(player!=null&&mc.world!=null){
            if(mc.crosshairTarget.getType() == HitResult.Type.ENTITY){
                //focusing entity， attack
                //should respect whitelist
                if(isAttackable(((EntityHitResult)mc.crosshairTarget).getEntity())){
                    mc.interactionManager.attackEntity(mc.player, ((EntityHitResult)mc.crosshairTarget).getEntity());
                    mc.player.swingHand(Hand.MAIN_HAND);
                    handleShieldPredict(mc.player.getPitch(), mc.player.getYaw());
                }
                return false;
            }
            if(!force && mc.crosshairTarget.getType()== HitResult.Type.BLOCK&& notSuitableForAttack( mc.player.getMainHandStack()) ){
                //stop if player only want to mine a block
                return false;
            }
            List<Entity> targets= getAttackableEntitiesForPlayer();
            //Debug.info(pos);
            targets.sort(Comparator.comparingDouble( e-> e.getPos().squaredDistanceTo(player.getEyePos())));
            if(!targets.isEmpty()) {
                //Debug.info("attack!");
                Entity target=targets.get(0);
                return  attackEntity(player,target);
            }
        }
        return false;
    }
    public static boolean passCriticalPredicate(PlayerEntity player){
        boolean bl3 = player.getAttackCooldownProgress(0.5f) > 0.9f && player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS) && !player.hasVehicle() ;
        bl3 = bl3 && !player.isSprinting();
        return bl3;
    }
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




    private static final AtomicBoolean DO_INTERVEL_WEAPON = Configs.COMBAT_CONFIG.getBoolean(Configs.AUTOATTACK_DO_INTERVEL_WEAPON);
    private static final AtomicBoolean DO_INTERVEL_HAND = Configs.COMBAT_CONFIG.getBoolean(Configs.AUTOATTACK_DO_INTERVEL_HAND);
    private static final AtomicInteger MAX_ONCE_ATTACK = Configs.COMBAT_CONFIG.getInt(Configs.AUTOATTACK_ONCE_MAX);
    private static int internalInterval = 0;
    public static void handleAutoAttack(ClientPlayerEntity player){
        boolean holdingWeapon = isHoldingWeapon(player);
        //force consider attack interval legal mode
        if(legalMode.get()){
            if(++internalInterval <= 2){
                return;
            }
        }
        internalInterval = 0;
        if((holdingWeapon && DO_INTERVEL_WEAPON.get())||(!holdingWeapon&&DO_INTERVEL_HAND.get())){
            //do not attack because of legal mode

            if(player.getAttackCooldownProgress(0.5F)>0.98){
                //ready for attack
                //force attack
                autoAttackBest(true);
            }
        }else{
            //attack! attack! attack!
            List<Entity> targets= getAttackableEntitiesForPlayer();
            int max = MAX_ONCE_ATTACK.get();
            if(!targets.isEmpty()) {
                for (Entity target : targets) {
                    attackEntity(player,target);
                    if( -- max <= 0){
                        return;
                    }
                }
            }
        }
    }
    private static final AtomicBoolean autoshield = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_AUTOSHIELD);
    public static boolean handleShieldCooldownFastWrite(CooldownUpdateS2CPacket packet){
        if(packet.item() instanceof ShieldItem shield && packet.cooldown() > 0){
            try{
                synchronized (CombatTasks.class){
                    //async update, synchronize to protect concurrent cooldown update,
                    mc.player.getItemCooldownManager().set(shield, packet.cooldown());
//                if(mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == shield){
//
//                }
                    //consume packet
                    return false;
                }

            }catch (Throwable e){
                //any exception
                return true;
            }
        }
        return true;
    }
    public static void handleShieldPredict(float pitch, float yaw){
        if(autoshield.get() && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof ShieldItem shield && !mc.player.getItemCooldownManager().isCoolingDown(shield)){
            mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                return new PlayerInteractItemC2SPacket(mc.player.getActiveHand(), sequence, yaw, pitch);
            });
        }
    }
    private static int shieldExceptionspam = 0;
    public static boolean handleAutoShield(EntityTrackerUpdateS2CPacket trackerUpdateS2CPacket){
        if(autoshield.get() && mc.player != null && trackerUpdateS2CPacket.id() == mc.player.getId() && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof ShieldItem shieldItem && !mc.player.getItemCooldownManager().isCoolingDown(shieldItem)){
            //shield not in cooldown
            //block shield from
            for (var trackerUpdate : trackerUpdateS2CPacket.trackedValues()){
                //fixme the ordinal  of LIVING FLAGS in LivingEntity, may vary with versionsl pls check
                if(trackerUpdate.id() == 8 ){
                    byte byteValue =(byte) trackerUpdate.value();
                    boolean bl = ((Byte)byteValue & 1) > 0;
                    Hand hand = ((Byte)byteValue & 2) > 0 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                    //cooldown should be ok,
                    //the only position the server disable shield correctly should be cooldown
                    //so we kick it back
                    if(!bl && hand == mc.player.getActiveHand()){
                        //using shield , but banned
                        if(shieldExceptionspam + 4 < Tasks.getTick()){
                            shieldExceptionspam = Tasks.getTick();
                            Debug.chat(Text.literal("[anti-grim] 阻挡异常盾牌禁用").formatted(Formatting.RED));
                        }
                        mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                            return new PlayerInteractItemC2SPacket(hand, sequence, mc.player.getYaw(), mc.player.getPitch());
                        });
                        return false;
                    }
                }
            }

        }
        return true;
    }


    static {
        updateWhitelist(COMBAT_WHITELISTED.get());
        COMBAT_WHITELISTED.addUpdateListener(CombatTasks::updateWhitelist);
        COMBAT_FRIEND.addUpdateListener(str->COMBAT_FRIEND_PATTERN = Pattern.compile(str).asMatchPredicate());
        EntityTasks.getEntityTickListener().registerHandler(CombatTasks::handlePlayerTickUpdate);
        Listener.registerSinglePacketListener(EntityTrackerUpdateS2CPacket.class, CombatTasks::handleAutoShield);
        Listener.registerSinglePacketListener(CooldownUpdateS2CPacket.class, CombatTasks::handleShieldCooldownFastWrite);
    }

}
