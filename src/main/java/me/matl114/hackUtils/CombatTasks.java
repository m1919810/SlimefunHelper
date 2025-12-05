package me.matl114.hackUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;

import me.matl114.access.ClientPlayerAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.renders.RenderMain;
import me.matl114.utils.*;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.LegalMovementManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final Config.FlagRef attackHostile=new Config.FlagRef(true);
    private static final Config.DoubleRef attackRange=Configs.COMBAT_CONFIG.getDouble(Configs.ATTACK_RANGE);
    private static final Config.StringRef COMBAT_FRIEND = Configs.COMBAT_CONFIG.getString(Configs.ATTACK_PLAYER_FRIENDLIST);
    private static Predicate<String> COMBAT_FRIEND_PATTERN = null;
    private static final Config.FlagRef attackNamed=Configs.COMBAT_CONFIG.getBoolean(Configs.ATTACK_NAMED);
    private static final Config.FlagRef attackTeammate = Configs.COMBAT_CONFIG.getBoolean(Configs.ATTACK_TEAMMATE);
    private static final Config.FlagRef legalMode = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_LEGAL_MOD);
    private static final Config.FlagRef exactAttack = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_EXACT_ATTACK);
    private static final Config.FlagRef exactAntiShield = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_EXACT_ATTACK_SHIELD);
    private static final Config.DoubleRef tpAttackRange = Configs.COMBAT_CONFIG.getDouble(Configs.COMBAT_TP_REACH);
    private static final Config.DoubleRef maceHack = Configs.COMBAT_CONFIG.getDouble(Configs.COMBAT_MACE_HACK);
    private static final Config.DoubleRef oppoTargetMultiply = Configs.COMBAT_CONFIG.getDouble(Configs.COMBAT_OPPOSITE_ATTACK_MULTIPLY);
    private static final Config.DoubleRef playerTargetMultiply = Configs.COMBAT_CONFIG.getDouble(Configs.COMBAT_PLAYER_ATTACK_MULTIPLY);
    private static final Config.EnumRef<Configs.LegalTargetingMode> attackBypassMode = Configs.COMBAT_CONFIG.getEnum(Configs.COMBAT_LEGAL_TARGETTING);
    private static final Config.FlagRef critic = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_CRITIC);
    private static final Config.FlagRef renderAttackEntity = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_RENDER_TARGET);

    public static double getAttackRange(){

        double d=attackRange.get();
        return mc.player.getEntityInteractionRange() + d;
    }
    private static void updateWhitelist(String value){
        EntityUtils.parseEntityWhiteList(value,WHITELISTED_ENTITIES);
        if(Pattern.matches(value,"hostile")){
            attackHostile.set(true);
        }else {
            attackHostile.set(false);
        }
    }
    private static double withMultiply(Entity e, double v){
        return Math.abs(v) - ((v < 0.0)? oppoTargetMultiply.get(): 0.0D) + (e instanceof PlayerEntity ? playerTargetMultiply.get() : 0.0D);
    }
    private static boolean passWhitelistCheck(Entity e){
        return WHITELISTED_ENTITIES.contains(e.getType()) || (attackHostile.get() &&
                    (
                            e instanceof Angerable angerable && mc.player.getUuid().equals(angerable.getAngryAt())
                    )
                );
    }
    private static boolean passExtraCheck(Entity e){
        if(e instanceof PlayerEntity player){
            String name = player.getGameProfile().getName();
            if(COMBAT_FRIEND_PATTERN != null){
                boolean match = COMBAT_FRIEND_PATTERN.test(name);
                if(match){
                    return false;
                }
//                boolean noMatch = COMBAT_FRIEND_PATTERN.test("!"+name);
//                if(noMatch){
//                    return true;
//                }
            }
        }else{
            if(!attackNamed.get() && e.hasCustomName()){
                return false;
            }
            if(COMBAT_FRIEND_PATTERN != null){
                if(e.hasCustomName()){
                    String value = ChatUtils.textToString( e.getCustomName());
                    if(COMBAT_FRIEND_PATTERN.test(value)){
                        return false;
                    }
                }
            }

        }
        return true;
    }
    private static boolean isAttackable(Entity e){
        return e!=null&&e!=mc.player && (!(e instanceof LivingEntity) || ((LivingEntity) e).getHealth() > 0) && passWhitelistCheck(e) && passExtraCheck(e);
    }
    private static double getFinalRange(){
        return getAttackRange() + Math.max(0.0d, tpAttackRange.get());
    }
    private static boolean isRangeAttackable(Entity e){
        double attackRange = getFinalRange();
        return e.getBoundingBox().squaredMagnitude(mc.player.getEyePos())< MathUtils.s2(attackRange);
    }
    private static boolean notSuitableForAttack(ItemStack item){
        return item.isEmpty() || (!ItemStackUtils.hasInPatch(item, DataComponentTypes.ATTRIBUTE_MODIFIERS)
            && notSuitableForAttack(item.getItem()));
    }
    private static boolean notSuitableForAttack(Item item){
        return ((item instanceof MiningToolItem && !(item instanceof AxeItem))||
            //非重锤 非工具
            (!(item instanceof MaceItem) &&!(item instanceof ToolItem))) ;
    }
    private static boolean isAttackablePlayerOrElse(Entity e){
        if(e instanceof PlayerEntity playerEntity ){
            //todo check team， should we attack teammate
            if(playerEntity.getScoreboardTeam() != null && playerEntity.getScoreboardTeam() == mc.player.getScoreboardTeam()){
                Team team = playerEntity.getScoreboardTeam();
                //过滤友伤
                if(!team.isFriendlyFireAllowed()){
                    return false;
                }
            }

            return true;
        }else {
            return true;
        }
    }
    private static boolean considerAntiShield(Entity target){
        return exactAntiShield.get() && target instanceof LivingEntity livingEntity && livingEntity.isUsingItem() && livingEntity.getActiveItem().getItem() instanceof ShieldItem;
    }
    public static List<Entity> getAttackableEntitiesForPlayer(){
        return StreamSupport.stream( mc.world.getEntities().spliterator(),true).filter(CombatTasks::isAttackable).filter(CombatTasks::isRangeAttackable).filter(CombatTasks::isAttackablePlayerOrElse)
                .collect(Collectors.toCollection(ArrayList::new));
    }
    public static boolean canPlayerDirectlySee(Entity entity){
        //横向距离小于300
        return entity.getPos().subtract(mc.player.getPos()).horizontalLengthSquared() < 90000 && !RaycastUtils.raycastAnyBlock(mc.player, mc.player.getEyePos(), entity.getEyePos());
    }
    public static List<Entity> getBowAimableEntitiesForPlayer(){
        return StreamSupport.stream( mc.world.getEntities().spliterator(),true).filter(CombatTasks::isAttackable)
            .filter(CombatTasks::isAttackablePlayerOrElse)
            //filter shits that can not be shoot
            .filter(e -> !(e instanceof EndermanEntity) && !(e instanceof ShulkerEntity))
            .filter(CombatTasks::canPlayerDirectlySee)
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

    private static Vec3d getExactAttackPosition(PlayerEntity player, Entity target){
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

                if(!MovTasks.ENGIN.checkEnvironmentCollision(player, testPos)){
                    return testPos;
                }
            }
            return null;
        }else {
            //fixme use player facing when considerShield
            boolean considerAntiShield = considerAntiShield(target);
            Vec3d deltaMovments;
            if(considerAntiShield){
                deltaMovments = target.getRotationVector().normalize().multiply(-0.2);
            }else{
                Vec3d targetFacing = player.getPos().subtract(target.getPos());
                Vec3d targetFacingHorizontal = new Vec3d(targetFacing.x, 0.0d, targetFacing.z);
                double multiply =  0.5;
                deltaMovments = targetFacingHorizontal.normalize().multiply(multiply);
            }

            Vec3d targetPos = target.getPos();
            Vec3d actualMove =  MovTasks.ENGIN.simulateMovement(player, targetPos, deltaMovments);
            return targetPos.add(actualMove);
        }

    }
    private static void attackWithCritic(PlayerEntity player, Entity target, boolean criticSprint){
        if(criticSprint){
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        handleShieldPredict(mc.player.getPitch(), mc.player.getYaw());
        if(criticSprint){
            ClientPlayerAccess.of(mc.player).resyncSprint();
        }
    }

    private static boolean attackEntity(PlayerEntity player,Entity target){
        //already targeted at
       // Debug.chat("Execute on entity", target);
        final boolean criticSprint = critic.get() && player.isSprinting();
        if(legalMode.get()){
            Vec3d vec3d = mc.player.getPos();
            //do not add mace or tp attack in legal mode

            if(mc.crosshairTarget instanceof EntityHitResult entity && entity.getEntity() == target){
                //already actioned in caller
                //may not actioned in caller, fix it
                attackWithCritic(player, target, criticSprint);
                return false;
            }
            else if(true){
                //提前转向 下个tick就有正确的velocity了
                //mace not enable in legal mode
                //use Item packet should trigger by a non-empty item
                //todo: targeting need recal,
                //todo: add movement prediction position targeting option
                //todo: check if it can pass grimac in real situation
                boolean useTp = (tpAttackRange.get() > 1E-7 && target.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) > MathUtils.s2(getAttackRange()));
                boolean delayTurningAround = attackBypassMode.getValue() == Configs.LegalTargetingMode.DELAY_MOVEMENT || useTp ;
                if(!delayTurningAround && attackBypassMode.getValue() == Configs.LegalTargetingMode.USEITEM_PACKET){
                    //use item
                    //only do the targeting and attack
                    Hand hand ;
                    if (!mc.player.getMainHandStack().isEmpty()){
                        hand = Hand.MAIN_HAND;
                    }else if(!mc.player.getOffHandStack().isEmpty()){
                        hand = Hand.OFF_HAND;
                    }else {
                        hand = null;
                    }
                    if(hand != null){
                        Vec3d eyePos = target.getEyePos();
                        Vec3d targetPos = target.getPos();
                        double percentage = attackOffsetRand.nextDouble(0.75d, 0.95d);
                        Vec3d attackOffsetted = targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
                        attackOffsetted.add(
                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                            attackOffsetRand.nextDouble(-0.05d, 0.05d)
                        );
                        Vec3d cacheDirection = attackOffsetted.subtract(mc.player.getEyePos()).normalize();
                        Vec2f toDirection = EntityUtils.rotationToPitchYaw(cacheDirection);
                        mc.interactionManager.sendSequencedPacket(
                            mc.world, (i)->{
                                return new PlayerInteractItemC2SPacket(hand, i, EntityUtils.getSafeYaw(mc.player, toDirection.y), toDirection.x);
                            }
                        );
//
                        attackWithCritic(player, target, criticSprint);
                        //EntityUtils.setEntityRotationSafe(args, cacheDirection);
                        return false;
                    }
                    //fallback to delay Turn
                    delayTurningAround = true;
                }
                boolean useDelay = delayTurningAround ;
                if(useDelay){
                    //TODO: fix this bug: can not pass matrix ac when on ground , check numbers and positions,
                    ClientPlayerAccess.of(mc.player).getLegalMovementManager().addMovementModifier(new LegalMovementManager.MovementModifier() {
                        Vec3d posDelta = Vec3d.ZERO;
                        Vec3d posDelta2 = Vec3d.ZERO;
                        Vec3d velocity ;
                        boolean distancePassAttack = true;
                        @Override
                        public int priority() {
                            return -10000000;
                        }

                        @Override
                        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                            //todo: set sprint false if critic
                            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                            //step back our position
                            velocity = args.getVelocity();

                            Vec3d vec3d = args.getPos();

                            if(tpAttackRange.get()> 1E-7 && target.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) > MathUtils.s2(getAttackRange())){
                                //need tp attack
                                //how?
                                //平面突袭？
                                if(exactAttack.get()){
                                    //disable
                                    Debug.chat("[Attack Bot] Exact Attack选项在Legal Mode中无效!");
                                }


                                Vec3d vec3d1 = MovTasks.tpAttackSearch(vec3d, target.getBoundingBox(), getAttackRange(), 9.9, 1).stream().findFirst().orElse(null);
                                //calculateBestReachPos(vec3d, target.getBoundingBox());
                                if(vec3d1 != null && vec3d1.squaredDistanceTo(vec3d) > 1E-7){
                                    posDelta = vec3d;//vec3d1.subtract(vec3d);
                                    posDelta2 = vec3d1;
                                    args.setPosition(vec3d1.add(0, 9E-8, 0));

                                }
                                //backoff
                                if(target.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) > MathUtils.s2(getAttackRange())){
                                    //Debug.chat("Distance to large , disable atack");
                                    distancePassAttack = false;
                                    movementManagerEvent.context.playerStatus.restoreRotation();
                                    args.setPosition(vec3d);
                                    //skip attack
                                }

                            }
                            //after move player, do target
                            if(distancePassAttack){
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
                                if(maceHack.get() > 0.0D && mc.player.getActiveItem().getItem() instanceof MaceItem){
                                    Debug.chat(Text.literal("[Attack Bot] Mace Attack Simulation：Simulation not supported in legal mode !"));
                                }
                            }

                            //restore velocity after collide
                            args.setVelocity(velocity);

                        }

                        @Override
                        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                            if(!enabledThisTick){
                                //rare,,, maybe
                                Debug.chat("Attack Task conflict with other movement tasks !!!");
                                return false;
                            }
                            //fixme: check at matrix
                            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                            if(distancePassAttack){
                                ACPostTasks.addPostTransactionAction((ch)->{

                                    attackWithCritic(player, target, criticSprint);
                                });
                                movementManagerEvent.context.playerStatus.restoreRotation();
                                if(posDelta != Vec3d.ZERO){
                                    Vec3d trueDelta = args.getPos().subtract(posDelta2);//.subtract(0, 0.2, 0);// = args.getPos().subtract(posDelta);
                                    //Debug.chat("true move", RenderTasks.getDisplayedLocationDouble(trueDelta));
                                    //args.setPosition(posDelta);
                                    args.setPosition(posDelta);
                                    // args.move(MovementType.PLAYER, posDelta.subtract(args.getPos()));
                                    args.move(MovementType.PLAYER, trueDelta);
                                    //Debug.chat("final pos", RenderTasks.getDisplayedLocationDouble(args.getPos()));
                                    //Debug.chat("TpReach back", RenderTasks.getDisplayedLocationDouble(args.getPos()));
                                    posDelta = posDelta2 = Vec3d.ZERO;
                                }
                            }else{
                                ACPostTasks.addPostTransactionAction((ch)->{
                                    args.swingHand(Hand.MAIN_HAND);
                                });
                            }
                            //return do not kept
                            return false;
                        }
                    });

                    //can not try, they control the packets movement
                    //  mc.world.tickEntity(mc.player);
                    return true;
                }
                return false;

            }else {
                return false;

            }
        }else {

            boolean alreadyAtTarget = mc.crosshairTarget instanceof EntityHitResult entity && entity.getEntity() == target;
            //rewrite tp system
            Deque<MovTasks.MovInfo> movementStack = new ArrayDeque<>();
            Deque<MovTasks.MovInfo> shouldMoveBackStack = new ArrayDeque<>();
            movementStack.addLast(MovTasks.MovInfo.createNoUpdate( mc.player.getPos()));
            shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNoUpdate( mc.player.getPos()));
            boolean alreadyInRange = alreadyAtTarget || target.getBoundingBox().squaredMagnitude(player.getEyePos()) < MathUtils.s2(getAttackRange());
            //mace hack、
            boolean useExactAttack = exactAttack.get() && (!alreadyInRange || considerAntiShield(target));
            boolean shouldResetFallDamage = false;
            boolean currentSuccessful = true;
            boolean vanillaSuccessful = false;
            boolean exactSuccessful = false;
            if(currentSuccessful){
                vanillaSuccessful = processVanillaAttack(player, target, movementStack, shouldMoveBackStack, alreadyAtTarget);
            }
            if(currentSuccessful){
                //exact attack
                if(useExactAttack ){
                   if(processExactAttack(player, target, movementStack, shouldMoveBackStack, vanillaSuccessful)){
                        exactSuccessful = true;
                    }
                }
            }
            if(currentSuccessful){
                if(!exactSuccessful && !vanillaSuccessful){
                    currentSuccessful &= processCommonTpAttack(player, target, movementStack, shouldMoveBackStack, alreadyAtTarget);
                }
            }
            if(currentSuccessful){
                if(processMaceAttack(player, target, movementStack, shouldMoveBackStack)){
                    int maceThreshold = (useExactAttack? 100: 140);
                    if(maceHack.get() > maceThreshold){
                        Debug.chat(Text.literal("[Attack Bot] 当前参数中,不建议将MaceHack范围设置在%d以上!".formatted(maceThreshold)));
                    }
                }
            }


            //attacking creative player with mace at same height will cause falldamage calculate(caused by the shit code below: we should resetHeight even if backStack.size() = 1
            Vec3d lastlyPos = movementStack.peekLast().vec3d();
            //final pos lies in attack range
            if(currentSuccessful && (alreadyAtTarget || (target.getBoundingBox().squaredMagnitude(lastlyPos.add(0, mc.player.getStandingEyeHeight(), 0)) < MathUtils.s2(getAttackRange())))){
                //start execute
                var iter = movementStack.iterator();
                Preconditions.checkArgument(iter.hasNext());
                Vec3d vec3d1 = iter.next().vec3d();
                MovTasks.MovingContext movingContext = MovTasks.MovingContext.create(vec3d1);
                List<MovTasks.MovInfo> moveInfos = new ArrayList<>();
                iter.forEachRemaining(moveInfos::add);
                MovTasks.scheduleFarawayMoveInternal(moveInfos, false, movingContext, false);
                //attack
                attackWithCritic(player, target, criticSprint);

                //already at first, remove duplicate stack
                shouldMoveBackStack.removeFirst();
//                Debug.info(movementStack);
//                Debug.info(shouldMoveBackStack);
                var inviter = shouldMoveBackStack.stream().toList();
                MovTasks.scheduleFarawayMoveInternal(inviter, true, movingContext,
                    //calculate nofall down there in this argument, no need to consider
                    false
                );
                //force resync position to origin
                if(!shouldMoveBackStack.isEmpty()){
                    Vec3d finalId = shouldMoveBackStack.peekLast().vec3d();
                    mc.player.setPosition(finalId);
                    //feature
                    MovTasks.setupAutoResync(finalId, 10);
                }
                //check fall damage
                List< MovTasks.MovInfo> movementList = Streams.concat(movementStack.stream(), shouldMoveBackStack.stream()).toList();
//                Debug.info(movementList);
//                Debug.info(movementList.size());
                int size = movementList.size();

                if(size > 1){
                    double maxY = Integer.MIN_VALUE;
                    double minY = Integer.MAX_VALUE;
                    for (var i =0 ; i< size - 1; ++ i){
                        maxY = Math.max(maxY, movementList.get(i).vec3d().y);
                        minY = Math.min(minY, movementList.get(i).vec3d().y);
                    }
                    //calculate max deltaY
                    if(Math.abs(maxY - minY) > player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) - 1){
                        ClientPlayerAccess.of((ClientPlayerEntity) player).setForceNoFall(true);
                        //in case that resync packet cause OnGround falldamage
                        player.setOnGround(false);
                    }
                }

              //  shouldResetFallDamage = size >= 2 && movementList.get(size - 1).vec3d().y < movementList.get(size - 2).vec3d().y;
                    //- mc.player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE);
                // falldistance will sum up if movement is down,

               // if(shouldResetFallDamage){
                    //let noFall functions make fall judgement
