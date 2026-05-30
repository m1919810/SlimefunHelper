package me.matl114.hacks.modules.combat;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import java.awt.*;
import java.util.*;
import java.util.List;
import lombok.With;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.ApiStatus;

public class Attack extends BaseModule {
    public final ModulePath attack = makePath(Configs.COMBAT_CONFIG, "att-bot");

    public Attack() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(attack.add("always-att")).build();

    public final KeyBindRef hotkey = moduleEntry(
                    attack.add("always-att-hotkey"), new MultiKeyBind(), attack.add("always-att"))
            .build();

    public final FlagRef legalMode = flagBuilder(attack.add("legal-mode")).build();

    public final FlagRef enableTp = flagBuilder(attack.add("tp-enable")).build();

    public final DoubleRef tpRange = doubleBuilder(attack.add("tp-reach"))
            .defaultValue(0.0D)
            .show(enableTp::get)
            .build();

    public final FlagRef enableMace =
            flagBuilder(attack.add("mace-enable")).show(() -> !legalMode.get()).build();

    public final DoubleRef maceHeight = doubleBuilder(attack.add("mace-height-multiply"))
            .defaultValue(30.0D)
            .validator(Configs.doubleRange(-200.0D, 200.0D))
            .show(() -> !legalMode.get() && enableMace.get())
            .build();

    public final FlagRef exactAttack = flagBuilder(attack.add("exact-tp"))
            .show(() -> !legalMode.get() && enableTp.get())
            .build();

    public final EnumRef<Configs.LegalTargetingMode> legalTargetingMode = builder(
                    attack.add("legal-targeting"), Configs.LegalTargetingMode.class)
            .defaultValue(Configs.LegalTargetingMode.DELAY_MOVEMENT)
            .show(legalMode::get)
            .build();

    public final FlagRef targetPredict = flagBuilder(attack.add("use-delay-movement-pos-predict"))
            .show(() -> legalMode.get() && legalTargetingMode.get().isIn(Configs.LegalTargetingMode.DELAY_MOVEMENT))
            .build();

    public final FlagRef autoAntiShield =
            flagBuilder(attack.add("auto-anti-shield")).build();

    public final FlagRef autoSwap = flagBuilder(attack.add("attack-inv-swap")).build();

    public final FlagRef autoSelect =
            flagBuilder(attack.add("attack-select-best-weapon")).build();
    //
    public final FlagRef autoRelease =
            flagBuilder(attack.add("auto-handle-use-when-attack")).build();

    @ApiStatus.Experimental
    public final FlagRef autoMaceSwap = flagBuilder(attack.add("mace-swap")).build();

    // todo: ghosthand mace enchantment

    public final FlagRef renderAttackTarget =
            flagBuilder(attack.add("render-target")).build();

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

    public boolean delayAttacking = false;

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

