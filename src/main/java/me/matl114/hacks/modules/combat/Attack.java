package me.matl114.hacks.modules.combat;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import lombok.With;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.EventContainer;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.modules.task.ServerFeature;
import me.matl114.hacks.utils.*;
import me.matl114.hacks.utils.PlayerInputUtils;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.config.WrapEnum;
import me.matl114.hacks.utils.entity.EntityMovementStatus;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalTargetingMode;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.render.RenderCollector;
import me.matl114.versioned.api.VItem;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.Nullable;

public class Attack extends BaseModule {
    public static Attack INSTANCE;
    public final ModulePath attack = makePath(Configs.COMBAT_CONFIG, "att-bot");

    public Attack() {
        super("Attack");
        bindFlag(enable);
        INSTANCE = this;
    }

    //    public final FlagRef legalMode = flagBuilder(attack.add("legal-mode")).build();
    public final EnumRef<LegalTargetingMode> legalTargetingMode = builder(
                    attack.add("legal-targeting"), LegalTargetingMode.class)
            .defaultValue(LegalTargetingMode.MOVEMENT_POST)
            .build();

    public final NBTRef<OptionalPrimitive<Double>> tpRange = builder(
                    attack.add("tp-reach"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.DOUBLE_TYPE, 0.0D))
            .build();

    public final NBTRef<OptionalPrimitive<Double>> maceHeight = builder(
                    attack.add("mace-height-multiply"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 30.0d))
            .show(() -> !legalTargetingMode.get().isLegal())
            .build();

    public final FlagRef exactAttack = flagBuilder(attack.add("exact-tp"))
            .show(() -> !legalTargetingMode.get().isLegal() && tpRange.get().isPresent())
            .build();

    public final FlagRef postFix = builder(attack.add("attack-post-fix"), Boolean.class)
            .defaultValue(true)
            .show(() -> !legalTargetingMode.get().isLegal()
                    && legalTargetingMode.get().isIn(LegalTargetingMode.MOVEMENT_POST))
            .build();

    public final FlagRef autoAntiShield =
            flagBuilder(attack.add("auto-anti-shield")).build();

    public final NBTRef<OptionalPrimitive<Integer>> fastDestroyShield = builder(
                    attack.add("fast-destroy-shield"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 1))
            .build();

    public final FlagRef autoSwap = flagBuilder(attack.add("attack-inv-swap")).build();

    public final FlagRef autoSelect =
            flagBuilder(attack.add("attack-select-best-weapon")).build();
    //
    public final FlagRef autoRelease =
            flagBuilder(attack.add("auto-handle-use-when-attack")).build();

    public final NBTRef<OptionalPrimitive<Double>> autoMaceSwap = builder(
                    attack.add("mace-swap"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 6.0D))
            .build();

    public final NBTRef<OptionalPrimitive<WrapEnum<MaceMode>>> maceFixMode = builder(
                    attack.add("mace-hit-fix-mode"), OptionalPrimitive.configEnum(MaceMode.class))
            .defaultValue(
                    new OptionalPrimitive<>(false, NBTTypes.CONFIG_ENUM_TYPE.cast(), new WrapEnum<>(MaceMode.LEGIT)))
            .build();

    public final FlagRef tryCriticSprint =
            flagBuilder(attack.add("try-critical-sprint")).build();

    // todo: ghosthand mace enchantment

    public final EnumRef<GhostHandMode> ghostHand = builder(attack.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef swingHand =
            builder(attack.add("swing-hand"), Boolean.class).defaultValue(true).build();
    public final ModulePath base = attack.add("attack-redirect");

    public final FlagRef enable = flagBuilder(base.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(base.addHotkey(), new MultiKeyBind(), base.addEnable()).build();

    public final FlagRef onlyFocus = flagBuilder(base.add("only-focus")).build();

    public final FlagRef onlyFront = flagBuilder(base.add("only-front")).build();

    public final FlagRef renderAttackTarget = flagBuilder(base.add("render")).build();

    public final NBTRef<WrapColor> renderAttackColor = builder(base.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.GREEN))
            .build();

    public boolean canUseTp() {
        return !legalTargetingMode.get().isLegal() && tpRange.get().positive();
    }

    public boolean canUseMaceTp() {
        return canUseTp();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        // should be in high priority
        registerListener(Listener.getAttackAction(), this::onAttack, -999);
        registerListener(Listener.getPostGameTick(), this::updateRenderCollector);
        registerListener(RenderListener.getRender3DEvent(), this::onRenderTarget);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::handleMaceAttack);
        // After AutoEat (0) and AutoUse (pre input)
        registerListener(Listener.getPostHandleInputEvents(), this::onPostHandleInputEvents, 1);
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
        if (mc.player != null
                && mc.player.isFallFlying()
                && willUseMaceAttack()
                && shouldUseMaceFix()
                && shouldUseDelayMovementAttackMaceFix()) {
            return 1;
        }
        return 0;
    }