//                //do not consume fall damage when resync if any custom tp is applied
//                if(size > 1){
//
//                }
               // }
            }


            //next, can continue
            return false;
        }
    }
    private static boolean processMaceAttack(PlayerEntity player, Entity target, Deque<MovTasks.MovInfo> movementStack, Deque<MovTasks.MovInfo> shouldMoveBackStack){
//        if(maceHack.get() > 0.0D && player.getMainHandStack().getItem() instanceof MaceItem mace){
//            //dupe fall distance
//            double maxMace = maceHack.get();
//            player.setOnGround(false);
//            double deltaY = Math.max(target.getY() - mc.player.getY(),0);
//            //error: down search returns negative value
//            double height = MovTasks.searchFirstNoCollisionSpaceYHeight(mc.player.getPos().add(0, maxMace, 0), 0, maxMace - 2 - deltaY, false);
//
//            double maceHeightMultiplier = maxMace + height;
//            //attack space
//            double minAvailableHeight = MovTasks.searchFirstNoCollisionSpaceYHeight(mc.player.getPos(), deltaY , maxMace, true);
//
//            if(maceHeightMultiplier - minAvailableHeight > 1.5){
//                Debug.chat(Text.literal("Mace Attack Simulation: simulate height %.2f, target height: %.2f".formatted(maceHeightMultiplier, minAvailableHeight)).formatted(Formatting.GREEN));
//                Vec3d top = movementStack.peekLast().vec3d();
//                movementStack.addLast(MovTasks.MovInfo.createNoUpdate( top.add(0, maceHeightMultiplier,0)));
//                movementStack.addLast(MovTasks.MovInfo.createNoUpdate(top.add(0, minAvailableHeight,0)));
//                shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNoUpdate(top.add(0, minAvailableHeight, 0)));
//            }
//
//        }
        if(maceHack.get() > 0.0D && player.getMainHandStack().getItem() instanceof MaceItem mace){
            double maxMace = maceHack.get();
            player.setOnGround(false);
            Vec3d playerPos = movementStack.peekLast().vec3d();
            double deltaY = target.getY() - playerPos.y;
            //error: down search returns negative value
            double height = MovTasks.searchFirstNoCollisionSpaceYHeight(playerPos.add(0, maxMace, 0), 0, maxMace - 2 - deltaY, false);
            //+height
            double maceHeightMultiplier = maxMace + height;
            //attack space
            //we assume that target is in attack range centered playerPos
            // we don't need to search for the 'minAvailableHeight‘
            //the height is 0.0D
            double minAvailableHeight = 0.0D;
                //MovTasks.searchFirstNoCollisionSpaceYHeight(playerPos, deltaY , maxMace, true);
            // moving height towards enermy only cause the distance be smaller
            //  moving height towards enermy causes collision with shulker
            //maybe we should delete height redirect
            //
            if(maceHeightMultiplier - minAvailableHeight > 2.0){
                Debug.chat(Text.literal("[Attack Bot] Mace Attack Simulation: simulate height %.2f".formatted(maceHeightMultiplier)).formatted(Formatting.GREEN));
                movementStack.addLast(MovTasks.MovInfo.createNoUpdate(playerPos.add(0, maceHeightMultiplier,0)));
                //Debug.info("add", playerPos.add(0, maceHeightMultiplier,0));
                movementStack.addLast(MovTasks.MovInfo.createNoUpdate(playerPos.add(0, minAvailableHeight,0)));
                //Debug.info("add", playerPos);
                if(Math.abs(minAvailableHeight) > 1E-7){
                    shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNoUpdate(playerPos.add(0, minAvailableHeight,0)));
                }
                return true;
            }

        }
        return false;
    }
    private static boolean processVanillaAttack(PlayerEntity player, Entity target, Deque<MovTasks.MovInfo> movementStack, Deque<MovTasks.MovInfo> shouldMoveBackStack , boolean alreadAtTarget){
        Vec3d top = movementStack.peekLast().vec3d();
        if(alreadAtTarget){
            return true;
        }else if(target.getBoundingBox().squaredMagnitude(top.add(0, mc.player.getStandingEyeHeight(), 0)) <= MathUtils.s2(getAttackRange())){
            return true;
        }else return false;
    }
    private static boolean processExactAttack(PlayerEntity player, Entity target, Deque<MovTasks.MovInfo> movementStack, Deque<MovTasks.MovInfo> shouldMoveBackStack, boolean vanillaSuccess){
        // how to manage exact attack and mace hack
        //fixed : can not tp to shulker inside
        //should teleport the player to the pos of target entity
        if(vanillaSuccess){
            if(!considerAntiShield(target)){
                return true;
            }
        }
        Vec3d current = player.getPos();
        //feat : teleporting position should met the need of antishield
        Vec3d targetPos = getExactAttackPosition(player, target);

        if(targetPos != null){
            //common atttack?
            if(RenderTasks.DEBUG_RENDER_COMBAT)
                RenderTasks.drawBox(player.dimensions.getBoxAt(targetPos), 150, Color.GREEN);
            List<Vec3d> tpSequence = MovTasks.generateTpSequence(current, targetPos, false, 1.5* getFinalRange(), true);
            List<Vec3d> tpSequenceBack = MovTasks.generateTpSequence(targetPos, current,false,1.5* getFinalRange(), true);
            if((tpSequence.size() == 2 || tpSequence.size() == 4) && (tpSequenceBack.size() == 2 || tpSequenceBack.size() == 4)){
                //correct tp sequence
                //try compact mace hack
                Vec3d lastly;
                if(tpSequence.size() == 2){
                    //can directly tp
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));

                }else {
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(2)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(3)));
                    lastly = tpSequence.get(3);
                }
                //  Debug.info(movementStack);
                int size = tpSequenceBack.size();

                for (int i= size - 2; i >= 0; --i){
                    shouldMoveBackStack.addFirst(MovTasks.MovInfo.create(tpSequenceBack.get(i)));
                }