    public int getModePredictTicks() {
        if (targetPredict.get() && legalMode.get()) {
            switch (legalTargetingMode.get()) {
                case DELAY_MOVEMENT: {
                    if (mc.player.isFallFlying()
                            && willUseMaceAttack(autoMaceSwap.get())
                            && ElytraExtra.INSTANCE.shouldUseDelayMovementAttackMaceFix()) {
                        return 2;
                    }
                    return 1;
                }
                case LEGACY_SLIENT_ROT:
                    return 0;
                default:
                    return 0;
            }
        } else {
            return 0;
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
        Entity entity =
                CombatTasks.getTargetSelector().searchAttackEntity(getTpSelectRange(), auto, getModePredictTicks());
        if (entity != null) {
            return attackEntity(entity, createAttackSettings());
        }
        return false;
    }

    public double getTpSelectRange() {
        return CombatTasks.getCombatExtra().getAttackRange() + (canUseTp() ? Math.max(0.0d, tpRange.get()) : 0.0D);
    }

    private static boolean canEntityUseShieldBlockMe(LivingEntity target, PlayerEntity player) {
        return player.getEyePos().subtract(target.getEyePos()).dotProduct(target.getRotationVector()) > 0;
    }

    public AttackSettings createAttackSettings() {
        boolean useTp = canUseTp();
        boolean maceSwap = autoMaceSwap.get();
        boolean invSwap = autoSwap.get();
        boolean selectWeapon = autoSelect.get();
        boolean antiShield = autoAntiShield.get();
        boolean useAttack = autoRelease.get() && mc.player.isUsingItem();
        boolean elytraSwitch =
                MovTasks.getElytraExtra().shouldUseDelayMovementAttackMaceFix() && willUseMaceAttack(maceSwap);
        boolean criticalSprint = !legalMode.get() && mc.player.isSprinting();
        boolean maceVClip = canUseMaceTp() && !legalMode.get();
        return new AttackSettings(
                useTp, maceSwap, invSwap, selectWeapon, antiShield, useAttack, elytraSwitch, criticalSprint, maceVClip);
    }

    public static void attackWithSettings(PlayerEntity player, Entity target, AttackSettings attackSettings) {
        Runnable callback = null;
        IndexEntry<ItemStack> invResult;
        if (attackSettings.antiShieldSwap()
                && target instanceof LivingEntity lv
                && lv.isUsingItem()
                && lv.getActiveItem().getItem() instanceof ShieldItem sh
                && canEntityUseShieldBlockMe(lv, player)
                && (invResult = InventoryUtils.findPlayerItem(
                                (ex) -> VItem.getInstance().isAxe(ex), false, false))
                        != null) {
            callback = InvExtra.INSTANCE.swapInventoryIndexToHand(invResult.index());
        } else if (attackSettings.invSwap()
                && !VItem.getInstance().isWeapon(mc.player.getStackInHand(Hand.MAIN_HAND))
                && target instanceof LivingEntity lv
                && (invResult = InventoryUtils.findBestPlayerItem(
                                (ex) -> {
                                    if (VItem.getInstance().isWeapon(ex)) {
                                        Integer damageCost = VItem.getInstance().getAttackDurabilityCost(ex);
                                        return damageCost == null
                                                ? null
                                                : -((double) damageCost * 1E8)
                                                        + DamageUtils.getAttackDamage(player, lv, ex)
                                                                * DamageUtils.getAttackSpeed(player, ex);
                                    }
                                    return null;
                                },
                                false,
                                false))
                        != null) {
            callback = InvExtra.INSTANCE.swapInventoryIndexToHand(invResult.index());
        } else if (attackSettings.maceSwap()
                && target instanceof LivingEntity lv
                && (invResult = InventoryUtils.findBestPlayerItem(
                                (ex) -> {
                                    if (ex.getItem() == Items.MACE) {
                                        return DamageUtils.getAttackDamage(lv, ex);
                                    }
                                    return null;
                                },
                                false,
                                false))
                        != null) {
            callback = InvExtra.INSTANCE.swapInventoryIndexToHand(invResult.index());
        } else if (attackSettings.selectWeapon()
                && !mc.player.getStackInHand(Hand.MAIN_HAND).isEmpty()
                && target instanceof LivingEntity
                && (invResult = InventoryUtils.findBestPlayerItem(
                                (ex) -> {
                                    if (ex.isOf(mc.player
                                            .getStackInHand(Hand.MAIN_HAND)
                                            .getItem())) {
                                        return DamageUtils.getAttackDamage(player, target, ex)
                                                * DamageUtils.getAttackSpeed(player, ex);
                                    }
                                    return null;
                                },
                                false,
                                false))
                        != null) {
            callback = InvExtra.INSTANCE.swapInventoryIndexToHand(invResult.index());
        }
        attackWithCritic(player, target, attackSettings.criticalSprint());
        if (callback != null) {
            callback.run();
        }
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
        if (criticSprint) {
            ClientPlayerAccess.of(mc.player).resyncSprint();
        }
    }

    public boolean attackEntity(Entity target) {
        return attackEntity(target, createAttackSettings());
    }

    public boolean attackEntity(Entity target, AttackSettings settings) {
        if (legalMode.get()) {
            return processLegalAttack(target, settings);
        } else {
            return processIllegalAttack(target, settings);
        }
    }

    private boolean willUseMaceAttack(boolean autoMace) {
        return mc.player.getMainHandStack().getItem() instanceof MaceItem mace
                || (autoMace
                        && InventoryUtils.findPlayerItem((ex) -> ex.getItem() == Items.MACE, false, false) != null);
    }

    private boolean processLegalAttack(Entity target, AttackSettings settings) {
        var player = mc.player;
        if (player == null) return false;
        // todo: pitch yaw fix;
        // do not add mace or tp attack in legal mode

        // mace attack, use item attack, need, delay
        return switch (legalTargetingMode.get()) {
            case DELAY_MOVEMENT -> processDelayMovementAttack(target, settings);
            case LEGACY_SLIENT_ROT -> processLegacySnapAttack(target, settings);
        };
    }

    private boolean processDelayMovementAttack(Entity target, AttackSettings settings) {
        ElytraExtra elytraExtra = MovTasks.getElytraExtra();
        final double attackRange = CombatTasks.getCombatExtra().getAttackRange();
        boolean useMaceAttack =
                elytraExtra.shouldUseDelayMovementAttackMaceFix() && willUseMaceAttack(settings.maceSwap());
        // remove crosshairTarget judge, use
        boolean canDirectlyHit = RaycastUtils.canRaycastHit(
                mc.player,
                PlayerStateManager.INSTANCE.lastPitch,
                PlayerStateManager.INSTANCE.lastYaw,
                target,
                attackRange);
        if (settings.isNoDelay() && canDirectlyHit) {
            // already actioned in caller
            // may not actioned in caller, fix it
            attackWithSettings(mc.player, target, settings);
            return false;
        } else {
            // 提前转向 下个tick就有正确的velocity了
            // mace not enable in legal mode
            // use Item packet should trigger by a non-empty item

            // add movement prediction position targeting option
            // check if it can pass grimac in real situation

            // TODO: fix this bug: can not pass matrix ac when on ground , check numbers and positions,
            int swapElytraSlot = -1;
            boolean armorFly = elytraExtra.isCurrentArmorGliding();
            if (useMaceAttack) {
                // do here
                if (armorFly) {
                    // disable next restart
                    elytraExtra.disableNextArmorFlyLazyElytraTransaction = 10;
                } else {
                    // common elytra fly not supported yet
                    swapElytraSlot = elytraExtra.findEmptyPlaceForElytra();
                    if (swapElytraSlot != -1) {
                        elytraExtra.switchSlotToArmor(swapElytraSlot);
                    }
                }
            }
            final int elytraSlot = swapElytraSlot;
            // testing failed,
            // see Grim' s Reach
            if (settings.useAttack()) {
                MovTasks.getNoSlowDown().setPreAttackUseTick();
            }
            boolean preAttack = false; // legalTargetingMode.get().isPreAttack();
            //                if(!useMaceAttack && preAttack) {
            //                    // pieces of shit... may not bypass shit grim after one REACH flag, I dont know
            // why??
            //                    attackWithCritic(player, target, criticSprint);
            //                }
            delayAttacking = true;
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
                            if (useMaceAttack) {
                                if (args.isFallFlying()) {
                                    max--;
                                    runThisTick = false;
                                    return;
                                }
                            }

                            // step back our position
                            velocity = args.getVelocity();
                            Vec3d predictedEyePos = mc.player.getEyePos();
                            // revert shit
                            if (useMaceAttack || args.isFallFlying()) {
                                // fix targeting in big velocity
                                predictedEyePos = predictedEyePos.add(
                                        mc.player.getVelocity()); // predictedEyePos.add(mc.player.getVelocity());
                            }
                            Vec3d vec3d = args.getPos();
                            if (tpRange.get() > 1E-7
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
                                    predictedEyePos = args.getEyePos();
                                }
                                // backoff
                                if (target.getBoundingBox().squaredMagnitude(predictedEyePos)
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
                                EntityUtils.setEntityRotationSafe(args, cacheDirection);
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
                                movementManagerEvent.context.markForMoveFix();
                            }
                        }

                        @Override
                        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                            LegalMovementManager.MovementModifier.super.applyBeforeMovementPacketModify(
                                    movementManagerEvent);
                        }

                        @Override
                        public boolean postModify(
                                Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                            if (!runThisTick) {
                                return max >= 0;
                            }
                            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                            if (distancePassAttack) {
                                if (!preAttack) {
                                    applyPostAttack(target, settings);
                                }
                                if (posDelta != Vec3d.ZERO) {
                                    Vec3d trueDelta = args.getPos().subtract(posDelta2); // .subtract(0, 0.2, 0);// =
                                    args.setPosition(posDelta);
                                    // args.move(MovementType.PLAYER, posDelta.subtract(args.getPos()));
                                    args.move(MovementType.PLAYER, trueDelta);
                                    posDelta = posDelta2 = Vec3d.ZERO;
                                }
                            }
                            if (useMaceAttack && !preAttack) {
                                if (armorFly) {
                                    elytraExtra.disableNextArmorFlyLazyElytraTransaction = 0;
                                    ACTasks.addPostTransactionAction((ch) -> {
                                        if (elytraExtra.onSwitchItemArmorFallFlying()) {
                                            mc.getNetworkHandler()
                                                    .sendPacket(new ClientCommandC2SPacket(
                                                            mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                                            elytraExtra.switchSlotToArmor(elytraExtra.thisFallFlyingIsArmorFly);
                                            elytraExtra.thisTickSwitchingIndex = -1;
                                            EntityInternalAccess.of(mc.player)
                                                    .setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, true);
                                        }
                                    });
                                } else {
                                    if (elytraSlot != -1) {
                                        ACTasks.addPostTransactionAction((ch) -> {
                                            elytraExtra.switchSlotToArmor(elytraSlot);
                                            if (!mc.player.isFallFlying())
                                                ch.sendPacket(new ClientCommandC2SPacket(
                                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                                            //
                                            // EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, true);
                                        });
                                    }
                                }
                            }
                            Tasks.scheduleDelayedPre(
                                    () -> {
                                        delayAttacking = false;
                                    },
                                    0);
                            // return do not kept
                            return false;
                        }
                    });

            // can not try, they control the packets movement
            //  mc.world.tickEntity(mc.player);
            return true;
        }
    }