    private void updateRenderCollector() {
        Entity submittedTarget = currentTickAttackTarget;
        currentTickAttackTarget = null;
        targetCollector.clear();
        if (mc.player == null || !renderAttackTarget.get()) return;

        Entity target = EntityUtils.isEntityValid(submittedTarget) ? submittedTarget : null;
        if (target == null) {
            if (!enable.get()) return;
            if (mc.player.isUsingItem()) {
                if (mc.player.getActiveHand() == Hand.MAIN_HAND) return;
                if (mc.player.getActiveItem().getItem() instanceof RangedWeaponItem) return;
            }
            if (mc.crosshairTarget != null
                    && mc.crosshairTarget.getType() != HitResult.Type.MISS
                    && CombatTasks.notSuitableForAttack(mc.player.getMainHandStack())) return;

            target = CombatTasks.getTargetSelector().searchAttackEntity(getTpSelectRange(), true);
        }

        if (!EntityUtils.isEntityValid(target)) {
            return;
        }
        float dist = target.distanceTo(mc.player);
        float opacity = Math.min(0.6F, 0.10F + dist * 0.02F);
        targetCollector.submit(
                target,
                ColorUtils.withAlpha(renderAttackColor.get().color(), opacity).getRGB());
    }

    public void onRenderTarget(Event<Render3D> stackE) {
        if (!renderAttackTarget.get() || mc.player == null) return;
        RenderUtils.startDrawVirtual(stackE.context.stack());
        try {
            targetCollector.render3D(stackE.context.stack(), stackE.context.partialTicks());
        } finally {
            RenderUtils.stopDrawVirtual(stackE.context.stack());
        }
    }

    private final RenderCollector<Entity> targetCollector =
            RenderCollectors.createEntityBoxCollector(false, true, false);

    @Nullable
    public Entity currentTickAttackTarget;

    public void submitCurrentTickAttackTarget(@Nullable Entity target) {
        if (target != null) currentTickAttackTarget = target;
    }

    public List<Entity> getCurrentRangeEntities() {
        return CombatTasks.getTargetSelector().getAttackableEntities(getTpSelectRange());
    }

    public Entity getCurrentSelectTarget(boolean auto, int modePredictionTicks) {
        return CombatTasks.getTargetSelector().searchAttackEntity(getTpSelectRange(), auto, modePredictionTicks);
    }

    public Entity getCurrentSelectTarget(boolean auto, Predicate<Entity> predicate) {
        return CombatTasks.getTargetSelector()
                .searchAttackEntity(getTpSelectRange(), auto, getModePredictTicks(), predicate);
    }

    // the attack return value of whether it needs cooldown, for delayMovement attacking
    public boolean tryAttack(boolean auto) {
        if (mc.player == null) return false;
        Entity entity;
        if (onlyFocus.get()) {
            if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return false;
            entity = hit.getEntity();
            if (!CombatTasks.getTargetSelector().canAttack(entity)) return false;
        } else {
            entity = getCurrentSelectTarget(auto, getModePredictTicks());
        }
        if (entity == null) return false;
        if (onlyFront.get() && !CombatTasks.getTargetSelector().isInFront(entity)) return false;
        return attackEntity(entity, createAttackSettings());
    }

    public double getTpSelectRange() {
        return CombatTasks.getCombatExtra().getAttackRange()
                + (canUseTp() ? Math.max(0.0d, tpRange.get().getValue()) : 0.0D);
    }

    private static boolean canEntityUseShieldBlockMe(LivingEntity target, PlayerEntity player) {
        return true; // player.getEyePos().subtract(target.getEyePos()).dotProduct(target.getRotationVector()) > 0;
    }

    private static IndexEntry<ItemStack> findAntiShieldWeapon(AttackSettings settings) {
        return InventoryUtils.findPlayerItem(
                (ex) -> VItem.getInstance().isAxe(ex) && !willBreak(ex),
                settings.ghostHandMode().getSearchSize(false),
                false,
                false);
    }

