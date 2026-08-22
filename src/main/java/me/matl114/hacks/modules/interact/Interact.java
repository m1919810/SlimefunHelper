package me.matl114.hacks.modules.interact;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import java.util.*;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.combat.CombatExtra;
import me.matl114.hacks.modules.combat.PositionPredict;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class Interact extends BaseModule {
    public static Interact INSTANCE;

    public Interact() {
        super("Interact");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final ModulePath root = makePath(Configs.INTERACT_CONFIG, "interact-arua.interact");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef enableEntity =
            builder(root.add("enable-entity"), Boolean.class).defaultValue(true).build();

    public final FlagRef enableBlock =
            builder(root.add("enable-block"), Boolean.class).defaultValue(false).build();

    public final EnumRef<Configs.LegalTargetingMode> entityMode = builder(
                    root.add("entity-mode"), Configs.LegalTargetingMode.class)
            .defaultValue(Configs.LegalTargetingMode.NONE)
            .build();

    public final EnumRef<Configs.LegalInteractMode> blockMode = builder(
                    root.add("block-mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

    public final NBTRef<EntityTypeRegex> interactWhiteList = builder(
                    root.add("entity-whitelist"), EntityTypeRegex.class)
            .defaultValue(new EntityTypeRegex(new Regex("^(villager|chest_minecart)$")))
            .build();

    public final FlagRef ignoreBlockPlace =
            flagBuilder(root.add("ignore-block-place")).build();

    public final FlagRef ignoreUseItem =
            flagBuilder(root.add("ignore-use-item")).build();

    public final NBTRef<EntrySet<Item>> useItemBlackList = builder(
                    root.add("use-item-black-list"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(new Regex("^()$"), Registries.ITEM))
            .build();

    public final FlagRef entityPriority = builder(root.add("entity-priority"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef entityOnlyInteractable = builder(root.add("only-interact-interactable-entity"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<EntrySet<Block>> blockWhiteList = builder(
                    root.add("block-whitelist"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(.*chest|shulker.*)$"), Registries.BLOCK))
            .build();

    public final FlagRef blockOnlyHandNotPlace = builder(
                    root.add("interact-block-only-when-hand-not-block"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef blockOnlyInteractable = builder(root.add("only-interact-interactable-block"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<OptionalPrimitive<Double>> tpInteract = builder(
                    root.add("tp-interact-range"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 10.0D))
            .build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    public final FlagRef renderAttackTarget =
            flagBuilder(root.add("render-target")).build();

    public final NBTRef<WrapColor> renderAttackColor = builder(root.add("render-target-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.RED)))
            .build();

    HitResult currentInteractTarget = null;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
        registerListener(Listener.getItemUseAction(), this::onInteract);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public boolean canUseTp() {
        return tpInteract.get().test(s -> s > 1E-6);
    }

    public boolean canInteract(Entity entity, double range) {
        if (!TargetSelector.INSTANCE.isTargetInRange(entity, range, 0)) {
            return false;
        }
        if (entity.isAlive() && !entity.isSpectator() && interactWhiteList.get().test(entity.getType())) {
            if (entityOnlyInteractable.get()
                    && !InteractUtils.isInteractAcceptable(
                            mc.world, mc.player, entity, mc.player.getStackInHand(Hand.MAIN_HAND))) {
                return false;
            }
            return true;
        } else return false;
    }

    public Entity searchInteractableEntity() {
        double search = CombatExtra.INSTANCE.getAttackRange();
        if (canUseTp()) {
            search += tpInteract.get().getValue();
        }
        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (canInteract(entity, search)) {
                return entity;
            }
        }

        final double searchRange = search;
        return TargetSelector.INSTANCE.searchAttack(search, true, 0, (entity) -> {
            return canInteract(entity, searchRange);
        });
    }

    public boolean canInteract(BlockPos bp, double range) {
        BlockState state = mc.world.getBlockState(bp);
        if (!state.isAir() && !state.isLiquid() && blockWhiteList.get().test(state.getBlock())) {
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), bp, range)) {
                return false;
            }
            if (blockOnlyInteractable.get()
                    && !InteractUtils.isInteractAcceptable(
                            mc.world, mc.player, bp, state, mc.player.getStackInHand(Hand.MAIN_HAND))) {
                return false;
            }
            if (blockOnlyHandNotPlace.get()
                    && mc.player.getStackInHand(Hand.MAIN_HAND).getItem() instanceof BlockItem) {
                return false;
            }
            return true;
        }
        return false;
    }

    public BlockPos searchInteractableBlock() {
        double search = InteractExtra.INSTANCE.getBlockReachDistance();
        if (canUseTp()) {
            search += tpInteract.get().getValue();
        }
        Vec3d playerEye = mc.player.getEyePos();
        Vec3d rot = mc.player.getRotationVector();
        Vec3d endPos = playerEye.add(rot.normalize().multiply(search));
        for (var bp : RaycastUtils.createRaycastBlockPoses(playerEye, endPos)) {
            if (canInteract(bp, search)) {
                return bp;
            }
        }
        BlockPos playerPos = mc.player.getSteppingPos().add(0, 1, 0);
        for (var bd : InteractExtra.INSTANCE.getBlocksAround()) {
            BlockPos testPos = playerPos.add(bd);
            if (canInteract(testPos, search)) {
                return testPos;
            }
        }
        return null;
    }

    public void onPreTick(Event<ClientPlayerEntity> event) {
        currentInteractTarget = null;
        if (enable.get()) {
            HitResult currentCrosshairTarget = mc.crosshairTarget;
            // hold use
            if (!ignoreUseItem.get()) {
                ItemStack stack = mc.player.getStackInHand(Hand.MAIN_HAND);
                if (!useItemBlackList.get().test(stack.getItem())
                        && InteractUtils.isInteractAcceptable(mc.world, mc.player, stack)) {
                    currentInteractTarget = null;
                    return;
                }
            }
            if (currentCrosshairTarget.getType() == HitResult.Type.BLOCK) {
                if (!ignoreBlockPlace.get()
                        && mc.player.getStackInHand(Hand.MAIN_HAND).getItem() instanceof BlockItem bl) {
                    currentInteractTarget = currentCrosshairTarget;
                } else {
                    BlockPos pos = ((BlockHitResult) currentCrosshairTarget).getBlockPos();
                    BlockState state = mc.world.getBlockState(pos);
                    if (InteractUtils.isInteractAcceptable(
                            mc.world, mc.player, pos, state, mc.player.getStackInHand(Hand.MAIN_HAND))) {
                        currentInteractTarget = currentCrosshairTarget;
                    }
                }
            } else if (currentCrosshairTarget.getType() == HitResult.Type.ENTITY) {
                Entity target = ((EntityHitResult) currentCrosshairTarget).getEntity();
                if (InteractUtils.isInteractAcceptable(
                        mc.world, mc.player, target, mc.player.getStackInHand(Hand.MAIN_HAND))) {
                    currentInteractTarget = currentCrosshairTarget;
                }
            }
            if (currentInteractTarget != null) {
                return;
            }
            if (entityPriority.get()) {
                Entity targetEntity = enableEntity.get() ? searchInteractableEntity() : null;
                if (targetEntity != null) {
                    currentInteractTarget = new EntityHitResult(targetEntity);
                } else {
                    BlockPos pos = enableBlock.get() ? searchInteractableBlock() : null;
                    if (pos != null) {
                        currentInteractTarget = RaycastUtils.createHitResult(pos, mc.player.getEyePos());
                    }
                }
            } else {
                BlockPos pos = enableBlock.get() ? searchInteractableBlock() : null;
                if (pos != null) {
                    currentInteractTarget = RaycastUtils.createHitResult(pos, mc.player.getEyePos());
                } else {
                    Entity targetEntity = enableEntity.get() ? searchInteractableEntity() : null;
                    if (targetEntity != null) {
                        currentInteractTarget = new EntityHitResult(targetEntity);
                    }
                }
            }
        }
    }

    public void onInteract(Event<HitResult> hitResult) {
        if (hitResult.isCancelled()) return;
        if (enable.get()) {
            Hand hand = hitResult.getArgs(0);
            if (hand == Hand.MAIN_HAND) {
                PlayerEntity player = mc.player;
                if (player != null && mc.world != null) {
                    if (currentInteractTarget != null && currentInteractTarget != mc.crosshairTarget) {
                        if (currentInteractTarget instanceof EntityHitResult entity
                                && entity.getType() == HitResult.Type.ENTITY
                                && interactEntity(entity.getEntity())) {
                            hitResult.cancel();
                        } else if (currentInteractTarget instanceof BlockHitResult hit
                                && hit.getType() == HitResult.Type.BLOCK
                                && interactBlock(hit)) {
                            hitResult.cancel();
                        }
                    }
                }
            }
        }
    }

    public boolean interactEntity(Entity target) {
        if (entityMode.get().isLegal()) {
            return processLegalInteract(target);
        } else {
            return processIllegalInteract(target);
        }
    }

    private boolean processLegalInteract(Entity target) {
        var player = mc.player;
        if (player == null) return false;
        // todo: pitch yaw fix;
        // do not add mace or tp attack in legal mode

        // mace attack, use item attack, need, delay
        return switch (entityMode.get()) {
            case DELAY_MOVEMENT -> processDelayMovementInteract(target);
            case LEGACY_SLIENT_ROT -> processLegacySnapInteract(target);
            case NONE -> {
                InteractionTasks.interactEntity(mc.player, target, Hand.MAIN_HAND, swingHand.get());
                yield true;
            }
        };
    }

    private final Random attackOffsetRand = new Random();

    private boolean processDelayMovementInteract(Entity target) {
        final double attackRange = CombatExtra.INSTANCE.getAttackAtTargetRange(target);
        // remove crosshairTarget judge, use
        boolean canDirectlyHit = RaycastUtils.canRaycastHit(
                mc.player,
                PlayerStateManager.INSTANCE.lastPitch,
                PlayerStateManager.INSTANCE.lastYaw,
                target,
                attackRange);
        if (canDirectlyHit) {
            // already actioned in caller
            // may not actioned in caller, fix it
            InteractionTasks.interactEntity(mc.player, target, Hand.MAIN_HAND, swingHand.get());
            return true;
        } else {
            // 提前转向 下个tick就有正确的velocity了
            ClientPlayerAccess.of(mc.player)
                    .getLegalMovementManager()
                    .addMovementModifier(new LegalMovementManager.MovementModifier() {
                        Vec3d posDelta = Vec3d.ZERO;
                        Vec3d posDelta2 = Vec3d.ZERO;
                        Vec3d velocity;
                        Vec3d lookVec;
                        boolean distancePassAttack = true;
                        boolean runThisTick = true;
                        int max = 10;

                        @Override
                        public int priority() {
                            return PRIORITY_LOW;
                        }

                        @Override
                        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                            runThisTick = true;
                            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;

                            // step back our position
                            velocity = args.getVelocity();
                            Vec3d predictedEyePos = TargetSelector.INSTANCE.getBestAttackEyePos(
                                    mc.player.getPos(), target.getBoundingBox()); // mc.player.getEyePos();
                            // revert shit
                            if (args.isFallFlying()) {
                                // fix targeting in big velocity
                                predictedEyePos = predictedEyePos.add(
                                        mc.player.getVelocity()); // predictedEyePos.add(mc.player.getVelocity());
                            }
                            Vec3d vec3d = args.getPos();
                            if (tpInteract.get().positive()
                                    && target.getBoundingBox().squaredMagnitude(predictedEyePos)
                                            > MathUtils.s2(attackRange)) {
                                // need tp attack
                                // how?
                                // 平面突袭？

                                Vec3d vec3d1 =
                                        MovTasks.tpAttackSearch(vec3d, target.getBoundingBox(), attackRange, 9.9, 1)
                                                .stream()
                                                .findFirst()
                                                .orElse(null);
                                // calculateBestReachPos(vec3d, target.getBoundingBox());
                                if (vec3d1 != null && vec3d1.squaredDistanceTo(vec3d) > 1E-7) {
                                    posDelta = vec3d; // vec3d1.subtract(vec3d);
                                    posDelta2 = vec3d1;
                                    args.setPosition(vec3d1.add(0, 9E-8, 0));
                                    predictedEyePos = TargetSelector.INSTANCE.getBestAttackEyePos(
                                            args.getPos(), target.getBoundingBox());
                                }
                                // backoff
                                if (!TargetSelector.INSTANCE.isWithinAttackRange(
                                        args.getPos(), target.getBoundingBox(), attackRange)) {
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
                                double percentage = attackOffsetRand.nextDouble(0.8d, 1.00d);
                                Vec3d attackOffsetted =
                                        targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
                                attackOffsetted.add(
                                        attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                        attackOffsetRand.nextDouble(-0.05d, 0.05d),
                                        attackOffsetRand.nextDouble(-0.05d, 0.05d));
                                Vec3d cacheDirection = attackOffsetted
                                        .subtract(predictedEyePos)
                                        .normalize();
                                movementManagerEvent.context.pushImportantRotation(true, true);
                                PlayerStateManager.setPlayerRotationSafe(args, cacheDirection);
                                if (RenderTasks.DEBUG_RENDER_COMBAT) {
                                    RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                                            RenderTasks.DEBUG_TICK,
                                            new RenderTasks.LineObject(predictedEyePos, cacheDirection)));
                                }
                                lookVec = cacheDirection;

                                movementManagerEvent.context.markForResetRot();
                            }

                            // restore velocity after collide
                            args.setVelocity(velocity);
                        }

                        @Override
                        public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
                            if (!runThisTick) return;
                            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                            // there is no need for fall flying player to correct this
                            if (lookVec != null && !args.isFallFlying()) {
                                // rewrite input to fit lookVec
                                PlayerInputUtils.of(mc.player).sprint(false).applyInput(mc.player);
                                movementManagerEvent.context.markForMoveFix();
                            }
                        }

                        @Override
                        public boolean postModify(
                                Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                            if (!runThisTick) {
                                return max >= 0;
                            }
                            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                            if (distancePassAttack) {
                                postInteract(args, target);
                                if (posDelta != Vec3d.ZERO) {
                                    Vec3d trueDelta = args.getPos().subtract(posDelta2); // .subtract(0, 0.2, 0);// =
                                    args.setPosition(posDelta);
                                    // args.move(MovementType.PLAYER, posDelta.subtract(args.getPos()));
                                    args.move(MovementType.PLAYER, trueDelta);
                                    posDelta = posDelta2 = Vec3d.ZERO;
                                }
                            }
                            // return do not kept
                            return false;
                        }
                    });
            return true;
        }
    }

    private void postInteract(PlayerEntity player, Entity target) {
        // consider post
        InteractionTasks.interactEntity(mc.player, target, Hand.MAIN_HAND, swingHand.get());
    }

    private boolean processLegacySnapInteract(Entity target) {

        double attackRange = CombatTasks.getCombatExtra().getAttackAtTargetRange(target);
        boolean canDirectlyHit = RaycastUtils.canRaycastHit(
                mc.player,
                PlayerStateManager.INSTANCE.lastPitch,
                PlayerStateManager.INSTANCE.lastYaw,
                target,
                attackRange);
        if (canDirectlyHit) {
            // already actioned in caller
            // may not actioned in caller, fix it
            InteractionTasks.interactEntity(mc.player, target, Hand.MAIN_HAND, swingHand.get());
            return true;
        }
        Vec3d vec3d = mc.player.getPos();
        Vec3d predictedEyePos = TargetSelector.INSTANCE.getBestAttackEyePos(vec3d, target.getBoundingBox());
        boolean distancePassAttack =
                TargetSelector.INSTANCE.isWithinAttackRange(vec3d, mc.player.getBoundingBox(), attackRange);
        if (tpInteract.get().positive() && !distancePassAttack) {

            Vec3d vec3d1 = MovTasks.tpAttackSearch(vec3d, target.getBoundingBox(), attackRange, 9.9, 1).stream()
                    .findFirst()
                    .orElse(null);
            // calculateBestReachPos(vec3d, target.getBoundingBox());
            if (vec3d1 != null && vec3d1.squaredDistanceTo(vec3d) > 1E-7) {
                mc.player.setPosition(vec3d1.add(0, 9E-8, 0));
                predictedEyePos = TargetSelector.INSTANCE.getBestAttackEyePos(vec3d, target.getBoundingBox());
            }
            // backoff
            if (!TargetSelector.INSTANCE.isWithinAttackRange(vec3d, mc.player.getBoundingBox(), attackRange)) {
                distancePassAttack = false;
                mc.player.setPosition(vec3d);
                // skip attack
            }
        }
        // after move player, do target
        if (distancePassAttack) {

            if (mc.player.isUsingItem()) {
                mc.player.stopUsingItem();
                mc.getNetworkHandler()
                        .sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
            }
            Vec3d eyePos = target.getEyePos();
            Vec3d targetPos = target.getPos();
            double percentage = attackOffsetRand.nextDouble(0.8d, 1.00d);
            Vec3d attackOffsetted = targetPos.add(eyePos.subtract(targetPos).multiply(percentage));
            attackOffsetted.add(
                    attackOffsetRand.nextDouble(-0.05d, 0.05d),
                    attackOffsetRand.nextDouble(-0.05d, 0.05d),
                    attackOffsetRand.nextDouble(-0.05d, 0.05d));
            Vec3d cacheDirection = attackOffsetted.subtract(predictedEyePos).normalize();
            // mace
            LegacySnapRotManager.INSTANCE.snapAt(cacheDirection, false);
            InteractionTasks.interactEntity(mc.player, target, Hand.MAIN_HAND, swingHand.get());
        }
        return true;
    }
    // shit mountains copied from Attack
    private boolean processIllegalInteract(Entity target) {
        var player = mc.player;
        if (player == null) return false;
        final double attackRange = CombatTasks.getCombatExtra().getAttackRange();
        boolean alreadyAtTarget = RaycastUtils.canRaycastHit(
                mc.player, PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw, target);
        // rewrite tp system
        Deque<MovTasks.MovInfo> movementStack = new ArrayDeque<>();
        Deque<MovTasks.MovInfo> shouldMoveBackStack = new ArrayDeque<>();
        Vec3d currentStartPos = mc.player.getPos();
        movementStack.addLast(MovTasks.MovInfo.createNoUpdate(mc.player.getPos()));
        shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNoUpdate(mc.player.getPos()));
        boolean alreadyInRange = alreadyAtTarget
                || TargetSelector.INSTANCE.isWithinAttackRange(
                        player.getPos(),
                        target.getBoundingBox(),
                        attackRange); // target.getBoundingBox().squaredMagnitude(player.getEyePos()) <
        // MathUtils.s2(attackRange);
        // mace hack、
        boolean useTp = canUseTp();
        boolean useExactAttack = useTp && (!alreadyInRange);
        boolean currentSuccessful = true;
        boolean vanillaSuccessful = false;
        Vec3d top = movementStack.peekLast().vec3d();
        if (alreadyAtTarget) {
            vanillaSuccessful = true;
        } else if (target.getBoundingBox().squaredMagnitude(top.add(0, mc.player.getStandingEyeHeight(), 0))
                <= MathUtils.s2(CombatTasks.getCombatExtra().getAttackRange())) {
            vanillaSuccessful = true;
        }
        // exact attack
        if (useExactAttack) {
            if (processExactInteract(player, target, movementStack, shouldMoveBackStack, vanillaSuccessful, useTp)) {
                currentSuccessful = true;
            } else {
                currentSuccessful = false;
            }
        } else {
            currentSuccessful = vanillaSuccessful;
        }

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
            InteractionTasks.interactEntity(player, target, Hand.MAIN_HAND, swingHand.get());
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

            // force resync position to origin
            if (useTp && (!shouldMoveBackStack.isEmpty() || !movementStack.isEmpty())) {
                mc.player.setPosition(currentStartPos);
                // feature
                MovTasks.setupAutoResync();
            }
            // check fall damage
            List<MovTasks.MovInfo> movementList = Streams.concat(movementStack.stream(), shouldMoveBackStack.stream())
                    .toList();

            int size = movementList.size();

            if (size > 1) {
                double maxY = Integer.MIN_VALUE;
                double minY = Integer.MAX_VALUE;
                for (var i = 0; i < size - 1; ++i) {
                    maxY = Math.max(maxY, movementList.get(i).vec3d().y);
                    minY = Math.min(minY, movementList.get(i).vec3d().y);
                }
                // calculate max deltaY
                if (Math.abs(maxY - minY) > player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) - 1) {
                    ClientPlayerAccess.of((ClientPlayerEntity) player).setForceNoFall(true);
                    // in case that resync packet cause OnGround falldamage
                    player.setOnGround(false);
                }
            }
            return true;
        }
        // next, can continue
        return false;
    }

    private boolean processExactInteract(
            PlayerEntity player,
            Entity target,
            Deque<MovTasks.MovInfo> movementStack,
            Deque<MovTasks.MovInfo> shouldMoveBackStack,
            boolean vanillaSuccess,
            boolean useTp) {
        // how to manage exact attack and mace hack
        // fixed : can not tp to shulker insidef
        // should teleport the player to the pos of target entity
        PositionPredict positionPredict = CombatTasks.getPositionPredict();
        if (vanillaSuccess) {
            if (!positionPredict.considerAntiShield(target)) {
                return true;
            }
        }
        if (!useTp) {
            return false;
        }
        double range = CombatExtra.INSTANCE.getAttackAtTargetRange(player)
                + tpInteract.get().getValue();
        Vec3d current = player.getPos();
        // feat : teleporting position should met the need of antishield
        Vec3d targetPos = positionPredict.getExactAttackPosition(target);

        if (targetPos != null) {
            // common atttack?
            List<Vec3d> tpSequence = MovTasks.generateTpSequence(current, targetPos, false, 1.5 * range, true);
            List<Vec3d> tpSequenceBack = MovTasks.generateTpSequence(targetPos, current, false, 1.5 * range, true);
            if ((tpSequence.size() == 2 || tpSequence.size() == 4)
                    && (tpSequenceBack.size() == 2 || tpSequenceBack.size() == 4)) {
                // correct tp sequence
                // try compact mace hack

                if (tpSequence.size() == 2) {
                    // can directly tp
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));

                } else {
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(2)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(3)));
                }
                //  Debug.info(movementStack);
                int size = tpSequenceBack.size();

                for (int i = size - 2; i >= 0; --i) {
                    shouldMoveBackStack.addFirst(MovTasks.MovInfo.create(tpSequenceBack.get(i)));
                }
                return true;
            }
        }
        return vanillaSuccess;
    }

    @ApiMethod
    public boolean placeBlock(BlockPos pos) {
        FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                pos, !blockMode.get().isLegal(), !blockMode.get().isLegal());
        if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
            return interactBlock(hitResult.val());
        } else return false;
    }

    @ApiMethod
    public boolean placeBlockStrict(BlockPos pos, BlockState state) {
        FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                pos, !blockMode.get().isLegal(), !blockMode.get().isLegal());
        if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
            BlockRotate.INSTANCE.addTempStateSchematic(pos, state);
            return interactBlock(hitResult.val());
        } else return false;
    }

    @ApiMethod
    public boolean interactBlock(BlockPos pos) {
        return interactBlock(RaycastUtils.createHitResult(pos, mc.player.getEyePos()));
    }

    @ApiMethod
    public boolean interactBlock(BlockHitResult hitResult) {
        double reach = InteractExtra.INSTANCE.getBlockReachDistance();
        boolean isWithinDistance =
                InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), hitResult.getBlockPos(), reach);
        boolean canDirectlyHit = RaycastUtils.canRaycastHit(
                mc.player,
                PlayerStateManager.INSTANCE.lastPitch,
                PlayerStateManager.INSTANCE.lastYaw,
                hitResult.getBlockPos(),
                reach);
        if (canDirectlyHit) {
            InteractionTasks.interactBlock(Hand.MAIN_HAND, hitResult, swingHand.get());
            return true;
        }
        switch (blockMode.get()) {
            case NONE -> {
                if (canUseTp() && !isWithinDistance) {
                    return TpInteract.INSTANCE.tpAndInteractBlock(hitResult, Hand.MAIN_HAND, swingHand.get());
                }
                InteractionTasks.interactBlock(Hand.MAIN_HAND, hitResult, swingHand.get());
                return true;
            }
            default -> {
                if (isWithinDistance) {
                    InteractionTasks.handlePlaceMode(blockMode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
                    return true;
                }
                return false;
            }
        }
    }

    public void onRender3D(Event<MatrixStack> event) {
        if (renderAttackTarget.get() && currentInteractTarget != null) {
            float tickDelta = (Float) event.extraArgs[0];
            Box currentBox;
            if (currentInteractTarget instanceof BlockHitResult hitResult
                    && hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = hitResult.getBlockPos();
                BlockState state = mc.world.getBlockState(hitPos);
                VoxelShape shape = state.getOutlineShape(mc.world, hitPos);
                if (shape.isEmpty()) {
                    return;
                }
                currentBox = shape.getBoundingBox().offset(hitPos);
            } else if (currentInteractTarget instanceof EntityHitResult hitResult
                    && hitResult.getType() == HitResult.Type.ENTITY) {
                currentBox = RenderUtils.getLerpedBox(hitResult.getEntity(), tickDelta);
            } else {
                return;
            }
            RenderUtils.startDrawVirtual(event.context);
            try {
                float dist = (float)
                        currentBox.getCenter().subtract(mc.player.getEyePos()).length();
                float opacity = Math.min(0.6F, 0.20F + dist * 0.02F);
                RenderUtils.drawSolidBox(
                        event.context,
                        currentBox.getMinPos(),
                        currentBox.getMaxPos(),
                        ColorUtils.withAlpha(renderAttackColor.get().color(), opacity));
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        entityMode.set(Configs.LegalTargetingMode.getFromPreset(event.context.getValue()));
        blockMode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
    }
}