//                if(maceHack.get() > 80){
//                    Debug.chat(Text.literal("[Attack Bot] 在精确攻击模式下,不建议将MaceHack设置在80以上!"));
//                }

                //shouldMoveBackStack.addFirst(MovTasks.MovInfo.create(tpSequenceBack.get(0).add(0, 9E-8,0)));
                // Debug.info(shouldMoveBackStack);
                return true;
            }else {
                Debug.chat("[Attack Bot] Exact Attack failed, fall back to common mode");
            }
        }
        return vanillaSuccess;
    }

    private static boolean processCommonTpAttack(PlayerEntity player, Entity target, Deque<MovTasks.MovInfo> movementStack, Deque<MovTasks.MovInfo> shouldMoveBackStack, boolean alreadyAtTarget){
        final Vec3d vec3d = movementStack.peekLast().vec3d();
        if(alreadyAtTarget){
            //pass
            return true;
        }
        else if(tpAttackRange.get()> 1E-7 && target.getBoundingBox().squaredMagnitude(vec3d.add(0, mc.player.getStandingEyeHeight(), 0)) > MathUtils.s2(getAttackRange())){

            List<Vec3d> sequence = MovTasks.tpAttackSearch(vec3d, target.getBoundingBox(), getAttackRange() - 0.25, 135, 1);
            if(!sequence.isEmpty() && RenderTasks.DEBUG_RENDER_COLLISION){
                Vec3d vec3d1 = sequence.get(sequence.size() -1);
                RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(vec3d1.add(new Vec3d(-0.5, 0, -0.5)), vec3d1.add(new Vec3d(0.5, 2, 0.5)), 16));
            }

            if(!sequence.isEmpty() && target.getBoundingBox().squaredMagnitude(sequence.get(sequence.size() - 1)) < MathUtils.s2(getAttackRange())){
                for (var vec : sequence){
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(vec));

                    shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNotOnGround(vec));
                }