    public AttackSettings createAttackSettings() {
        boolean useTp = canUseTp();
        boolean maceSwap = autoMaceSwap.get().isPresent()
                && PlayerStateManager.INSTANCE.fallDistance
                        >= autoMaceSwap.get().getValue();
        boolean invSwap = autoSwap.get() && !ServerFeature.INSTANCE.attackStrengthUpdateWithHand.get();
        boolean selectWeapon = autoSelect.get();
        boolean antiShield = autoAntiShield.get();
        boolean useAttack = autoRelease.get() && mc.player.isUsingItem();
        boolean elytraSwitch = shouldUseMaceFix()
                && shouldUseDelayMovementAttackMaceFix()
                && PlayerStateManager.INSTANCE.fallDistance > 1.5
                && willUseMaceAttack(maceSwap);
        boolean legal = legalTargetingMode.get().isLegal();
        boolean criticalSprint = tryCriticSprint.get()
                && mc.player.isSprinting()
                && !mc.player.isFallFlying()
                && DamageUtils.canDealCriticalEnvironmentSelf()
                && PlayerStateManager.INSTANCE.fallDistance > 0;
        boolean maceVClip = canUseMaceTp() && !legal;
        if (maceVClip
                && autoMaceSwap.get().isPresent()
                && maceHeight.get().getValue() >= autoMaceSwap.get().getValue()) {
            maceSwap = true;
        }
        return new AttackSettings(
                useTp,
                maceSwap,
                invSwap,
                selectWeapon,
                antiShield,
                fastDestroyShield.get().asOptional(),
                useAttack,
                elytraSwitch,
                criticalSprint,
                maceVClip,
                swingHand.get(),
                postFix.get(),
                ghostHand.get());
    }

    public static boolean shouldUseAntiShield(Entity target) {
        return shouldUseAntiShield(target, INSTANCE.createAttackSettings());
    }

    private static boolean shouldUseAntiShield(Entity target, AttackSettings settings) {
        return target instanceof LivingEntity lv
                && lv.isUsingItem()
                && lv.getActiveItem().getItem() instanceof ShieldItem sh
                && canEntityUseShieldBlockMe(lv, mc.player)
                && findAntiShieldWeapon(settings) != null;
    }

    private static double applyArmorReduceOn(ItemStack stack, double finalDamage, LivingEntity target) {
        var context = DamageUtils.createAssumeProtectionFourContext(target);
        return DamageUtils.getFinalAttackDamageAgainst(mc.player, stack, (float) finalDamage, context);
    }

    private static boolean willBreak(ItemStack stack) {
        return !stack.contains(DataComponentTypes.UNBREAKABLE)
                && stack.getMaxDamage() > 0
                && (stack.getDamage() >= stack.getMaxDamage() - 1);
    }

