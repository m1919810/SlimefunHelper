package me.matl114.hacks.modules.combat;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

public class Attack extends BaseModule {
    public static final String[] COMBAT_TP_ENABLE = {"att-bot", "tp-enable"};
    public static final String[] COMBAT_TP_REACH = {"att-bot", "tp-reach"};
    public static final String[] COMBAT_LEGAL_MOD = {"att-bot", "legal-mode"};
    public static final String[] COMBAT_MACE_ENABLE = {"att-bot", "mace-enable"};
    public static final String[] COMBAT_MACE_HACK = {"att-bot", "mace-height-multiply"};

    public static final String[] COMBAT_EXACT_ATTACK = {"att-bot", "exact-tp"};
    public static final String[] COMBAT_LEGAL_TARGETTING = {"att-bot", "legal-targeting"};
    public static final String[] COMBAT_CRITIC = {"att-bot", "critic"};
    public static final String[] ATTACK = {"att-bot", "always-att"};
    public static final String[] HOTKEY_ATTACK = {"att-bot", "always-att-hotkey"};
    public static final String[] COMBAT_RENDER_TARGET = {"att-bot", "render-target"};

    public Attack() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.COMBAT_CONFIG, ATTACK).build();

    public final KeyBindRef hotkey = toggleHotkey(
                    Configs.COMBAT_CONFIG,
                    HOTKEY_ATTACK,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_K),
                    ATTACK)
            .build();

    public final FlagRef legalMode =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_LEGAL_MOD).build();

    public final FlagRef enableTp =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_TP_ENABLE).build();

    public final DoubleRef tpRange = builder(Configs.COMBAT_CONFIG, COMBAT_TP_REACH, DoubleRef.TYPE)
            .defaultValue(0.0D)
            .build();

    public final FlagRef enableMace =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_MACE_ENABLE).build();

    public final DoubleRef maceHeight = builder(Configs.COMBAT_CONFIG, COMBAT_MACE_HACK, DoubleRef.TYPE)
            .defaultValue(30.0D)
            .validator(Configs.doubleRange(-200.0D, 200.0D))
            .build();

    public final FlagRef exactAttack =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_EXACT_ATTACK).build();

    public final EnumRef<Configs.LegalTargetingMode> legalTargetingMode = builder(
                    Configs.COMBAT_CONFIG, COMBAT_LEGAL_TARGETTING, Configs.LegalTargetingMode.class)
            .defaultValue(Configs.LegalTargetingMode.DELAY_MOVEMENT)
            .build();

    @ApiStatus.Experimental
    public final FlagRef critic =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_CRITIC).build();

    public final FlagRef renderAttackTarget =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_RENDER_TARGET).build();

    private final Random attackOffsetRand = new Random();

    public boolean canUseTp() {
        return enableTp.get() && tpRange.get() > 1E-7;
    }

    public boolean canUseMaceTp() {
        return enableMace.get() && maceHeight.get() > 1E-7;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        // should be in high priority
        registerListener(Listener.getAttackAction(), this::onAttack, -999);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRenderTarget);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public void onAttack(Event<HitResult> hitResult) {
        if (hitResult.isCancelled()) return;
        if (enable.get()) {
            PlayerEntity player = mc.player;
            if (player != null && mc.world != null) {
                if (tryAttack(false)) {
                    mc.attackCooldown = 1;
                    hitResult.cancel();
                } else if (hitResult.context().getType() == HitResult.Type.ENTITY) {
                    // if target at an Entity but we didn't attack it, then it should be cancelled
                    mc.attackCooldown = 0;
                    hitResult.cancel();
                }
            }
        }
    }

    public void onRenderTarget(Event<MatrixStack> stackE) {
        var stack = stackE.context;
        if (enable.get() && mc.player != null && renderAttackTarget.get()) {
            float tickDelta = (Float) stackE.extraArgs[0];
            if (mc.player.isUsingItem()) {
                // filter bow, but keep shield
                if (mc.player.getActiveHand() == Hand.MAIN_HAND) {
                    return;
                }
                if (mc.player.getActiveItem().getItem() instanceof RangedWeaponItem bow) {
                    return;
                }
            }
            // only render when holding weapon,
            if (CombatTasks.notSuitableForAttack(mc.player.getMainHandStack())) {
                return;
            }
            RenderUtils.startDrawVirtual(stack);
            try {
                Entity entity = CombatTasks.getTargetSelector().searchAttackEntity(getTpSelectRange(), true);
                if (entity != null) {
                    float dist = entity.distanceTo(mc.player);
                    float opacity = Math.min(0.6F, 0.10F + dist * 0.02F);
                    Box box = RenderUtils.getLerpedBox(entity, tickDelta);
                    RenderUtils.drawSolidBox(
                            stack, box.getMinPos(), box.getMaxPos(), ColorUtils.withAlpha(Color.GREEN, opacity));
                }
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }

    public List<Entity> getCurrentRangeEntities() {
        return CombatTasks.getTargetSelector().getAttackableEntities(getTpSelectRange());
    }

    // the attack return value of whether it needs cooldown, for delayMovement attacking
    public boolean tryAttack(boolean auto) {
        if (mc.player == null) return false;
        Entity entity = CombatTasks.getTargetSelector().searchAttackEntity(getTpSelectRange(), auto);
        if (entity != null) {
            return attackEntity(entity);
        }
        return false;
    }

    public double getTpSelectRange() {
        return CombatTasks.getCombatExtra().getAttackRange() + (canUseTp() ? Math.max(0.0d, tpRange.get()) : 0.0D);
    }

    @ApiMethod
    public static void attackWithCritic(PlayerEntity player, Entity target, boolean criticSprint) {
        if (criticSprint) {
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        // we use event to handle shield predict
        // handleShieldPredict(mc.player.getPitch(), mc.player.getYaw());
        if (criticSprint) {
            ClientPlayerAccess.of(mc.player).resyncSprint();
        }
    }

    public boolean attackEntity(Entity target) {
        if (legalMode.get()) {
            return processLegalAttack(target);
        } else {
            return processIllegalAttack(target);
        }
    }

    private boolean processLegalAttack(Entity target) {
        var player = mc.player;
        if (player == null) return false;
        final boolean criticSprint = critic.get() && player.isSprinting();
        final double attackRange = CombatTasks.getCombatExtra().getAttackRange();
        Vec3d vec3d = mc.player.getPos();
        // do not add mace or tp attack in legal mode

        if (mc.crosshairTarget instanceof EntityHitResult entity && entity.getEntity() == target) {
            // already actioned in caller
            // may not actioned in caller, fix it
            attackWithCritic(player, target, criticSprint);
            return false;
        } else if (true) {
            // 提前转向 下个tick就有正确的velocity了
            // mace not enable in legal mode
            // use Item packet should trigger by a non-empty item

            // add movement prediction position targeting option
            // check if it can pass grimac in real situation
            boolean useTp = (canUseTp()
                    && target.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) > MathUtils.s2(attackRange));
            boolean delayTurningAround =
                    legalTargetingMode.getValue() == Configs.LegalTargetingMode.DELAY_MOVEMENT || useTp;
            if (!delayTurningAround && legalTargetingMode.getValue() == Configs.LegalTargetingMode.USEITEM_PACKET) {
                // use item
                // only do the targeting and attack
                Hand hand;
                if (!mc.player.getMainHandStack().isEmpty()) {
                    hand = Hand.MAIN_HAND;
                } else if (!mc.player.getOffHandStack().isEmpty()) {
                    hand = Hand.OFF_HAND;
                } else {
                    hand = null;
                }
                if (hand != null) {
                    Vec3d eyePos = target.getEyePos();
                    Vec3d targetPos = target.getPos();
                    double percentage = attackOffsetRand.nextDouble(0.75d, 0.95d);
                    Vec3d attackOffsetted =
                            targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
                    attackOffsetted.add(
                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                            attackOffsetRand.nextDouble(-0.05d, 0.05d));
                    Vec3d cacheDirection =
                            attackOffsetted.subtract(mc.player.getEyePos()).normalize();
                    Vec2f toDirection = EntityUtils.rotationToPitchYaw(cacheDirection);
                    mc.interactionManager.sendSequencedPacket(mc.world, (i) -> {
                        return new PlayerInteractItemC2SPacket(
                                hand, i, EntityUtils.getSafeYaw(mc.player, toDirection.y), toDirection.x);
                    });
                    //
                    attackWithCritic(player, target, criticSprint);
                    // EntityUtils.setEntityRotationSafe(args, cacheDirection);
                    return false;
                }
                // fallback to delay Turn
                delayTurningAround = true;
            }
            boolean useDelay = delayTurningAround;
            if (useDelay) {
                // TODO: fix this bug: can not pass matrix ac when on ground , check numbers and positions,
                ClientPlayerAccess.of(mc.player)
                        .getLegalMovementManager()
                        .addMovementModifier(new LegalMovementManager.MovementModifier() {
                            Vec3d posDelta = Vec3d.ZERO;
                            Vec3d posDelta2 = Vec3d.ZERO;
                            Vec3d velocity;
                            boolean distancePassAttack = true;

                            @Override
                            public int priority() {
                                return -10000000;
                            }

                            @Override
                            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                                ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                                // step back our position
                                velocity = args.getVelocity();

                                Vec3d vec3d = args.getPos();

                                if (tpRange.get() > 1E-7
                                        && target.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                                                > MathUtils.s2(attackRange)) {
                                    // need tp attack
                                    // how?
                                    // 平面突袭？
                                    if (exactAttack.get()) {
                                        // disable
                                        Debug.chat("[Attack Bot] Exact Attack选项在Legal Mode中无效!");
                                    }

                                    Vec3d vec3d1 = MovTasks.tpAttackSearch(
                                                    vec3d, target.getBoundingBox(), attackRange, 9.9, 1)
                                            .stream()
                                            .findFirst()
                                            .orElse(null);
                                    // calculateBestReachPos(vec3d, target.getBoundingBox());
                                    if (vec3d1 != null && vec3d1.squaredDistanceTo(vec3d) > 1E-7) {
                                        posDelta = vec3d; // vec3d1.subtract(vec3d);
                                        posDelta2 = vec3d1;
                                        args.setPosition(vec3d1.add(0, 9E-8, 0));
                                    }
                                    // backoff
                                    if (target.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                                            > MathUtils.s2(attackRange)) {
                                        // Debug.chat("Distance to large , disable atack");
                                        distancePassAttack = false;
                                        movementManagerEvent.context.playerStatus.restoreRotation();
                                        args.setPosition(vec3d);
                                        // skip attack
                                    }
                                }
                                // after move player, do target
                                if (distancePassAttack) {
                                    Vec3d eyePos = target.getEyePos();
                                    Vec3d targetPos = target.getPos();
                                    double percentage = attackOffsetRand.nextDouble(0.75d, 0.95d);
                                    Vec3d attackOffsetted = targetPos.add(
                                            eyePos.subtract(targetPos).multiply(percentage));
                                    attackOffsetted.add(
                                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                            attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                            attackOffsetRand.nextDouble(-0.05d, 0.05d));
                                    Vec3d cacheDirection = attackOffsetted
                                            .subtract(args.getEyePos())
                                            .normalize();

                                    EntityUtils.setEntityRotationSafe(args, cacheDirection);
                                    if (canUseMaceTp()
                                            && mc.player.getActiveItem().getItem() instanceof MaceItem) {
                                        Debug.chat(
                                                Text.literal(
                                                        "[Attack Bot] Mace Attack Simulation is not supported in legal mode !"));
                                    }
                                }

                                // restore velocity after collide
                                args.setVelocity(velocity);
                            }

                            @Override
                            public boolean postModify(
                                    Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                                if (!enabledThisTick) {
                                    // rare,,, maybe
                                    Debug.chat("Attack Task conflict with other movement tasks !!!");
                                    return false;
                                }
                                ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                                if (distancePassAttack) {
                                    ACPostTasks.addPostTransactionAction((ch) -> {
                                        attackWithCritic(player, target, criticSprint);
                                    });
                                    movementManagerEvent.context.playerStatus.restoreRotation();
                                    if (posDelta != Vec3d.ZERO) {
                                        Vec3d trueDelta =
                                                args.getPos().subtract(posDelta2); // .subtract(0, 0.2, 0);// =
                                        // args.getPos().subtract(posDelta);
                                        // Debug.chat("true move", RenderTasks.getDisplayedLocationDouble(trueDelta));
                                        // args.setPosition(posDelta);
                                        args.setPosition(posDelta);
                                        // args.move(MovementType.PLAYER, posDelta.subtract(args.getPos()));
                                        args.move(MovementType.PLAYER, trueDelta);
                                        // Debug.chat("final pos",
                                        // RenderTasks.getDisplayedLocationDouble(args.getPos()));
                                        // Debug.chat("TpReach back",
                                        // RenderTasks.getDisplayedLocationDouble(args.getPos()));
                                        posDelta = posDelta2 = Vec3d.ZERO;
                                    }
                                } else {
                                    ACPostTasks.addPostTransactionAction((ch) -> {
                                        args.swingHand(Hand.MAIN_HAND);
                                    });
                                }
                                // return do not kept
                                return false;
                            }
                        });

                // can not try, they control the packets movement
                //  mc.world.tickEntity(mc.player);
                return true;
            }
            return false;

        } else {
            return false;
        }
    }

    private boolean processIllegalAttack(Entity target) {
        var player = mc.player;
        if (player == null) return false;
        final boolean criticSprint = critic.get() && player.isSprinting();
        final double attackRange = CombatTasks.getCombatExtra().getAttackRange();
        boolean alreadyAtTarget = mc.crosshairTarget instanceof EntityHitResult entity && entity.getEntity() == target;
        // rewrite tp system
        Deque<MovTasks.MovInfo> movementStack = new ArrayDeque<>();
        Deque<MovTasks.MovInfo> shouldMoveBackStack = new ArrayDeque<>();
        Vec3d currentStartPos = mc.player.getPos();
        movementStack.addLast(MovTasks.MovInfo.createNoUpdate(mc.player.getPos()));
        shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNoUpdate(mc.player.getPos()));
        boolean alreadyInRange = alreadyAtTarget
                || target.getBoundingBox().squaredMagnitude(player.getEyePos()) < MathUtils.s2(attackRange);
        // mace hack、
        boolean useExactAttack = exactAttack.get()
                && (!alreadyInRange || CombatTasks.getPositionPredict().considerAntiShield(target));
        boolean shouldResetFallDamage = false;
        boolean currentSuccessful = true;
        boolean vanillaSuccessful = false;
        boolean exactSuccessful = false;
        // todo: add special attack logic,  special attack logic should before any attack logic
        // todo: remake configuration, use CustomRef
        // todo: add hand swapping logic
        if (currentSuccessful) {
            vanillaSuccessful =
                    processVanillaAttack(player, target, movementStack, shouldMoveBackStack, alreadyAtTarget);
        }
        if (currentSuccessful) {
            // exact attack
            if (useExactAttack) {
                if (processExactAttack(player, target, movementStack, shouldMoveBackStack, vanillaSuccessful)) {
                    exactSuccessful = true;
                }
            }
        }
        if (currentSuccessful) {
            if (!exactSuccessful && !vanillaSuccessful) {
                currentSuccessful &=
                        processCommonTpAttack(player, target, movementStack, shouldMoveBackStack, alreadyAtTarget);
            }
        }
        boolean maceAttack = false;
        if (currentSuccessful) {
            if (processMaceAttack(player, target, movementStack, shouldMoveBackStack)) {
                maceAttack = true;
                int maceThreshold = (useExactAttack ? 100 : 140);
                if (maceHeight.get() > maceThreshold) {
                    Debug.chat(Text.literal("[Attack Bot] 当前参数中,不建议将MaceHack范围设置在%d以上!".formatted(maceThreshold)));
                }
            }
        }

        // attacking creative player with mace at same height will cause falldamage calculate(caused by the shit code
        // below: we should resetHeight even if backStack.size() = 1
        //            Vec3d lastlyPos = movementStack.peekLast().vec3d();
        // final pos lies in attack range
        // remove final pos check because already checked
        if (currentSuccessful) {
            // start execute
            var iter = movementStack.iterator();
            Preconditions.checkArgument(iter.hasNext());
            Vec3d vec3d1 = iter.next().vec3d();
            MovTasks.MovingContext movingContext = MovTasks.MovingContext.create(vec3d1);
            List<MovTasks.MovInfo> moveInfos = new ArrayList<>();
            iter.forEachRemaining(moveInfos::add);
            shouldMoveBackStack.removeFirst();
            int movingToBundleCnt = moveInfos.size();
            moveInfos.addAll(shouldMoveBackStack);
            //                MovTasks.scheduleFarawayMoveInternal(moveInfos, false, movingContext, false);
            // attack
            List<MovTasks.StepActionBundle> actionBundles =
                    MovTasks.createMovingPacketsForMovSequence(movingContext, moveInfos, true, false);
            for (int i = 0; i < movingToBundleCnt; ++i) {
                actionBundles.get(i).run();
            }
            // processDuplicateAttack(player, target, moveInfos, movingContext, maceAttack);
            attackWithCritic(player, target, criticSprint);
            for (int i = movingToBundleCnt; i < actionBundles.size(); ++i) {
                if (actionBundles.get(i).success) {
                    actionBundles.get(i).run();

                } else {
                    List<MovTasks.MovInfo> leftTasks = moveInfos.subList(i, moveInfos.size());
                    Tasks.scheduleDelayed(
                            () -> {
                                MovTasks.scheduleFarawayMoveInternal(leftTasks, false, movingContext.resetTick(), true);
                            },
                            1);
                    break;
                }
            }
            // already at first, remove duplicate stack
            //                Debug.info(movementStack);
            //                Debug.info(shouldMoveBackStack);
            //                var inviter = shouldMoveBackStack.stream().toList();
            //                MovTasks.scheduleFarawayMoveInternal(inviter, true, movingContext,
            //                    //calculate nofall down there in this argument, no need to consider
            //                    false
            //                );

            // force resync position to origin
            if (!shouldMoveBackStack.isEmpty() || !movementStack.isEmpty()) {
                mc.player.setPosition(currentStartPos);
                // feature
                MovTasks.setupAutoResync();
            }
            // check fall damage
            List<MovTasks.MovInfo> movementList = Streams.concat(movementStack.stream(), shouldMoveBackStack.stream())
                    .toList();
            //                Debug.info(movementList);
            //                Debug.info(movementList.size());
            int size = movementList.size();

            if (size > 1) {
                double maxY = Integer.MIN_VALUE;
                double minY = Integer.MAX_VALUE;
                for (var i = 0; i < size - 1; ++i) {
                    maxY = Math.max(maxY, movementList.get(i).vec3d().y);
                    minY = Math.min(minY, movementList.get(i).vec3d().y);
                }
                // calculate max deltaY
                if (Math.abs(maxY - minY) > player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) - 1) {
                    ClientPlayerAccess.of((ClientPlayerEntity) player).setForceNoFall(true);
                    // in case that resync packet cause OnGround falldamage
                    player.setOnGround(false);
                }
            }

            //  shouldResetFallDamage = size >= 2 && movementList.get(size - 1).vec3d().y < movementList.get(size -
            // 2).vec3d().y;
            // - mc.player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE);
            // falldistance will sum up if movement is down,

            // if(shouldResetFallDamage){
            // let noFall functions make fall judgement
            //                //do not consume fall damage when resync if any custom tp is applied
            //                if(size > 1){
            //
            //                }
            // }
        }

        // next, can continue
        return false;
    }

    private static int shieldExceptionspam = 0;

    private boolean processMaceAttack(
            PlayerEntity player,
            Entity target,
            Deque<MovTasks.MovInfo> movementStack,
            Deque<MovTasks.MovInfo> shouldMoveBackStack) {
        //        if(maceHack.get() > 0.0D && player.getMainHandStack().getItem() instanceof MaceItem mace){
        //            //dupe fall distance
        //            double maxMace = maceHack.get();
        //            player.setOnGround(false);
        //            double deltaY = Math.max(target.getY() - mc.player.getY(),0);
        //            //error: down search returns negative value
        //            double height = MovTasks.searchFirstNoCollisionSpaceYHeight(mc.player.getPos().add(0, maxMace, 0),
        // 0, maxMace - 2 - deltaY, false);
        //
        //            double maceHeightMultiplier = maxMace + height;
        //            //attack space
        //            double minAvailableHeight = MovTasks.searchFirstNoCollisionSpaceYHeight(mc.player.getPos(), deltaY
        // , maxMace, true);
        //
        //            if(maceHeightMultiplier - minAvailableHeight > 1.5){
        //                Debug.chat(Text.literal("Mace Attack Simulation: simulate height %.2f, target height:
        // %.2f".formatted(maceHeightMultiplier, minAvailableHeight)).formatted(Formatting.GREEN));
        //                Vec3d top = movementStack.peekLast().vec3d();
        //                movementStack.addLast(MovTasks.MovInfo.createNoUpdate( top.add(0, maceHeightMultiplier,0)));
        //                movementStack.addLast(MovTasks.MovInfo.createNoUpdate(top.add(0, minAvailableHeight,0)));
        //                shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNoUpdate(top.add(0, minAvailableHeight,
        // 0)));
        //            }
        //
        //        }
        if (canUseMaceTp() && player.getMainHandStack().getItem() instanceof MaceItem mace) {
            double maxMace = maceHeight.get();
            player.setOnGround(false);
            Vec3d playerPos = movementStack.peekLast().vec3d();
            // do not mace attack into water, water will reset fall distance
            if (mc.world.getBlockState(BlockPos.ofFloored(playerPos)).getBlock() == Blocks.WATER) {
                Debug.chat(Text.literal("[Attack Bot] 目标攻击位置位于水中,无法执行MaceAttack!"));
                return false;
            }
            double deltaY = target.getY() - playerPos.y;
            // error: down search returns negative value
            double height = MovTasks.searchFirstNoCollisionSpaceYHeight(
                    playerPos.add(0, maxMace, 0), 0, maxMace - 2 - deltaY, false);
            // +height
            double maceHeightMultiplier = maxMace + height;
            // attack space
            // we assume that target is in attack range centered playerPos
            // we don't need to search for the 'minAvailableHeight‘
            // the height is 0.0D
            double minAvailableHeight = 0.0D;
            // MovTasks.searchFirstNoCollisionSpaceYHeight(playerPos, deltaY , maxMace, true);
            // moving height towards enermy only cause the distance be smaller
            //  moving height towards enermy causes collision with shulker
            // maybe we should delete height redirect
            //
            if (maceHeightMultiplier - minAvailableHeight > 2.0) {
                Debug.chat(Text.literal("[Attack Bot] Mace Attack Simulation: simulate height %.2f"
                                .formatted(maceHeightMultiplier))
                        .formatted(Formatting.GREEN));
                movementStack.addLast(MovTasks.MovInfo.createNoUpdate(playerPos.add(0, maceHeightMultiplier, 0)));
                // Debug.info("add", playerPos.add(0, maceHeightMultiplier,0));
                movementStack.addLast(MovTasks.MovInfo.createNoUpdate(playerPos.add(0, minAvailableHeight, 0)));
                // Debug.info("add", playerPos);
                if (Math.abs(minAvailableHeight) > 1E-7) {
                    shouldMoveBackStack.addFirst(
                            MovTasks.MovInfo.createNoUpdate(playerPos.add(0, minAvailableHeight, 0)));
                }
                return true;
            }
        }
        return false;
    }

    private static boolean processVanillaAttack(
            PlayerEntity player,
            Entity target,
            Deque<MovTasks.MovInfo> movementStack,
            Deque<MovTasks.MovInfo> shouldMoveBackStack,
            boolean alreadAtTarget) {
        Vec3d top = movementStack.peekLast().vec3d();
        if (alreadAtTarget) {
            return true;
        } else if (target.getBoundingBox().squaredMagnitude(top.add(0, mc.player.getStandingEyeHeight(), 0))
                <= MathUtils.s2(CombatTasks.getCombatExtra().getAttackRange())) {
            return true;
        } else return false;
    }

    private boolean processExactAttack(
            PlayerEntity player,
            Entity target,
            Deque<MovTasks.MovInfo> movementStack,
            Deque<MovTasks.MovInfo> shouldMoveBackStack,
            boolean vanillaSuccess) {
        // how to manage exact attack and mace hack
        // fixed : can not tp to shulker inside
        // should teleport the player to the pos of target entity
        PositionPredict positionPredict = CombatTasks.getPositionPredict();
        if (vanillaSuccess) {
            if (!positionPredict.considerAntiShield(target)) {
                return true;
            }
        }
        double range = getTpSelectRange();
        Vec3d current = player.getPos();
        // feat : teleporting position should met the need of antishield
        Vec3d targetPos = positionPredict.getExactAttackPosition(target);

        if (targetPos != null) {
            // common atttack?
            if (RenderTasks.DEBUG_RENDER_COMBAT)
                RenderTasks.drawBox(player.dimensions.getBoxAt(targetPos), 150, Color.GREEN);
            List<Vec3d> tpSequence = MovTasks.generateTpSequence(current, targetPos, false, 1.5 * range, true);
            List<Vec3d> tpSequenceBack = MovTasks.generateTpSequence(targetPos, current, false, 1.5 * range, true);
            if ((tpSequence.size() == 2 || tpSequence.size() == 4)
                    && (tpSequenceBack.size() == 2 || tpSequenceBack.size() == 4)) {
                // correct tp sequence
                // try compact mace hack
                Vec3d lastly;
                if (tpSequence.size() == 2) {
                    // can directly tp
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));

                } else {
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(2)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(3)));
                    lastly = tpSequence.get(3);
                }
                //  Debug.info(movementStack);
                int size = tpSequenceBack.size();

                for (int i = size - 2; i >= 0; --i) {
                    shouldMoveBackStack.addFirst(MovTasks.MovInfo.create(tpSequenceBack.get(i)));
                }
                //                if(maceHack.get() > 80){
                //                    Debug.chat(Text.literal("[Attack Bot] 在精确攻击模式下,不建议将MaceHack设置在80以上!"));
                //                }

                // shouldMoveBackStack.addFirst(MovTasks.MovInfo.create(tpSequenceBack.get(0).add(0, 9E-8,0)));
                // Debug.info(shouldMoveBackStack);
                return true;
            } else {
                Debug.chat("[Attack Bot] Exact Attack failed, fall back to common mode");
            }
        }
        return vanillaSuccess;
    }

    private boolean processCommonTpAttack(
            PlayerEntity player,
            Entity target,
            Deque<MovTasks.MovInfo> movementStack,
            Deque<MovTasks.MovInfo> shouldMoveBackStack,
            boolean alreadyAtTarget) {
        final Vec3d vec3d = movementStack.peekLast().vec3d();
        double commonAttackRange = CombatTasks.getCombatExtra().getAttackRange();
        if (alreadyAtTarget) {
            // pass
            return true;
        }
        // todo: get this better

        else if (canUseTp()
                && target.getBoundingBox().squaredMagnitude(vec3d.add(0, mc.player.getStandingEyeHeight(), 0))
                        > MathUtils.s2(commonAttackRange)) {

            List<Vec3d> sequence =
                    MovTasks.tpAttackSearch(vec3d, target.getBoundingBox(), commonAttackRange - 0.25, 135, 1);
            //            if(!sequence.isEmpty() && RenderTasks.DEBUG_RENDER_COLLISION){
            //                Vec3d vec3d1 = sequence.get(sequence.size() -1);
            //                RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(vec3d1.add(new
            // Vec3d(-0.5, 0, -0.5)), vec3d1.add(new Vec3d(0.5, 2, 0.5)), 16));
            //            }

            if (!sequence.isEmpty()
                    && target.getBoundingBox().squaredMagnitude(sequence.get(sequence.size() - 1))
                            < MathUtils.s2(commonAttackRange)) {
                for (var vec : sequence) {
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(vec));

                    shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNotOnGround(vec));
                }
                //                if(tpAttackRange.get() >= 135){
                //                    Debug.chat(Text.literal("[Attack Bot] 不建议将tpAttack范围设置在135以上!"));
                //                }
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    public static boolean passCriticalPredicate(PlayerEntity player) {
        boolean bl3 = player.getAttackCooldownProgress(0.5f) > 0.9f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle();
        bl3 = bl3 && !player.isSprinting();
        return bl3;
    }

    @Deprecated
    private static Vec3d calculateBestReachPos(Vec3d from, Box target) {
        return from;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context().getValue();
        switch (preset) {
            case HACKING, VANILLA -> {
                legalMode.set(false);
                if (tpRange.get() < 0) {
                    tpRange.set(-tpRange.get());
                }
            }
            default -> {
                legalMode.set(true);
                if (tpRange.get() > 0) {
                    tpRange.set(-tpRange.get());
                }
            }
        }
    }
}