//                if(tpAttackRange.get() >= 135){
//                    Debug.chat(Text.literal("[Attack Bot] 不建议将tpAttack范围设置在135以上!"));
//                }
                return true;
            }
            return false;
        }else{
            return false;
        }
    }



    @Deprecated
    private static Vec3d calculateBestReachPos(Vec3d from, Box target){
//        double lenExtra1 = Math.min( Math.sqrt( target.squaredMagnitude(mc.player.getEyePos())) -  getAttackRange() + 0.5d, tpAttackRange.get());
//        //already deprecated:  select a better location for player to move to, closer and without collide at the end . like we search about position y + 0.5 position y - 0.5 or something. should be suitable for placing a player here. should detect whether blocks are down or up. if down has block, try align to blockface. we may use this: try player.move(player.getPos() - y:1), check final position.
//        Vec3d pos2Eye = mc.player.getEyePos().subtract(from);
//        Vec3d currentCenter = mc.player.getBoundingBox().getCenter();
//        Vec3d targetCenter = target.getCenter();
//        Vec3d towards = targetCenter.subtract(currentCenter).normalize();
//        Vec3d vec3d1 = MovTasks.simulatePlayerMoveTowards(from, towards, lenExtra1);
//        boolean saveTo = MovTasks.validMovementAsServer(vec3d1,)
//        Vec3d expectedBack = from.subtract(vec3d1);
//        Vec3d backSimulation = MovTasks.simulatePlayerMoveTowards(vec3d1, expectedBack);
//        boolean safeBack = MovTasks.validMovementAsServer(backSimulation, expectedBack);
//        if(safeBack && target.squaredMagnitude(vec3d1.add(pos2Eye)) < MathUtils.s2(getAttackRange())){
//            return vec3d1;
//        }
//        else{
//            Vec3d towardsUp = targetCenter.add(0,1,0).subtract(currentCenter).normalize();
//            Vec3d towardsDown = targetCenter.add(0,-1,0).subtract(currentCenter).normalize();
//            double estimateLength = Math.min( Math.sqrt( target.squaredMagnitude(mc.player.getEyePos()) - MathUtils.s2( getAttackRange())), tpAttackRange.get());
////                    double lengthHorizontal = Math.sqrt( MathUtils.s2(targetCenter.getX() - vec3d.getX()) + MathUtils.s2(targetCenter.getZ() - vec3d.getZ()));
////                    double lengthVertical = targetCenter.y - pos2Eye.y - vec3d.y;
//            Vec3d vec3d2 = MovTasks.simulatePlayerMoveTowards(from, towardsUp, estimateLength);
//            Vec3d vec3d3 = MovTasks.simulatePlayerMoveTowards(from, towardsDown, estimateLength);
//            if(target.squaredMagnitude(vec3d2.add(pos2Eye)) < MathUtils.s2(getAttackRange())){
//                boolean safeBack2 = MovTasks.simulatePlayerMoveTowards(vec3d2, from.subtract(vec3d2)).squaredDistanceTo(from) < 1E-2;
//                if(safeBack2)return vec3d2;
//            }
//            if(target.squaredMagnitude(vec3d3.add(pos2Eye)) < MathUtils.s2(getAttackRange())){
//                boolean safeBack3 = MovTasks.simulatePlayerMoveTowards(vec3d3, from.subtract(vec3d3)).squaredDistanceTo(from) < 1E-2;
//                if(safeBack3)return vec3d3;
//            }
//        }
        return from;
    }


    public static HitResult createCrossHairHitResult(Entity camera, double blockInteractionRange, double entityInteractionRange, float tickDelta) {
        double d = Math.max(blockInteractionRange, entityInteractionRange);
        double e = MathHelper.square(d);
        Vec3d vec3d = camera.getCameraPosVec(tickDelta);
        HitResult hitResult = camera.raycast(d, tickDelta, false);
        double f = hitResult.getPos().squaredDistanceTo(vec3d);
        if (hitResult.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            e = f;
            d = Math.sqrt(e);
        }

        Vec3d vec3d2 = camera.getRotationVec(tickDelta);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d);
        float g = 1.0F;
        Box box = camera.getBoundingBox().stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.raycast(camera, vec3d, vec3d3, box, (entity) -> {
            return !entity.isSpectator() && entity.canHit();
        }, e);
        return entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < f ? ensureTargetInRange(entityHitResult, vec3d, entityInteractionRange) : ensureTargetInRange(hitResult, vec3d, blockInteractionRange);
    }
    public static HitResult createEntityOnlyCrossHairResult(Entity camera, double entityInteractionRange, float tickDelta, Predicate<Entity> filter){
        double d = entityInteractionRange;
        double e = MathHelper.square(d);
        Vec3d vec3d = camera.getCameraPosVec(tickDelta);
        Vec3d vec3d2 = camera.getRotationVec(tickDelta);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d);
        Box box = camera.getBoundingBox().stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.raycast(camera, vec3d, vec3d3, box, (entity) -> {
            return !entity.isSpectator() && entity.canHit() && (filter == null || filter.test(entity));
        }, e);
        return entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < e ? ensureTargetInRange(entityHitResult, vec3d, entityInteractionRange): null;
    }
    private static HitResult ensureTargetInRange(HitResult hitResult, Vec3d cameraPos, double interactionRange) {
        Vec3d vec3d = hitResult.getPos();
        if (!vec3d.isInRange(cameraPos, interactionRange)) {
            Vec3d vec3d2 = hitResult.getPos();
            Direction direction = Direction.getFacing(vec3d2.x - cameraPos.x, vec3d2.y - cameraPos.y, vec3d2.z - cameraPos.z);
            return BlockHitResult.createMissed(vec3d2, direction, BlockPos.ofFloored(vec3d2));
        } else {
            return hitResult;
        }
    }
    //todo add Auto crystal
    //return whether the attack will execute delay
    //todo: add attack target render, render the attackTarget if attack is on, refresh every two ticks
    public static Entity searchAttackEntity(boolean auto){
        if(mc.player == null)return null;
        //when tp reach, also attack the targeted entity first
        if(mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY){
            Entity entityCheck = ((EntityHitResult)mc.crosshairTarget).getEntity();
            //fix: check attackable when not auto
            if(!auto || isAttackable(entityCheck)){
                return entityCheck;
            }
        }
        HitResult result = createEntityOnlyCrossHairResult(mc.player, getFinalRange(), 1.0F, CombatTasks::isAttackable);
        if(result != null && result.getType() == HitResult.Type.ENTITY){
            //focusing entity， attack
            //should respect whitelist
            if(isAttackable(((EntityHitResult)result).getEntity())){
//                    mc.interactionManager.attackEntity(mc.player, ((EntityHitResult)result).getEntity());
//                    mc.player.swingHand(Hand.MAIN_HAND);
//                    handleShieldPredict(mc.player.getPitch(), mc.player.getYaw());
                return ((EntityHitResult)result).getEntity();
            }
        }
        //this use mc.crosshairTarget; if hand ...
        if(!auto && mc.crosshairTarget.getType()== HitResult.Type.BLOCK && notSuitableForAttack( mc.player.getMainHandStack())){
            //stop if player only want to mine a block
            return null;
        }
        List<Entity> targets = getAttackableEntitiesForPlayer();
        //Debug.info(pos);
        //fixed: if player is targeting a faraway entity, then it should be privileged
        //fixed: should not target entity at back of me, because some anticheat place fake players to test killarua; use weighted value
        Vec3d vec3d = mc.player.getEyePos();
        Vec3d eye = mc.player.getRotationVector().normalize();
        targets.sort(Comparator.comparingDouble( e-> {
            var pos= e.getPos().subtract(vec3d).normalize();//.dotProduct(eye))
            return (- withMultiply(e,( pos.x * eye.x + pos.z * eye.z)/ (e.getPos().subtract(vec3d).horizontalLength() + 1E-10)));
        }));
        if(!targets.isEmpty()) {
            return targets.get(0);
        }
        return null;
    }
    public static void renderTargetAttack(Event<MatrixStack> stackE){
        var stack = stackE.context;
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.ALWAYS_ATTACK) && mc.player != null && renderAttackEntity.get()){
            float tickDelta = (Float) stackE.extraArgs[0];
            if(mc.player.isUsingItem()){
                //filter bow, but keep shield
                if(mc.player.getActiveHand() == Hand.MAIN_HAND){
                    return;
                }
                if (mc.player.getActiveItem().getItem() instanceof RangedWeaponItem bow){
                    return;
                }
            }
            //only render when holding weapon,
            if(notSuitableForAttack(mc.player.getMainHandStack())){
                return;
            }
            RenderUtils.startDrawVirtual(stack);
            try{
                Entity entity = searchAttackEntity(true);
                if(entity != null){
                    float dist = entity.distanceTo(mc.player);
                    float opacity = Math.min(0.6F, 0.10F + dist * 0.02F);
                    RenderUtils.setAsShaderColor(Color.GREEN, opacity);
                    Box box = RenderUtils.getLerpedBox(entity, tickDelta);
                    RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), box.getMinPos(), box.getMaxPos());
                }
            }finally {
                RenderUtils.stopDrawVirtual(stack);
            }

        }
    }


    //todo: add render to best Entity when holding weapon

    public static boolean autoAttackBest(boolean auto){
        PlayerEntity player=mc.player;
        if(player!=null&&mc.world!=null){
            Entity searchEntity = searchAttackEntity(auto);
            if(searchEntity != null){
                return attackEntity(mc.player, searchEntity);
            }
        }
        return false;
    }
    public static boolean passCriticalPredicate(PlayerEntity player){
        boolean bl3 = player.getAttackCooldownProgress(0.5f) > 0.9f && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS) && !player.hasVehicle() ;
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




    private static final Config.FlagRef DO_INTERVEL_WEAPON = Configs.COMBAT_CONFIG.getBoolean(Configs.AUTOATTACK_DO_INTERVEL_WEAPON);
    private static final Config.FlagRef DO_INTERVEL_HAND = Configs.COMBAT_CONFIG.getBoolean(Configs.AUTOATTACK_DO_INTERVEL_HAND);
    private static final Config.IntRef MAX_ONCE_ATTACK = Configs.COMBAT_CONFIG.getInt(Configs.AUTOATTACK_ONCE_MAX);
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

            if(player.getAttackCooldownProgress(0.5F) > 0.98){
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
                    if(attackEntity(player, target))break;
                    if( -- max <= 0){
                        return;
                    }
                }
            }
        }
    }
    private static final Config.FlagRef autoshield = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_AUTOSHIELD);
    //make async
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
                //the ordinal  of LIVING FLAGS in LivingEntity, may vary with versionsl pls check
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
                            Debug.chat(Text.literal("[AC] 阻挡异常盾牌禁用").formatted(Formatting.RED));
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
    private static final Config.FlagRef bowTpToggle = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_BOW_TP_TOGGLE);
    private static final Config.DoubleRef bowTpRange = Configs.COMBAT_CONFIG.getDouble(Configs.COMBAT_PROJECTILE_TP);