    @Nonnull
    public static IndexEntry<ItemStack> selectBestWeapon(AttackSettings attackSettings, @Nullable Entity target) {
        IndexEntry<ItemStack> invResult;
        if (attackSettings.antiShieldSwap()
                && shouldUseAntiShield(target)
                && (invResult = findAntiShieldWeapon(attackSettings)) != null) {
            return invResult;
        }
        boolean attributeSwap = ServerFeature.INSTANCE.attributeSwap.get();
        if (attackSettings.fastBreakShieldSwap().isPresent()
                && target instanceof LivingEntity livingTarget
                && livingTarget.isUsingItem()
                && livingTarget.getActiveItem().getItem() instanceof ShieldItem
                && isWithinShieldBlockingAngle(livingTarget, mc.player)) {
            invResult = InventoryUtils.findBestPlayerItem(
                    (stack) -> {
                        if (!VItem.getInstance().isWeapon(stack)
                                || VItem.getInstance().isAxe(stack)
                                || willBreak(stack)) {
                            return null;
                        }
                        if (attributeSwap) {
                            double damage = applyArmorReduceOn(
                                    stack,
                                    DamageUtils.getRealAttackDamage(
                                            mc.player,
                                            livingTarget,
                                            stack,
                                            PlayerStateManager.INSTANCE.fallDistance,
                                            PlayerStateManager.INSTANCE.getTicksSinceLastAttack(),
                                            true),
                                    livingTarget);
                            return damage;
                        }
                        double baseDamage = DamageUtils.getAttackDamageAttribute(mc.player, stack, false);
                        return baseDamage >= 15.0D ? baseDamage : null;
                    },
                    attackSettings.ghostHandMode().getSearchSize(false),
                    false,
                    false);
            if (invResult != null) {
                return invResult;
            }
        }
        if (attackSettings.maceSwap()
                && target instanceof LivingEntity lv
                && (invResult = InventoryUtils.findBestPlayerItem(
                                (ex) -> {
                                    if (ex.getItem() == Items.MACE) {
                                        if (willBreak(ex)) {
                                            return null;
                                        }
                                        return applyArmorReduceOn(
                                                ex,
                                                DamageUtils.getRealAttackDamage(
                                                        mc.player,
                                                        lv,
                                                        ex,
                                                        PlayerStateManager.INSTANCE.fallDistance,
                                                        PlayerStateManager.INSTANCE.getTicksSinceLastAttack(),
                                                        ServerFeature.INSTANCE.attributeSwap.get()),
                                                lv);
                                    }
                                    return null;
                                },
                                attackSettings.ghostHandMode().getSearchSize(false),
                                false,
                                false))
                        != null) {
            return invResult;
        } else if (attackSettings.invSwap()
                && target instanceof LivingEntity lv
                && (invResult = InventoryUtils.findBestPlayerItem(
                                (ex) -> {
                                    if (VItem.getInstance().isSpear(ex)) return null;
                                    if (VItem.getInstance().isWeapon(ex)) {
                                        if (willBreak(ex)) {
                                            return null;
                                        }
                                        Integer damageCost = VItem.getInstance().getAttackDurabilityCost(ex);
                                        return damageCost == null
                                                ? null
                                                : -((double) damageCost * 1E8)
                                                        + applyArmorReduceOn(
                                                                ex,
                                                                DamageUtils.getRealAttackDamage(
                                                                        mc.player,
                                                                        lv,
                                                                        ex,
                                                                        PlayerStateManager.INSTANCE.fallDistance,
                                                                        PlayerStateManager.INSTANCE
                                                                                .getTicksSinceLastAttack(),
                                                                        ServerFeature.INSTANCE.attributeSwap.get()),
                                                                lv);
                                    }
                                    return null;
                                },
                                attackSettings.ghostHandMode().getSearchSize(false),
                                false,
                                false))
                        != null) {
            return invResult;
        } else if (attackSettings.selectWeapon()
                && !mc.player.getStackInHand(Hand.MAIN_HAND).isEmpty()
                && target instanceof LivingEntity lv
                && (invResult = InventoryUtils.findBestPlayerItem(
                                (ex) -> {
                                    if (VItem.getInstance().isSpear(ex)) return null;
                                    if (ex.isOf(mc.player
                                            .getStackInHand(Hand.MAIN_HAND)
                                            .getItem())) {
                                        if (willBreak(ex)) {
                                            return null;
                                        }
                                        return applyArmorReduceOn(
                                                ex,
                                                DamageUtils.getRealAttackDamage(
                                                        mc.player,
                                                        lv,
                                                        ex,
                                                        PlayerStateManager.INSTANCE.fallDistance,
                                                        PlayerStateManager.INSTANCE.getTicksSinceLastAttack(),
                                                        ServerFeature.INSTANCE.attributeSwap.get()),
                                                lv);
                                    }
                                    return null;
                                },
                                attackSettings.ghostHandMode().getSearchSize(false),
                                false,
                                false))
                        != null) {
            return invResult;
        }
        return InventoryUtils.getSelectedItem();
    }

    public static void attackWithSettings(PlayerEntity player, Entity target, AttackSettings attackSettings) {
        PlayerInputUtils.Input input = null;
        if (player.hasVehicle()) {
            input = PlayerInputUtils.of(mc.player);
            if (input.hasWASDMovement()) {
                var re =
                        input.clone().forward(false).backward(false).left(false).right(false);
                re.sendPlayerInputAsRiding();
            } else {
                input = null;
            }
        }
        IndexEntry<ItemStack> invResult = selectBestWeapon(attackSettings, target);
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(invResult.index(), false, attackSettings.ghostHandMode());
        attackWithCritic(player, target, attackSettings.swingHand());
        Optional<Integer> fastDestroyShield = attackSettings.fastBreakShieldSwap();
        if (fastDestroyShield.isPresent()
                && DamageUtils.getAttackDamageAttribute(
                                player, invResult.val(), ServerFeature.INSTANCE.attributeSwap.get())
                        >= 15.0D
                && !VItem.getInstance().isAxe(invResult.val())
                && target instanceof LivingEntity livingTarget
                && livingTarget.isUsingItem()
                && livingTarget.getActiveItem().getItem() instanceof ShieldItem
                && isWithinShieldBlockingAngle(livingTarget, player)) {
            for (int i = 0; i < fastDestroyShield.get(); i++) {
                attackWithCritic(player, target, attackSettings.swingHand());
            }
        }
        if (callback != null) {
            callback.run();
        }
        if (input != null) {
            input.sendPlayerInputAsRiding();
        }
    }