    private void applyPostAttack(Entity target, AttackSettings settings) {
        //        Vec3d vec3d = mc.player.getPos();
        //        Listener.addPostPacketCatcher(new PacketCatcherImpl<>(PlayerMoveC2SPacket.class, (packetEvent -> {
        //            if(PlayerMoveC2SPacketAccess.of(packetEvent.context).getCause() ==
        // PlayerMoveC2SPacketAccess.Cause.SET_BACK){
        //                Vec3d curr = mc.player.getPos();
        //                mc.player.setPosition(vec3d);
        //                mc.player.setOnGround(true);
        //                attackWithSettings(mc.player, target, settings);
        //                LegacySnapRotManager.INSTANCE.snapAt(mc.player.getRotationVector(), true);
        //                mc.player.setPosition(curr);
        //                mc.player.setOnGround(false);
        //                return true;
        //            }
        //            return false;
        //        })));
        ACTasks.addPostTransactionAction((ch) -> {
            attackWithSettings(mc.player, target, settings);
        });
    }

    private boolean processLegacySnapAttack(Entity target, AttackSettings settings) {
        ElytraExtra elytraExtra = MovTasks.getElytraExtra();
        boolean useMaceAttack =
                false && elytraExtra.shouldUseDelayMovementAttackMaceFix() && willUseMaceAttack(settings.maceSwap());
        double attackRange = CombatTasks.getCombatExtra().getAttackRange();
        boolean canDirectlyHit = RaycastUtils.canRaycastHit(
                mc.player,
                PlayerStateManager.INSTANCE.lastPitch,
                PlayerStateManager.INSTANCE.lastYaw,
                target,
                attackRange);
        if (settings.isNoDelay() && canDirectlyHit) {
            // already actioned in caller
            // may not actioned in caller, fix it
            attackWithSettings(mc.player, target, settings);
            return false;
        }
        Vec3d predictedEyePos = mc.player.getEyePos();
        Vec3d vec3d = mc.player.getPos();
        boolean distancePassAttack =
                target.getBoundingBox().squaredMagnitude(predictedEyePos) <= MathUtils.s2(attackRange);
        if (tpRange.get() > 1E-7 && !distancePassAttack) {

            Vec3d vec3d1 = MovTasks.tpAttackSearch(vec3d, target.getBoundingBox(), attackRange, 9.9, 1).stream()
                    .findFirst()
                    .orElse(null);
            // calculateBestReachPos(vec3d, target.getBoundingBox());
            if (vec3d1 != null && vec3d1.squaredDistanceTo(vec3d) > 1E-7) {
                mc.player.setPosition(vec3d1.add(0, 9E-8, 0));
                predictedEyePos = mc.player.getEyePos();
            }
            // backoff
            if (target.getBoundingBox().squaredMagnitude(predictedEyePos) > MathUtils.s2(attackRange)) {
                distancePassAttack = false;
                mc.player.setPosition(vec3d);
                // skip attack
            }
        }
        // after move player, do target
        if (distancePassAttack) {
            boolean useItem = false;
            if (settings.useAttack()) {
                useItem = true;
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
            attackWithSettings(mc.player, target, settings);
        }

        return false;
    }

    private boolean processIllegalAttack(Entity target, AttackSettings settings) {
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
                || target.getBoundingBox().squaredMagnitude(player.getEyePos()) < MathUtils.s2(attackRange);
        // mace hack、
        boolean useExactAttack = settings.useTp()
                && exactAttack.get()
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
                if (processExactAttack(
                        player, target, movementStack, shouldMoveBackStack, vanillaSuccessful, settings)) {
                    exactSuccessful = true;
                }
            }
        }
        if (currentSuccessful) {
            if (!exactSuccessful && !vanillaSuccessful) {
                currentSuccessful &= processCommonTpAttack(
                        player, target, movementStack, shouldMoveBackStack, alreadyAtTarget, settings);
            }
        }
        boolean maceAttack = false;
        if (currentSuccessful) {
            if (processMaceAttack(player, target, movementStack, shouldMoveBackStack, settings)) {
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
            attackWithSettings(player, target, settings);
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
            if ((settings.useTp() || settings.maceVClip())
                    && (!shouldMoveBackStack.isEmpty() || !movementStack.isEmpty())) {
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
            Deque<MovTasks.MovInfo> shouldMoveBackStack,
            AttackSettings attackSettings) {
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
        if (attackSettings.maceVClip() && willUseMaceAttack(attackSettings.maceSwap())) {
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
            boolean vanillaSuccess,
            AttackSettings settings) {
        // how to manage exact attack and mace hack
        // fixed : can not tp to shulker inside
        // should teleport the player to the pos of target entity
        PositionPredict positionPredict = CombatTasks.getPositionPredict();
        if (vanillaSuccess) {
            if (!positionPredict.considerAntiShield(target)) {
                return true;
            }
        }
        if (!settings.useTp()) {
            return false;
        }
        double range = getTpSelectRange();
        Vec3d current = player.getPos();
        // feat : teleporting position should met the need of antishield
        Vec3d targetPos = positionPredict.getExactAttackPosition(target);
        // todo: add Environment check and fallback plans like positions around
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
            boolean alreadyAtTarget,
            AttackSettings settings) {
        final Vec3d vec3d = movementStack.peekLast().vec3d();
        double commonAttackRange = CombatTasks.getCombatExtra().getAttackRange();
        if (alreadyAtTarget) {
            // pass
            return true;
        }
        // todo: get this better

        else if (settings.useTp()
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
        switch (preset) {
            case AC_GRIM_LEGACY -> {
                legalTargetingMode.set(Configs.LegalTargetingMode.LEGACY_SLIENT_ROT);
            }
            default -> {
                legalTargetingMode.set(Configs.LegalTargetingMode.DELAY_MOVEMENT);
            }
        }
    }

    @With
    public static record AttackSettings(
            boolean useTp,
            boolean maceSwap,
            boolean invSwap,
            boolean selectWeapon,
            boolean antiShieldSwap,
            boolean useAttack,
            boolean elytraDelaySwitch,
            boolean criticalSprint,
            boolean maceVClip) {
        public boolean isVanilla() {
            return !useTp && !elytraDelaySwitch && !maceVClip && !useAttack;
        }

        public boolean isNoDelay() {
            return !elytraDelaySwitch && !useAttack;
        }
    }
}