//    private static final Config.FlagRef bowAutoAim = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_BOW_AUTOAIM);
    private static final Config.FlagRef bowExact = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_BOW_EXACT_TP);
    // bow tp
    public static Entity searchBowAimEntity(boolean force, boolean direct){
        if(mc.player == null)return null;
        //when tp reach, also attack the targeted entity first
        if(mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY){
            return ((EntityHitResult)mc.crosshairTarget).getEntity();
        }
        HitResult result = createEntityOnlyCrossHairResult(mc.player, getFinalRange(), 1.0F, CombatTasks::isAttackable);
        if(result != null && result.getType() == HitResult.Type.ENTITY){
            //focusing entity， attack
            //should respect whitelist
            if(isAttackable(((EntityHitResult)result).getEntity())){
//                    mc.interactionManager.attackEntity(mc.player, ((EntityHitResult)result).getEntity());
//                    mc.player.swingHand(Hand.MAIN_HAND);
//                    handleShieldPredict(mc.player.getPitch(), mc.player.getYaw());
                return ((EntityHitResult)result).getEntity();
            }
        }
        List<Entity> targets= getBowAimableEntitiesForPlayer();
        //filter raycast

        Vec3d vec3d = mc.player.getEyePos();
        Vec3d playerRotation = mc.player.getRotationVector().normalize();
        //通过
        if(targets.isEmpty())return null;
        //通过视角偏差
        targets.sort(Comparator.comparingDouble(e -> - e.getEyePos().subtract(vec3d).normalize().dotProduct(playerRotation)));
        //Debug.info(pos);
        return targets.get(0);

    }
    public static void renderTargetIfAutoAim(Event<MatrixStack> stackE){
        var stack = stackE.context;
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_AUTO_AIM) && mc.player != null && mc.player.isUsingItem()){
            float tickDelta = (Float) stackE.extraArgs[0];
            ItemStack itemInUse = mc.player.getActiveItem();
            if(!itemInUse.isEmpty() && (itemInUse.getItem() instanceof RangedWeaponItem || itemInUse.getItem() instanceof TridentItem)){
                RenderUtils.startDrawVirtual(stack);
                try{
                    Entity entity = searchBowAimEntity(true,bowTpRange.get() > 0 );
                    if(entity != null){
                        RenderUtils.setAsShaderColor(Color.GREEN, 0.25F);
                        Box box = RenderUtils.getLerpedBox(entity, tickDelta);
                        RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), box.getMinPos(), box.getMaxPos());
                    }
                }finally {
                    RenderUtils.stopDrawVirtual(stack);
                }
            }
        }
    }
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
//    public static Vec2f calculatePitchYawPredict(float velocity, Vec3d extraVector, Vec3d targetVec){
//        float yaw;
//        double horizontalLen = extraVector.horizontalLength();
//        double
//        calculate_yaw_and_reset_velocity:
//        {
//            if(horizontalLen > 1E-4){
//
//            }
//
//            yaw = (float) Math.toDegrees(Math.atan2( -targetVec.x, targetVec.z));
//            velocity = velocity;
//        }
//
//        //calculate yaw
//        float g = 0.05f;
//        double len = extraVector.length();
//        if(len > 1E-4){
//            //things becomes difficult when we tries to calculate this shit
//            if(len > 10){
//                //GO away!
//                return  (float) Math.toDegrees( Math.asin(- targetVec.normalize().y));
//            }
//            //todo：
//            //计算水平方向
//            Vec3d hDir = new Vec3d(targetVec.x, 0, targetVec.z);
//            double hDistance = hDir.length();
//            if (hDistance < 1e-4) {
//                // 水平距离过小，直接返回目标俯仰角
//                return (float) Math.toDegrees(Math.asin(-targetVec.normalize().y));
//            }
//            hDir = hDir.normalize();
//
//            // 分解 extraVector
//            double extraHorizontal = extraVector.horizontalLength();
//            double extraVertical = extraVector.y;
//
//            // 一阶近似调整目标高度
//            double adjustedY = targetVec.y - (hDistance * extraVertical / velocity);
//            double hDistanceSq = hDistance * hDistance;
//            float velocitySq = velocity * velocity;
//            float velocityPow4 = velocitySq * velocitySq;
//
//            // 计算初始仰角估计 (theta0)
//            double discriminant = velocityPow4 - g * (g * hDistanceSq + 2 * adjustedY * velocitySq);
//            if (discriminant < 0) {
//                return (float) Math.toDegrees(Math.asin(-targetVec.normalize().y));
//            }
//            double value = (velocitySq - Math.sqrt(discriminant)) / (g * hDistance);
//            double pitch0 = Math.atan(value); // 向下为正的弧度
//            double theta0 = -pitch0; // 转换为向上为正的仰角
//
//            // 检查初始水平速度
//            double A0 = velocity * Math.cos(theta0) + extraHorizontal;
//            if (A0 <= 0) {
//                return (float) Math.toDegrees(Math.asin(-targetVec.normalize().y));
//            }
//
//            // 牛顿迭代法求解
//            double theta = theta0;
//            double tolerance = 1e-6;
//            int maxIter = 10;
//            boolean converged = false;
//
//            for (int i = 0; i < maxIter; i++) {
//                double cosTheta = Math.cos(theta);
//                double sinTheta = Math.sin(theta);
//                double A = velocity * cosTheta + extraHorizontal;
//
//                // 水平速度非正，终止迭代
//                if (A <= 1e-5) {
//                    break;
//                }
//
//                double B = velocity * sinTheta + extraVertical;
//                double term1 = (B * hDistance) / A;
//                double term2 = (g * hDistanceSq) / (2 * A * A);
//                double f = term1 - term2 - targetVec.y;
//
//                // 计算导数 f'(theta)
//                double numerator = velocity * cosTheta * A + velocity * sinTheta * B;
//                double dfTerm1 = (hDistance * numerator) / (A * A);
//                double dfTerm2 = (g * hDistanceSq * velocity * sinTheta) / (A * A * A);
//                double fPrime = dfTerm1 - dfTerm2;
//
//                // 避免除零
//                if (Math.abs(fPrime) < 1e-10) {
//                    break;
//                }
//ine
//                double delta = f / fPrime;
//                theta -= delta;
//
//                if (Math.abs(delta) < tolerance) {
//                    converged = true;
//                    break;
//                }
//            }
//
//            // 验证解的有效性
//            if (converged && !Double.isNaN(theta) && theta >= -Math.PI/2 && theta <= Math.PI/2) {
//                // 转换为向下为正的俯仰角（度数）
//                return (float) -Math.toDegrees(theta);
//            }
//
//            // 迭代失败退回目标俯仰角
//            return (float) Math.toDegrees(Math.asin(-targetVec.normalize().y));
//        }