    private static boolean isWithinShieldBlockingAngle(LivingEntity target, Entity attacker) {
        // Vanilla shield blocking uses horizontal source direction and head yaw.
        // The shield's default blocking angle is 90 degrees.
        Vec3d directionToAttacker = attacker.getPos()
                .subtract(target.getPos())
                .multiply(1.0D, 0.0D, 1.0D)
                .normalize();
        Vec3d horizontalView = Vec3d.fromPolar(0.0F, target.getHeadYaw());
        return horizontalView.dotProduct(directionToAttacker) >= 0.0D;
    }

    @ApiMethod
    public static void attackWithCritic(PlayerEntity player, Entity target, boolean swing) {
        //        if (criticSprint) {
        //            mc.getNetworkHandler()
        //                    .sendPacket(new ClientCommandC2SPacket(player,
        // ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        //        }
        mc.interactionManager.attackEntity(mc.player, target);
        if (swing) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        // we use event to handle shield predict

    }
    // return if it is a delay attack
    public boolean attackEntity(Entity target) {
        return attackEntity(target, createAttackSettings());
    }

    public boolean attackEntity(Entity target, AttackSettings settings) {
        settings = validateGlideState(settings);
        if (legalTargetingMode.get().isLegal()) {
            return processLegalAttack(target, settings);
        } else {
            return processIllegalAttack(target, settings);
        }
    }

    /** Gliding never computes critical sprint; not gliding never computes the elytra armor switch. */
    private AttackSettings validateGlideState(AttackSettings settings) {
        if (mc.player == null) return settings;
        if (mc.player.isFallFlying()) {
            return settings.criticalSprint() ? settings.withCriticalSprint(false) : settings;
        }
        return settings.elytraDelaySwitch() ? settings.withElytraDelaySwitch(false) : settings;
    }

    public boolean willUseMaceAttack() {
        return willUseMaceAttack(autoMaceSwap.get().isPresent()
                && autoMaceSwap.get().getValue() <= PlayerStateManager.INSTANCE.fallDistance);
    }

    public boolean willUseMaceAttack(boolean autoMace) {
        return mc.player.getMainHandStack().getItem() instanceof MaceItem mace
                || (autoMace
                        && InventoryUtils.findPlayerItem(
                                        (ex) -> ex.getItem() == Items.MACE,
                                        ghostHand.get().getSearchSize(false),
                                        false,
                                        false)
                                != null);
    }

    private boolean processLegalAttack(Entity target, AttackSettings settings) {
        var player = mc.player;
        if (player == null) return false;
        // todo: pitch yaw fix;
        // do not add mace or tp attack in legal mode

        // mace attack, use item attack, need, delay
        return switch (legalTargetingMode.get()) {
            case DELAY_MOVEMENT -> processDelayMovementAttack(target, settings, true);
            case MOVEMENT_POST -> processDelayMovementAttack(target, settings, false);
            case LEGACY_SLIENT_ROT -> processLegacySnapAttack(target, settings);
            case NONE -> {
                if (settings.criticalSprint() && !Criticals.canCritWithPlayerMovement()) {
                    PlayerStateManager.INSTANCE.sendSprintStatus(false, true);
                }
                attackWithSettings(mc.player, target, settings);
                yield false;
            }
        };
    }

    private boolean maySwitchSprintCriticalItem(ItemStack stack) {
        return (!ServerFeature.INSTANCE.attackStrengthUpdateWithHand.get()
                        || ItemStack.areItemsEqual(stack, mc.player.getMainHandStack()))
                && Criticals.canCritWithPlayerMovement(stack);
    }

    private boolean processDelayMovementAttack(Entity target, AttackSettings settings, boolean attackBeforeMovement) {
        ElytraExtra elytraExtra = MovTasks.getElytraExtra();
        double attackRange = CombatExtra.INSTANCE.getAttackAtTargetRange(target);
        attackBeforeMovement = attackBeforeMovement && !settings.useAttack();
        // settings may be stale on delayed retries
        settings = validateGlideState(settings);
        IndexEntry<ItemStack> bestWeapon = selectBestWeapon(settings, target);
        if (settings.elytraDelaySwitch() && !bestWeapon.val().isOf(Items.MACE)) {
            settings = settings.withElytraDelaySwitch(false);
            settings = settings.withMaceSwap(false);
        }
        boolean maySwitchSprint = settings.criticalSprint()
                && PlayerStateManager.INSTANCE.fallDistance > 0
                && PlayerStateManager.INSTANCE.lastSprint
                && maySwitchSprintCriticalItem(bestWeapon.val());
        boolean canDirectlyHit = RaycastUtils.canRaycastHit(
                mc.player,
                PlayerStateManager.INSTANCE.lastPitch,
                PlayerStateManager.INSTANCE.lastYaw,
                target.getBoundingBox(),
                attackRange);
        if (settings.isNoDelay() && canDirectlyHit && !maySwitchSprint) {
            attackWithSettings(mc.player, target, settings);
            return false;
        }

        boolean swapArmor = false;
        if (settings.elytraDelaySwitch()) {
            if (settings.useAttack() && elytraExtra.hasPendingFallFlyingReset()) {
                if (elytraExtra.isThisTickGlideMovementServerSideGlide()) {
                    // useAttack has priority: reset fall flying this post tick, then retry as a pre-movement attack.
                    AttackSettings retrySettings = settings.withUseAttack(false);
                    Tasks.scheduleDelayed(
                            () -> {
                                elytraExtra.requestManualResetFallFlying(20);
                                Tasks.scheduleDelayedPre(
                                        () -> {
                                            delayAttacking = false;
                                            processDelayMovementAttack(target, retrySettings, true);
                                        },
                                        0);
                            },
                            0);
                    delayAttacking = true;
                    releaseUsing();
                    return true;
                }
                // pending reset without server-side glide: attack after this tick's movement
                attackBeforeMovement = false;
            } else if (elytraExtra.hasPendingFallFlyingReset()) {
                // pending reset: attack before movement through the rotation request, no armor swap
                attackBeforeMovement = true;
            } else {
                // armor swap is a side effect: only perform it once the rotation request is accepted
                swapArmor = true;
                attackBeforeMovement = false;
                settings = settings.withPostFix(true);
            }
        }
        if (maySwitchSprint) {
            if (attackBeforeMovement) {
                AttackSettings newSettings = settings.withCriticalSprint(false);
                Tasks.scheduleDelayedPre(
                        () -> {
                            delayAttacking = false;
                            processDelayMovementAttack(target, newSettings, true);
                        },
                        0);
                delayAttacking = true;
                PlayerInputManager.INSTANCE.addSprintModifier(1, false, 0);
                return true;
            }
        }

        if (attackBeforeMovement
                && !TargetSelector.INSTANCE.isWithinAttackRange(mc.player.getPos(), target, attackRange)) return true;

        DelayMovementAttackTask task = new DelayMovementAttackTask(
                target, settings, attackBeforeMovement, maySwitchSprint && !attackBeforeMovement);
        // submit before any side effect; a rejected request means nothing else should happen this tick
        if (!task.submit()) {
            return true;
        }
        if (swapArmor) elytraExtra.requestManualArmorSwapAndResetFallFlying(20);
        // delayAttacking marks the move -> attack order only
        if (attackBeforeMovement) {
            attackWithSettings(mc.player, target, settings);
        } else {
            delayAttacking = true;
        }
        if (settings.useAttack()) {
            MovTasks.getNoSlowDown().setPreAttackUseTick();
            releaseUsing();
        }
        return true;
    }

    private boolean pendingReleaseUsing = false;

    // Defer the release until handleInputEvents has finished, so vanilla input cannot restart item use afterwards.
    private void releaseUsing() {
        pendingReleaseUsing = true;
    }

    private void onPostHandleInputEvents() {
        if (!pendingReleaseUsing) return;
        pendingReleaseUsing = false;
        ClientPlayerEntity player = mc.player;
        if (player == null || !player.isUsingItem()) return;
        player.stopUsingItem();
        mc.getNetworkHandler()
                .sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
    }

    private final class DelayMovementAttackTask {
        private final Entity target;
        private final AttackSettings settings;
        private final boolean attackBeforeMovement;
        private boolean finished;
        private final boolean noSprint;

        private DelayMovementAttackTask(
                Entity target,
                AttackSettings settings,
                boolean attackBeforeMovement,
                boolean noSprint) {
            this.target = target;
            this.settings = settings;
            this.attackBeforeMovement = attackBeforeMovement;
            this.noSprint = noSprint;
        }

        private boolean submit() {
            return RotationManager.INSTANCE.request(RotationManager.ofEntity(
                            () -> target, attackBeforeMovement ? 0 : 1)
                    .raycastPosition(player -> attackBeforeMovement
                            ? player.getPos()
                            : new EntityMovementStatus<>(player).clone().move0(PlayerInputUtils.of(mc.options)).pos)
                    .applyMoveFix(true)
                    .preTickTask(this::preTick)
                    .postTickTask(this::postTick));
        }

        private void preTick() {
            ClientPlayerEntity player = mc.player;
            if (!player.isFallFlying() && noSprint) {
                PlayerInputManager.INSTANCE.addSprintModifier(Integer.MAX_VALUE, false, 0);
            }
        }

        private void postTick() {
            if (!attackBeforeMovement) {
                applyPostAttack(target, settings);
            }
            finish();
        }