//
//
////        double hDistance = targetVec.horizontalLength();
////        double hDistanceSq = hDistance * hDistance;
////        float g = 0.006F;
////        float velocitySq = velocity * velocity;
////        float velocityPow4 = velocitySq * velocitySq;
////        return (float)-Math.toDegrees(Math.atan((velocitySq - Math
////            .sqrt(velocityPow4 - g * (g * hDistanceSq + 2 * targetVec.y * velocitySq)))
////            / (g * hDistance)));
//    }
    private static final AtomicBoolean delayPacketFlag = new AtomicBoolean(false);
    private static final Config.FlagRef legalBowAction = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_BOW_AIM_LEGALLY);
    private static final Config.DoubleRef bowTargetLerp = Configs.COMBAT_CONFIG.getDouble(Configs.COMBAT_BOW_TICKS_PREDICT);
    private static final Config.EnumRef<Configs.LegalTargetingMode> bowBypassMode = Configs.COMBAT_CONFIG.getEnum(Configs.COMBAT_BOW_LEGAL_TARGETTING);
    private static final Config.FlagRef autoTridentDupe = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_TRIDENT_AUTO_DUPE);
    private static final Config.FlagRef lowerVersionFeature = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_PROJECTILE_USE_1_20_4_RULES);
    //道具锁人 使用弓箭相同的配置
    private static float getShootingPowerCrossbow(ItemStack a) {
        ChargedProjectilesComponent stack = a.get(DataComponentTypes.CHARGED_PROJECTILES);
        return (stack != null && stack.contains(Items.FIREWORK_ROCKET)) ? 1.6F : 3.15F;
    }
    private static Vec3d predictTargetVecForEntity(Entity entity, float velocity, boolean useBow){
        Vec3d estimatedDelta = entity.getPos().subtract(mc.player.getPos());
        double estimateSpeed = Math.max( estimatedDelta.length() / (velocity + (useBow? Math.max(bowTpRange.get(), 0): 0)), 1);
        int tickNeeded = Math.min((int)estimateSpeed, 5);
        return entity.getEyePos().subtract(entity.getPos()).multiply(0.75).add(entity.getLerpedPos((float) (2.0f + bowTargetLerp.get() * tickNeeded)));
    }
    public static boolean handleBowActionBeforeShoot(PlayerActionC2SPacket actionPacket){
        //fixme: figure out why server-side 1.21- act like that, figureout how to
        //fixme: checkout
        //todo: add lowerversion flag
        if(actionPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && mc.player != null ){
            //delay tp do not run BowAction logic and let it go
            //may not using item anymore
            if(delayPacketFlag.compareAndSet(true, false)){
                return true;
            }
            //ret
            if(! mc.player.isUsingItem()){
                return true;
            }
            //run main logic
            ItemStack stack = mc.player.getActiveItem();
            //only consider BowItem
            if(stack.isEmpty())return true;
            Vec2f playerPitchYaw = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
            boolean autoAim = HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_AUTO_AIM);
            boolean delayPackets = false;
            Entity targetEntity;
            boolean useDelayMovement = legalMode.get() && bowBypassMode.getValue() == Configs.LegalTargetingMode.DELAY_MOVEMENT;
            Entity nowMePointingTheEntity = (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY)? ((EntityHitResult)mc.crosshairTarget).getEntity() : null;
            //随便写的数
            if(nowMePointingTheEntity != null && nowMePointingTheEntity.getPos().squaredDistanceTo(mc.player.getEyePos()) > 50){
                nowMePointingTheEntity = null;
            }
            float velocity;
            Vec3d facing;
            boolean searchEntity = autoAim;
            if(searchEntity){
                Entity entity = searchBowAimEntity(true, true);
                if(entity != null){

                    // calculate lerp by speed
                    targetEntity = entity;
                }else{


                    targetEntity = null;
                }
            }else{
                targetEntity = null;
            }
            if(passUseItemIdCheck(stack)){
                //if this is a fucking plugin weapon, we may not trigger action below
                velocity = 3600000000f;
                facing = targetEntity == null ?mc.player.getRotationVector().normalize():  predictTargetVecForEntity(targetEntity,  velocity,false).subtract(mc.player.getEyePos()); // target.getEyePos().subtract(mc.player.getEyePos());
            }
            else if((stack.getItem() instanceof BowItem ) || (stack.getItem() instanceof TridentItem)){
                //goes accelerate with bowTP
                boolean legalMode = legalBowAction.get();
                boolean shouldResetRotation = autoAim;
                if(stack.getItem() instanceof BowItem){
                    velocity = (72000 - mc.player.getItemUseTimeLeft()) / 20F;
                    velocity = (velocity * velocity + velocity * 2) / 3;
                    if(velocity > 1)
                        velocity = 1;
                    velocity = ((stack.getItem() instanceof BowItem) ? (velocity * 3.0F) : (velocity * getShootingPowerCrossbow(stack)));
                }else if(stack.getItem() instanceof TridentItem){
                    velocity = 2.5F;
                }else{
                    //whatever
                    velocity = 3.0F;
                }

                facing = targetEntity == null ? mc.player.getRotationVector().normalize() : predictTargetVecForEntity(targetEntity, velocity, true).subtract(mc.player.getEyePos());

                make_movements:
                {
                    if(!legalMode && bowTpToggle.get() && bowTpRange.get() > 0.0D && (stack.getItem() instanceof BowItem )){
                        //add movements to accelerate the projectile

                        boolean exactTp = bowExact.get();
                        double range = bowTpRange.get();
                        Vec3d facingNorm = facing.normalize();
                        Vec3d oppositeFacing = Vec3d.ZERO.subtract(facingNorm);
                        Vec3d finalMove = Vec3d.ZERO;
                        Vec3d currentPlayerPos = mc.player.getPos();

                        test_tp_position:
                        {
                            // optimize the collision check by caching List of Boxes
                            MovTasks.CollisionContext context = new MovTasks.CollisionCache(mc.player, currentPlayerPos, currentPlayerPos.add( oppositeFacing.multiply(range + 1.0d)), true);
                            double test = range;
                            for (; test > 10.0D; test -= 1.0D){
                                if(exactTp){
                                    Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                                    if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                        finalMove = oppositeMultiply;
                                        break test_tp_position;
                                    }
                                }else{
                                    if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeFacing.multiply(test))){
                                        finalMove = oppositeFacing.multiply(test);
                                        break test_tp_position;
                                    }
                                }
                            }
                            // t < 10
                            //check again
                            test = 10.0D;
                            for (; test > 0.0D; test -= 0.5D){
                                Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                                if(exactTp){
                                    if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                        finalMove = oppositeMultiply;
                                        break test_tp_position;
                                    }
                                }else{
                                    if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeMultiply)){
                                        finalMove = oppositeMultiply;
                                        break test_tp_position;
                                    }
                                }
                                Vec3d oppoHorizontal = new Vec3d(oppositeMultiply.x, 0.0d, oppositeMultiply.z);
                                Vec3d simulateMove = context.simulateMovement(mc.player, currentPlayerPos, oppoHorizontal);
                                if(MovTasks.validMovementAsServer(oppoHorizontal, simulateMove)){
                                    Vec3d simulateDownMove = context.simulateMovement(mc.player, currentPlayerPos.add(simulateMove), new Vec3d(0, oppositeMultiply.y, 0));
                                    Vec3d wholeMovement = simulateMove.add(simulateDownMove);
                                    //y does not matter , xz matters
                                    if(MovTasks.validMoveTo(context, currentPlayerPos.add(wholeMovement), wholeMovement.multiply(-1))){
                                        finalMove = wholeMovement;
                                        break test_tp_position;
                                    }
                                }
                            }
                            // should strengthen move when test < 10,
                        }
                        if(finalMove.lengthSquared() > 1E-4){
                            //随便写的阈值 速度太快不需要转向

                            shouldResetRotation &= finalMove.length() < 10d;
                            List<Vec3d> tpSequence = MovTasks.generateTpSequence(currentPlayerPos, currentPlayerPos.add(finalMove), false, 161, true);
                            if(!tpSequence.isEmpty()){
                                Vec2f redirectTarget = null;
                                Debug.chat(Text.literal("[Bow TP] Projectile Velocity Simulate %.2f".formatted(finalMove.length())).formatted(Formatting.GREEN));
                                List<MovTasks.MovInfo> movements = new ArrayList<>();
                                int size = tpSequence.size();
                                for (int i=0; i< size; ++i){
                                    movements.add(i == 0 ? MovTasks.MovInfo.createNotOnGround(tpSequence.get(i)) : MovTasks.MovInfo.create(tpSequence.get(i)));
                                }
                                if(shouldResetRotation){
                                    //need test
                                    redirectTarget =  calculatePitchYawPredict((float) (velocity ), finalMove, facing);  // new Vec2f( , EntityUtils.getSafeYaw (mc.player, (float)Math.toDegrees(Math.atan2( -finalMove.x, finalMove.z))));
                                    if(Float.isNaN(redirectTarget.x) || Float.isInfinite(redirectTarget.x)){
                                        //unreachable target via aim
                                        Debug.chat("[Bow Aim] Arrow failed to reach the target");
                                        shouldResetRotation = false;
                                    }
                                }
                                movements.add(shouldResetRotation ? new MovTasks.MovInfo(currentPlayerPos.add(0, 9E-8, 0), null, true, redirectTarget):  MovTasks.MovInfo.create(currentPlayerPos.add(0, 9E-8, 0)));

    //                        List< MovTasks.MovInfo> movements = List.of(
    //                            MovTasks.MovInfo.createNotOnGround(currentPlayerPos.add(finalMove)),
    //
    //                        );
                                MovTasks.scheduleFarawayMoveInternal(movements, false, MovTasks.MovingContext.create(currentPlayerPos), true);
//                                if(finalMove.y > 0){
//                                    //reset falldistance
//                                    mc.player.fallDistance = MovTasks. FORCE_RESET_DISTANCE;
//                                    //in case that resync packet cause OnGround falldamage
//                                    mc.player.setOnGround(false);
//                                }
                                MovTasks.setupAutoResync(mc.player.getPos() , 10);
                                //disable later autoAim because we have sent the pitchYaw
                                autoAim = false;
                                break make_movements;
                            }

                            //send packets to simulate movements
                        }
                        Debug.chat(Text.literal("[Bow TP] Projectile Velocity fail to simulate"));
                    }
                    //log the warm
                    if(legalMode){
                        if(bowTpToggle.get() && bowTpRange.get() > 0.0D){
                            Debug.chat("[Bow TP] Projectile Velocity Simulate not enabled in Legal Mode");
                        }
                    }
                    //try target normally
                    // do not turn head if targetEntity is already a targetEntity
                }

                //reset pitch yaw to avoid lookat change

            }else{
                return true;
            }
            if(RenderTasks.DEBUG_RENDER_BOWAIM){
                RenderTasks.registerVirtualRenderTask(new RenderTasks.LineRenderingTask(mc.player.getEyePos(), facing, RenderTasks.DEBUG_TICK));
            }
            if(searchEntity){
                if(targetEntity != null){
                    Debug.chat(Text.literal("[Bow Aim] Aim at %s".formatted(targetEntity instanceof PlayerEntity player? "player ": "entity ")).append(EntityUtils.getEntityDisplayable(targetEntity)).formatted(Formatting.GREEN));
                }else{
                    Debug.chat(Text.literal("[Bow Aim] Target absent"));
                    //targetingAtVec = null;
                }
            }
            if(autoAim && targetEntity != null && targetEntity != nowMePointingTheEntity){
                float vc = velocity;
                if(useDelayMovement){
                    //need implement
                    delayPackets = true;
                    ClientPlayerAccess.of(mc.player).getLegalMovementManager()
                        .addMovementModifier(
                            new LegalMovementManager.MovementModifier() {
                                @Override
                                public int priority() {
                                    return -10000000;
                                }
                                @Override
                                public boolean mayModifyRotation() {
                                    return true;
                                }
                                @Override
                                public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                                    ClientPlayerEntity player = movementManagerEvent.context().playerStatus.entity;
                                    Vec3d targetAt = predictTargetVecForEntity(targetEntity, vc, true);
                                    Vec3d targetAtFacing = targetAt.subtract(player.getEyePos());
                                    Vec2f pitchYaw = calculatePitchYawPredict((float) (vc),  player.getVelocity(), targetAtFacing);
                                    if(Float.isNaN(pitchYaw.x) || Float.isInfinite(pitchYaw.x) || Float.isNaN(pitchYaw.y) || Float.isInfinite(pitchYaw.y)){
                                        Debug.chat("[Bow Aim] Arrow failed to reach the target");
                                        return;
                                    }
                                    EntityUtils.setEntityPitchSafe(player, pitchYaw.x);
                                    EntityUtils.setEntityYawSafe(player, pitchYaw.y);
                                }

                                @Override
                                public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                                    if(enabledThisTick)
                                        movementManagerEvent.context().playerStatus.restoreRotation();
                                    //add post packets
                                    //mc.getNetworkHandler().sendPacket(actionPacket);
                                    if(true)
                                        ACPostTasks.addPostTransactionAction((handler)->{
                                            handler.sendPacket(actionPacket);
                                        });
                                    return false;
                                }
                            }
                        );
                }else{
                    //add use item feature
                    if(bowBypassMode.getValue() == Configs.LegalTargetingMode.DELAY_MOVEMENT){
                        //because of setting movement , so velocity reset to zero

                        Vec2f red =  calculatePitchYawPredict(velocity, Vec3d.ZERO, facing);
                        if(Float.isNaN(red.x) || Float.isInfinite(red.x) || Float.isNaN(red.y) || Float.isInfinite(red.y)){
                            Debug.chat("[Bow Aim] Arrow failed to reach the target");
                        }else{
                            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(red.y, red.x, mc.player.isOnGround()));
                        }
                    }else{
                        //recalculate redirectTarget
                        Vec2f red = calculatePitchYawPredict(velocity, mc.player.getVelocity(), facing);
                        if(Float.isNaN(red.x) || Float.isInfinite(red.x) || Float.isNaN(red.y) || Float.isInfinite(red.y)){
                            Debug.chat("[Bow Aim] Arrow failed to reach the target");
                        }else{
                            mc.interactionManager.sendSequencedPacket(mc.world, (s)->{
                                return new PlayerInteractItemC2SPacket(mc.player.getActiveHand(), s, red.y, red.x);
                            });
                        }
                    }
                }
            }

            if(delayPackets){
                //may schedule a post move for legal mode
                // do legal mode
                delayPacketFlag.set(true);

                return false;
            }else{
                mc.player.setPitch(playerPitchYaw.x);
                mc.player.setYaw(playerPitchYaw.y);

            }

        }
        return true;
    }
    public static boolean handleTridentDupe(PlayerActionC2SPacket actionC2SPacket){
        if(actionC2SPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && mc.player != null && autoTridentDupe.get() && mc.player.getMainHandStack().getItem() instanceof TridentItem trident){
            //dupe trident
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 3, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            Tasks.scheduleDelayed(()->mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 3, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player), 1);
        }
        return true;
    }


    private static final Config.StringRef useItemAutoAimId = Configs.COMBAT_CONFIG.getString(Configs.COMBAT_USE_ITEM_AUTOAIM);
    private static Predicate<String> precompileRegexUseItemId = null;
    private static final Config.FlagRef pearlTp = Configs.COMBAT_CONFIG.getBoolean(Configs.COMBAT_PEARL_TP);

    static{
        useItemAutoAimId.addUpdateListener((str)->{
            try{
                precompileRegexUseItemId = Pattern.compile(str).asMatchPredicate();
            }catch (Throwable e){
                Debug.info("Failed to compile regex %s".formatted(str));
                precompileRegexUseItemId = null;
            }
        });
        useItemAutoAimId.setValue(useItemAutoAimId.getValue());
    }
    private static boolean passUseItemIdCheck(ItemStack stack){
        if(precompileRegexUseItemId == null)return false;
        String id = Registries.ITEM.getId( stack.getItem()).getPath();
        if(precompileRegexUseItemId.test(id)){
            return true;
        }
        String sfid = ItemStackUtils.getSfId(stack);

        return sfid != null && precompileRegexUseItemId.test(sfid);
    }
    public static void handleCrossbowActionAndItemUseBeforeShoot(Event<PlayerInteractItemC2SPacket> packetMutableObject){
        //targeting
        if(packetMutableObject.isCancelled())return;
        PlayerInteractItemC2SPacket packet = packetMutableObject.context();
        Hand hand = packet.getHand();
        ItemStack stack = mc.player.getStackInHand(hand);
        if(!stack.isEmpty() ){
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_AUTO_AIM)){


                //pass check, autoaim
                if(passUseItemIdCheck(stack)){
                    //line predict
                    //can override crossbow-like items
                    Entity target = searchBowAimEntity(true, true);
                    if(target != null){
                        Debug.chat(Text.literal("[Bow Aim] Aim at %s".formatted(target instanceof PlayerEntity player? "player ": "entity ")).append(EntityUtils.getEntityDisplayable(target)).formatted(Formatting.GREEN));
                        //simulate line by vc high
                        Vec3d facing = predictTargetVecForEntity(target, 3600000000f, false).subtract(mc.player.getEyePos()); // target.getEyePos().subtract(mc.player.getEyePos());
                        Vec2f py = EntityUtils.rotationToPitchYaw(facing.normalize());
                        //todo need test under 1.21
                        packet = new PlayerInteractItemC2SPacket(hand, packet.getSequence(), py.y, py.x);
                    }else{
                        Debug.chat(Text.literal("[Bow Aim] Target absent"));
                    }
                }else if(stack.getItem() instanceof CrossbowItem item){
                    //bow predict
                    //direct = false;
                    Entity target = searchBowAimEntity(true, false);
                    if(target != null){
                        Debug.chat(Text.literal("[Bow Aim] Aim at %s".formatted(target instanceof PlayerEntity player? "player ": "entity ")).append(EntityUtils.getEntityDisplayable(target)).formatted(Formatting.GREEN));
                        float velocity = getShootingPowerCrossbow(stack);
                        Vec3d facing = predictTargetVecForEntity(target, velocity, false).subtract(mc.player.getEyePos());
                        Vec2f redirectTarget = calculatePitchYawPredict(velocity, Vec3d.ZERO, facing);
                        if(Float.isNaN(redirectTarget.x) || Float.isInfinite(redirectTarget.x) || Float.isNaN(redirectTarget.y) || Float.isInfinite(redirectTarget.y)){
                            Debug.chat("[Bow Aim] Arrow failed to reach the target");
                        }else{
                            packet = new PlayerInteractItemC2SPacket(hand, packet.getSequence(), redirectTarget.y, redirectTarget.x);
                        }
                    }else{
                        Debug.chat(Text.literal("[Bow Aim] Target absent"));
                    }
                }
                //pearl tp

            }
        }
        if(pearlTp.get()){
            if(stack.getItem() instanceof EnderPearlItem pearl || stack.getItem() instanceof SplashPotionItem || stack.getItem() instanceof ExperienceBottleItem || stack.getItem() instanceof LingeringPotionItem || stack.getItem() instanceof EggItem){
                if (pearlTp.get()){
                    pearl_tp:
                    {
                        boolean exactTp = bowExact.get();
                        double range = bowTpRange.get();
                        Vec3d facing = EntityUtils.pitchYawToRotation(packet.getPitch(), packet.getYaw());// mc.player.getRotationVector();
                        Vec3d facingNorm = facing.normalize();
                        Vec3d oppositeFacing = Vec3d.ZERO.subtract(facingNorm);
                        Vec3d finalMove = Vec3d.ZERO;
                        Vec3d currentPlayerPos = mc.player.getPos();

                        test_tp_position:
                        {
                            // optimize the collision check by caching List of Boxes
                            MovTasks.CollisionContext context = new MovTasks.CollisionCache(mc.player, currentPlayerPos, currentPlayerPos.add( oppositeFacing.multiply(range + 1.0d)), true);
                            double test = range;
                            for (; test > 10.0D; test -= 1.0D){
                                if(exactTp){
                                    Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                                    if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                        finalMove = oppositeMultiply;
                                        break test_tp_position;
                                    }
                                }else{
                                    if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeFacing.multiply(test))){
                                        finalMove = oppositeFacing.multiply(test);
                                        break test_tp_position;
                                    }
                                }
                            }
                            // t < 10
                            //check again
                            test = 10.0D;
                            for (; test > 0.0D; test -= 0.5D){
                                Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                                if(exactTp){
                                    if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                        finalMove = oppositeMultiply;
                                        break test_tp_position;
                                    }
                                }else{
                                    if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeMultiply)){
                                        finalMove = oppositeMultiply;
                                        break test_tp_position;
                                    }
                                }
                                Vec3d oppoHorizontal = new Vec3d(oppositeMultiply.x, 0.0d, oppositeMultiply.z);
                                Vec3d simulateMove = context.simulateMovement(mc.player, currentPlayerPos, oppoHorizontal);
                                if(MovTasks.validMovementAsServer(oppoHorizontal, simulateMove)){
                                    Vec3d simulateDownMove = context.simulateMovement(mc.player, currentPlayerPos.add(simulateMove), new Vec3d(0, oppositeMultiply.y, 0));
                                    Vec3d wholeMovement = simulateMove.add(simulateDownMove);
                                    //y does not matter , xz matters
                                    if(MovTasks.validMoveTo(context, currentPlayerPos.add(wholeMovement), wholeMovement.multiply(-1))){
                                        finalMove = wholeMovement;
                                        break test_tp_position;
                                    }
                                }
                            }
                            //should strengthen move when test < 10,
                        }
                        if(finalMove.lengthSquared() > 1E-4){
                            //随便写的阈值 速度太快不需要转向
                            List<Vec3d> tpSequence = MovTasks.generateTpSequence(currentPlayerPos, currentPlayerPos.add(finalMove), false, 161, true);
                            if(!tpSequence.isEmpty()){
                                Debug.chat(Text.literal("[Pearl TP] Projectile Velocity Simulate %.2f".formatted(finalMove.length())).formatted(Formatting.GREEN));
                                List<MovTasks.MovInfo> movements = new ArrayList<>();
                                int size = tpSequence.size();
                                for (int i=0; i< size; ++i){
                                    movements.add(i == 0 ? MovTasks.MovInfo.createNotOnGround(tpSequence.get(i)) : MovTasks.MovInfo.create(tpSequence.get(i)));
                                }
                                movements.add(MovTasks.MovInfo.create(currentPlayerPos.add(0, 9E-8, 0)));

                                //                        List< MovTasks.MovInfo> movements = List.of(
                                //                            MovTasks.MovInfo.createNotOnGround(currentPlayerPos.add(finalMove)),
                                //
                                //                        );
                                MovTasks.scheduleFarawayMoveInternal(movements, false, MovTasks.MovingContext.create(currentPlayerPos), true);
                                //                                if(finalMove.y > 0){
                                //                                    //reset falldistance
                                //                                    mc.player.fallDistance = MovTasks. FORCE_RESET_DISTANCE;
                                //                                    //in case that resync packet cause OnGround falldamage
                                //                                    mc.player.setOnGround(false);
                                //                                }
                                MovTasks.setupAutoResync(mc.player.getPos() , 10);
                                break pearl_tp;
                            }

                            //send packets to simulate movements
                        }
                        Debug.chat(Text.literal("[Pearl TP] Projectile Velocity fail to simulate"));
                    }
                }
            }
        }
        packetMutableObject.context(packet);
    }
    //todo: 自动搭路

    static {
        updateWhitelist(COMBAT_WHITELISTED.getValue());
        COMBAT_WHITELISTED.addUpdateListener(CombatTasks::updateWhitelist);
        COMBAT_FRIEND.addUpdateListener(str->COMBAT_FRIEND_PATTERN = Pattern.compile(str).asMatchPredicate());
        EntityTasks.getEntityTickListener().registerHandler(CombatTasks::handlePlayerTickUpdate);
        Listener.registerSinglePacketListener(EntityTrackerUpdateS2CPacket.class, CombatTasks::handleAutoShield);
        Listener.registerSinglePacketListener(CooldownUpdateS2CPacket.class, CombatTasks::handleShieldCooldownFastWrite);
        Listener.registerSinglePacketListener(PlayerActionC2SPacket.class, CombatTasks::handleBowActionBeforeShoot);
        Listener.registerSinglePacketListener(PlayerActionC2SPacket.class, CombatTasks::handleTridentDupe);
        Listener.getPlayerItemUsePacketCreate().registerHandler(CombatTasks::handleCrossbowActionAndItemUseBeforeShoot);
        RenderMain.getRenderLayerTasks().registerHandler(CombatTasks::renderTargetAttack);
        RenderMain.getRenderLayerTasks().registerHandler(CombatTasks::renderTargetIfAutoAim);
    }

}