        private void finish() {
            if (finished) return;
            finished = true;
            Tasks.scheduleDelayedPre(() -> delayAttacking = false, 0);
        }
    }

    private void applyPostAttack(Entity target, AttackSettings settings) {
        if (settings.isPreTickAttack()) {
            // next tick pre attack
            Tasks.scheduleDelayedPre(() -> attackAfterMovement(target, settings), 0);
        } else {
            // this tick post attack
            Tasks.scheduleDelayed(() -> attackAfterMovement(target, settings), 0);
        }
    }

    private void attackAfterMovement(Entity target, AttackSettings settings) {
        ClientPlayerEntity player = mc.player;
        if (player == null || target.isRemoved()) return;
        double range = CombatExtra.INSTANCE.getAttackAtTargetRange(target);
        Box box = target.getBoundingBox();
        if (!TargetSelector.INSTANCE.isWithinAttackRange(player.getPos(), box, range)) return;
        boolean aligned = TargetSelector.INSTANCE.canRayCast(
                player.getPos(), target, PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw);
        if (!aligned) {
            if (settings.isPostTickAttack()) {
                Tasks.scheduleDelayedPre(() -> attackAfterMovement(target, settings.withPostFix(true)), 0);
                return;
            }
            if (!RotationManager.INSTANCE.request(RotationManager.ofEntity(() -> target).applyMoveFix(true))) {
                return;
            }
        }
        attackWithSettings(player, target, settings);
    }

    private boolean processLegacySnapAttack(Entity target, AttackSettings settings) {
        IndexEntry<ItemStack> bestWeapon = selectBestWeapon(settings, target);
        if (settings.elytraDelaySwitch() && !bestWeapon.val().isOf(Items.MACE)) {
            settings = settings.withElytraDelaySwitch(false);
        }
        boolean delay = false;
        if (settings.criticalSprint()
                && PlayerStateManager.INSTANCE.fallDistance > 0
                && PlayerStateManager.INSTANCE.lastSprint
                && maySwitchSprintCriticalItem(bestWeapon.val())) {
            delay = true;
            settings = settings.withCriticalSprint(false);
        }
        AttackSettings settings1 = settings;
        if (settings.elytraDelaySwitch()) {
            // Legacy snap may attack even on a server-side glide tick.
            ElytraExtra elytraExtra = MovTasks.getElytraExtra();
            if (!elytraExtra.isThisTickGlideMovementServerSideGlide() && !elytraExtra.hasPendingFallFlyingReset()) {
                elytraExtra.requestManualArmorSwapAndResetFallFlying(20);
                delayAttacking = true;
                if (delay) {
                    PlayerInputManager.INSTANCE.addSprintModifier(0, false, 0);
                }
                Tasks.scheduleRepeatedPre(
                        () -> {
                            if (!elytraExtra.isThisTickGlideMovementServerSideGlide()
                                    && !elytraExtra.hasPendingFallFlyingReset()) {
                                delayAttacking = false;
                                return true;
                            }
                            if (mc.player == null || mc.world == null || target.isRemoved()) {
                                delayAttacking = false;
                                return true;
                            }
                            double attackRange = CombatTasks.getCombatExtra().getAttackAtTargetRange(target);
                            if (!canLegacySnapAttack(target, attackRange)) {
                                if (!elytraExtra.hasPendingFallFlyingReset()) return false;
                                delayAttacking = false;
                                return true;
                            }
                            try {
                                processLegacySnapAttackNow(target, settings1);
                            } finally {
                                delayAttacking = false;
                            }
                            return true;
                        },
                        1,
                        5);
                return true;
            }
        }
        if (delay && !mc.player.isFallFlying()) {
            delayAttacking = false;
            Tasks.scheduleDelayedPre(
                    () -> {
                        processLegacySnapAttackNow(target, settings1);
                        delayAttacking = false;
                    },
                    0);
            return true;
        }
        return processLegacySnapAttackNow(target, settings1);
    }

    private boolean canLegacySnapAttack(Entity target, double attackRange) {
        return TargetSelector.INSTANCE.isWithinAttackRange(mc.player.getPos(), target, attackRange);
    }

    private boolean processLegacySnapAttackNow(Entity target, AttackSettings settings) {
        // also reached from delayed retries with stale settings
        settings = validateGlideState(settings);
        double attackRange = CombatTasks.getCombatExtra().getAttackAtTargetRange(target);
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
        Vec3d vec3d = mc.player.getPos();
        boolean distancePassAttack = TargetSelector.INSTANCE.isWithinAttackRange(vec3d, target, attackRange);
        // after move player, do target
        if (distancePassAttack) {
            if (settings.useAttack()) {
                mc.player.stopUsingItem();
                mc.getNetworkHandler()
                        .sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
            }

            Vec3d cacheDirection = TargetSelector.INSTANCE.getEntityRayCast(mc.player.getPos(), target);
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
                || TargetSelector.INSTANCE.isWithinAttackRange(
                        player.getPos(),
                        target,
                        attackRange); // target.getBoundingBox().squaredMagnitude(player.getEyePos()) <
        // MathUtils.s2(attackRange);
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
                if (maceHeight.get().getValue() > maceThreshold) {
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
            double maxMace = maceHeight.get().getValue();
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
        // fixed : can not tp to shulker insidef
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

    public MaceMode getMaceMode() {
        return maceFixMode.get().getValue().get();
    }

    public boolean shouldUseDelayMovementAttackMaceFix() {
        return maceFixMode.get().isPresent() && getMaceMode() == MaceMode.LEGIT;
    }

    public void handleMaceAttack(Event<PlayerInteractEntityC2SPacket> interactPacket) {
        if (interactPacket.isCancelled()) return;
        PlayerInteractEntityC2SPacket packet = interactPacket.context();
        if (maceFixMode.get().isPresent()
                && PlayerInteractEntityC2SPacketAccess.of(packet).isAttack()
                && mc.player.getMainHandStack().getItem() instanceof MaceItem
                && mc.player.isFallFlying()
                && !ElytraExtra.INSTANCE.hasPendingFallFlyingReset()
                && !ElytraExtra.INSTANCE.isThisTickGlideMovementServerSideGlide()
                && shouldUseMaceFix()
                && !shouldUseDelayMovementAttackMaceFix()) {
            MaceMode mode = maceFixMode.get().getValue().get();
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            PacketManager.schedulePostScheduleCallback(interactPacket.context, () -> {
                if (mode.isIn(MaceMode.PACKET)) {
                    mc.getNetworkHandler()
                            .sendPacket(new ClientCommandC2SPacket(
                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                } else {
                    ElytraExtra.INSTANCE.requestManualResetFallFlying(20);
                }
            });
        }
    }

    public boolean shouldUseMaceFix() {
        return maceFixMode.get().isPresent() && mc.player.isFallFlying();
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        if (event.context().getValue().isGhostHandPreset())
            ghostHand.set(event.context.getValue().getGhostHandMode(ghostHand.get()));
        if (event.context().getValue().isOptionPreset()) return;
        ModulePreset preset = event.context().getValue();
        legalTargetingMode.set(LegalTargetingMode.getFromPreset(preset));
        switch (preset) {
            case HACKING, VANILLA -> {
                if (tpRange.get().getValue() < 0) {
                    tpRange.set(tpRange.get().withValue(-tpRange.get().getValue()));
                }
            }
            default -> {
                if (tpRange.get().getValue() > 0) {
                    tpRange.set(tpRange.get().withValue(-tpRange.get().getValue()));
                }
            }
        }
        switch (preset) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                maceFixMode.set(maceFixMode.get().withValue(new WrapEnum<>(MaceMode.LEGIT)));
            }
            default -> maceFixMode.set(maceFixMode.get().withValue(new WrapEnum<>(MaceMode.PACKET)));
        }
    }

    public enum MaceMode implements ConfigEnum {
        LEGIT,
        PACKET,
        PACKET_LEGIT;

        @Override
        public String getConfigEnumType() {
            return "attack_mace_fix_mode";
        }
    }

    @With
    public static record AttackSettings(
            boolean useTp,
            boolean maceSwap,
            boolean invSwap,
            boolean selectWeapon,
            boolean antiShieldSwap,
            Optional<Integer> fastBreakShieldSwap,
            boolean useAttack,
            boolean elytraDelaySwitch,
            boolean criticalSprint,
            boolean maceVClip,
            boolean swingHand,
            boolean postFix,
            GhostHandMode ghostHandMode) {
        public boolean isPreTickAttack() {
            return postFix;
        }

        public boolean isPostTickAttack() {
            return !isPreTickAttack();
        }

        public boolean isVanilla() {
            return !useTp && !elytraDelaySwitch && !maceVClip && !useAttack;
        }

        public boolean isNoDelay() {
            return !elytraDelaySwitch && !useAttack;
        }

        public AttackSettings withNoSwap() {
            return new AttackSettings(
                    useTp,
                    false,
                    false,
                    false,
                    false,
                    Optional.empty(),
                    useAttack,
                    elytraDelaySwitch,
                    criticalSprint,
                    maceVClip,
                    swingHand,
                    postFix,
                    ghostHandMode);
        }
    }
}
