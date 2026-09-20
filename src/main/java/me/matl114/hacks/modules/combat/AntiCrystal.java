package me.matl114.hacks.modules.combat;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.matl114.accessors.access.ClientAccess;
import me.matl114.accessors.access.FireworkRocketEntityAccess;
import me.matl114.accessors.access.HitResultAccess;
import me.matl114.accessors.hacks.KeyBindAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.AutoSurround;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.MiningProgressManager;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AntiCrystal extends BaseModule {
    public AntiCrystal() {
        super("AntiCrystal");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.anti-crystal");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final DoubleRef progressPercentage =
            doubleBuilder(root.add("progress-percentage")).defaultValue(0.66).build();

    public final FlagRef onlyAround = flagBuilder(root.add("only-around-self")).build();

    public final FlagRef eatingAbort = builder(root.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();

    public final EnumRef<GhostHandMode> ghostHand = builder(root.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final ModulePath fireworks = root.add("fireworks");

    public final FlagRef firework = flagBuilder(fireworks.addEnable()).build();

    public final KeyBindRef hotkey2 = toggleHotkey(fireworks.addHotkey(), new MultiKeyBind(), fireworks.addEnable())
            .build();

    public final DoubleRef eachLevelDelay = doubleBuilder(fireworks.add("each-level-effective"))
            .defaultValue(10.0D)
            .build();

    public final FlagRef onlyCeiling = builder(fireworks.add("only-ceiling"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef onlyWhenNotObsidian = builder(fireworks.add("only-no-drop"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<EntrySet<Block>> noDropBlockList = builder(
                    fireworks.add("no-drop-block-list"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(.*glass|.*leaves)"), Registries.BLOCK))
            .build();

    public final FlagRef fabricatedPlace =
            flagBuilder(fireworks.add("fabricated-place")).build();

    public final ModulePath itemFrames = root.add("item-frame");

    public final FlagRef itemFrame = flagBuilder(itemFrames.addEnable()).build();

    public final KeyBindRef hotkey3 = toggleHotkey(itemFrames.addHotkey(), new MultiKeyBind(), itemFrames.addEnable())
            .build();

    public final ModulePath bows = root.add("bow");

    public final FlagRef bow = flagBuilder(bows.addEnable()).build();

    public final KeyBindRef hotkey4 =
            toggleHotkey(bows.addHotkey(), new MultiKeyBind(), bows.addEnable()).build();

    public final IntRef ticks = intBuilder(bows.add("use-tick"))
            .validator(Configs.intHigher(3))
            .defaultValue(4)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(Listener.getPostHandleInputEvents(), this::onPostInputEvent);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitle("widget.interact.interact-block.use-argument", 0, dblank, dx, dy));
    }

    TimerExecutor useTimer = new TimerExecutor();

    private boolean checkNoEntity(BlockPos pos) {
        Box boxbox = new Box(pos).stretch(0, 1, 0);
        var entities = mc.world.getOtherEntities(null, boxbox, (en) -> {
            if (en instanceof FireworkRocketEntity firework) {
                // try escape
                if (!MathUtils.isInXZBox(boxbox, firework.getPos())) {
                    return false;
                }
                var level = firework.getStack().get(DataComponentTypes.FIREWORKS);
                if (level != null) {
                    return FireworkRocketEntityAccess.of(firework).getLiveTicks()
                            < (level.flightDuration() + 1) * eachLevelDelay.get();
                } else {
                    return true;
                }
            } else {
                return true;
            }
        });
        return entities.isEmpty();
    }

    private IndexEntry<ItemStack> findFireworks() {
        return InventoryUtils.findPlayerItem(
                s -> s.isOf(Items.FIREWORK_ROCKET), ghostHand.get().getSearchSize(false), true, false);
    }

    private IndexEntry<ItemStack> findItemFrame() {
        return InventoryUtils.findPlayerItem(
                s -> s.getItem() instanceof ItemFrameItem, ghostHand.get().getSearchSize(false), true, false);
    }

    private IndexEntry<ItemStack> findInfBow() {
        return InventoryUtils.findPlayerItem(
                s -> s.getItem() instanceof BowItem
                        && s.get(DataComponentTypes.ENCHANTMENTS) instanceof ItemEnchantmentsComponent ench
                        && ItemStackUtils.getEnchantmentLevel(ench, Enchantments.INFINITY) > 0,
                ghostHand.get().getSearchSize(false),
                true,
                false);
    }

    private boolean hasAnyArrow() {
        return InventoryUtils.findPlayerItem(s -> s.getItem() instanceof ArrowItem, true, false) != null;
    }

    Runnable bowCallback = null;
    Vec3d targetPoint;

    public void onPreInputEvent(Event<Void> event) {
        if (checkNull()) return;
        if (bowCallback != null) {
            if (mc.player.isUsingItem()) {
                if (mc.player.getActiveItem().isOf(Items.BOW)) {
                    if (mc.player.getItemUseTime() >= ticks.get()) {
                        if (ViaFabricPlusHooks.isSupportDupRot()) {
                            LegacySnapRotManager.INSTANCE.snapAt(
                                    targetPoint.subtract(mc.player.getEyePos()).normalize(), false);
                        }
                        mc.options.useKey.setPressed(false);
                        mc.interactionManager.stopUsingItem(mc.player);
                        useTimer.mark();
                        ;
                    } else {
                        mc.options.useKey.setPressed(true);
                        if (!ViaFabricPlusHooks.isSupportDupRot() && mc.player.getItemUseTime() >= ticks.get() - 1) {
                            var pitchYaw = EntityUtils.rotationToPitchYaw(
                                    targetPoint.subtract(mc.player.getEyePos()).normalize());
                            PlayerInputManager.INSTANCE.addInputModifier(PlayerInputManager.Modifier.empty(0)
                                    .withPitch(pitchYaw.x)
                                    .withYaw(pitchYaw.y));
                        }
                    }
                } else {
                    // reset usage
                    mc.options.useKey.setPressed(false);
                }
            }
        }
        if (bowCallback != null) {
            if (!mc.player.isUsingItem()) {
                KeyBindAccess.of(mc.options.useKey).resetKeyState();
                try {
                    bowCallback.run();
                    ;
                } finally {
                    bowCallback = null;
                }
            }
        }
        if (enable.get()) {

            if (eatingAbort.get() && mc.player.isUsingItem()) return;
            Set<BlockPos> pendingMines = MiningProgressManager.INSTANCE.getBreakingMap().values().stream()
                    .filter(s -> TargetSelector.INSTANCE.canAttack(s.player))
                    .filter(MiningProgressManager.BlockBreakTracker::canMine)
                    .filter(s -> s.predictBreakingProgress() > progressPercentage.get())
                    .map(MiningProgressManager.BlockBreakTracker::getBlockPos)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<BlockPos> surroundings = AutoSurround.INSTANCE.getTargetingPos();
            Set<BlockPos> needConsider;
            if (onlyAround.get()) {
                needConsider = pendingMines.stream()
                        .filter(surroundings::contains)
                        .filter(s -> {
                            return ExplosionUtils.calculateExplosionRawDamage(
                                            10,
                                            s.toBottomCenterPos(),
                                            mc.player.getBoundingBox(),
                                            ExplosionUtils.fromWorldWithOverrides(
                                                    mc.world, Map.of(s, Blocks.AIR.getDefaultState())),
                                            ExplosionUtils.EXPLOSION_RESISTENCE)
                                    > 2.0D;
                        })
                        .collect(Collectors.toSet());
            } else {
                needConsider = pendingMines.stream()
                        .filter(s -> {
                            return ExplosionUtils.calculateExplosionRawDamage(
                                            10,
                                            s.toBottomCenterPos(),
                                            mc.player.getBoundingBox(),
                                            ExplosionUtils.fromWorldWithOverrides(
                                                    mc.world, Map.of(s, Blocks.AIR.getDefaultState())),
                                            ExplosionUtils.EXPLOSION_RESISTENCE)
                                    > 2.0D;
                        })
                        .collect(Collectors.toSet());
            }
            if (firework.get()) {
                var supply = findFireworks();
                if (supply != null) {
                    for (var re : needConsider) {
                        if (!checkNoEntity(re)) continue;
                        if (SequencedActionManager.INSTANCE.isWaitingResponse(re, s -> s.isOf(Items.FIREWORK_ROCKET))
                                || SequencedActionManager.INSTANCE.isWaitingResponse(
                                        re.down(), s -> s.isOf(Items.FIREWORK_ROCKET))) {
                            continue;
                        }
                        if (onlyCeiling.get()) {
                            BlockState upState = mc.world.getBlockState(re.up());
                            if (upState.isAir()
                                    || upState.isLiquid()
                                    || upState.getCollisionShape(mc.world, re.up())
                                            .isEmpty()) {
                                continue;
                            }
                        }
                        BlockState state = mc.world.getBlockState(re);
                        if (!state.isAir()
                                && onlyWhenNotObsidian.get()
                                && noDropBlockList.get().test(state.getBlock())) {
                            continue;
                        }
                        BlockHitResult hitResult;
                        if (fabricatedPlace.get() && (!state.isAir() && !state.isLiquid())) {
                            hitResult = InteractionTasks.createHitResult(re, mc.player.getPos());
                            Vec3d hitPoint = re.toBottomCenterPos()
                                    .add(
                                            hitResult.getSide().getOffsetX() * -0.15,
                                            0,
                                            hitResult.getSide().getOffsetZ() * -0.15);
                            HitResultAccess.of(hitResult).setPos(hitPoint);
                        } else {
                            BlockHitResult hitResult1 = InteractionTasks.createHitResult(re, Direction.DOWN);
                            if ((!(!state.isAir() && !state.isLiquid()))
                                    || !InteractionTasks.checkPositionPlace(hitResult1)) {
                                BlockPos reDown = re.down();
                                BlockState downState = mc.world.getBlockState(reDown);
                                if (!downState.isAir() && !downState.isLiquid()) {
                                    BlockHitResult hitResult2 = InteractionTasks.createHitResult(reDown, Direction.UP);
                                    if (InteractionTasks.checkPositionPlace(hitResult2)) {
                                        hitResult = hitResult2;
                                    } else {
                                        hitResult = null;
                                    }
                                } else {
                                    hitResult = null;
                                }
                            } else {
                                hitResult = hitResult1;
                            }
                        }

                        if (hitResult == null) {
                            continue;
                        }
                        if (InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), hitResult.getBlockPos())
                                && InteractUtils.canInteractAndPlace(
                                        mc.player, InteractionTasks.getInteractEntry(hitResult))) {
                            Runnable runnable =
                                    InvExtra.INSTANCE.swapItemToHand(supply.index(), false, ghostHand.get());
                            if (runnable != null) {
                                Interact.INSTANCE.interactBlock(hitResult);
                                runnable.run();
                                break;
                            }
                        }
                    }
                }
            }
            if (itemFrame.get()) {
                place:
                for (var re : surroundings) {
                    if (!checkNoEntity(re)) continue;
                    BlockState state = mc.world.getBlockState(re);
                    if (!state.isAir()) continue;
                    var supply = findItemFrame();
                    if (supply != null) {
                        for (var direction : Direction.values()) {
                            BlockHitResult result =
                                    InteractionTasks.createHitResult(re.offset(direction), direction.getOpposite());
                            if (!SequencedActionManager.INSTANCE.isWaitingResponse(
                                            result.getBlockPos(), (rer) -> rer.isOf(Items.ITEM_FRAME))
                                    && InteractExtra.INSTANCE.isWithinInteractRange(
                                            mc.player.getPos(), result.getBlockPos())
                                    && InteractionTasks.checkPositionPlace(result)
                                    && InteractUtils.canInteractAndPlace(
                                            mc.player, InteractionTasks.getInteractEntry(result))) {
                                Runnable runnable =
                                        InvExtra.INSTANCE.swapItemToHand(supply.index(), false, ghostHand.get());
                                if (runnable != null) {
                                    Interact.INSTANCE.interactBlock(result);
                                    runnable.run();
                                    break place;
                                }
                            }
                        }
                    }
                }
            }
            shoot:
            if (bow.get() && bowCallback == null && useTimer.canRun(6)) {
                var supply = findInfBow();
                if (supply != null && hasAnyArrow()) {
                    Set<BlockPos> pendingMinesEarly = MiningProgressManager.INSTANCE.getBreakingMap().values().stream()
                            .filter(s -> TargetSelector.INSTANCE.canAttack(s.player))
                            .filter(s -> s.predictBreakingProgress() > Math.min(0.35, progressPercentage.get()))
                            .map(MiningProgressManager.BlockBreakTracker::getBlockPos)
                            .filter(Objects::nonNull)
                            .filter(s -> new Box(s).squaredMagnitude(mc.player.getEyePos()) < 2.25)
                            .filter(s -> {
                                return ExplosionUtils.calculateExplosionRawDamage(
                                                10,
                                                s.toBottomCenterPos(),
                                                mc.player.getBoundingBox(),
                                                ExplosionUtils.fromWorldWithOverrides(
                                                        mc.world, Map.of(s, Blocks.AIR.getDefaultState())),
                                                ExplosionUtils.EXPLOSION_RESISTENCE)
                                        > 2.0D;
                            })
                            .collect(Collectors.toSet());
                    for (var re : pendingMinesEarly) {
                        if (!checkNoEntity(re)) continue;
                        List<Vec3d> validHitPoints = Stream.of(
                                        new Vec3d(re.getX(), re.getY() + 1, re.getZ()),
                                        new Vec3d(re.getX() + 1, re.getY() + 1, re.getZ()),
                                        new Vec3d(re.getX(), re.getY() + 1, re.getZ() + 1),
                                        new Vec3d(re.getX() + 1, re.getY() + 1, re.getZ() + 1))
                                .sorted(Comparator.comparingDouble(s -> s.squaredDistanceTo(mc.player.getEyePos())))
                                .toList();
                        for (Vec3d hitPoint : validHitPoints) {
                            Vec3d eyePos = mc.player.getEyePos().subtract(0, 0.1, 0);
                            Vec3d targetDirection = hitPoint.subtract(eyePos);
                            if (!RaycastUtils.raycastHitAnyBlockOrEntity(mc.player, eyePos, eyePos.add(targetDirection))
                                    && RaycastUtils.raycastAnySolidBlock(
                                            mc.player,
                                            eyePos.add(targetDirection.multiply(0.9)),
                                            eyePos.add(targetDirection.multiply(1.1)))) {
                                Runnable cbb = InvExtra.INSTANCE.swapItemToHand(supply.index(), false, ghostHand.get());
                                if (cbb != null) {
                                    ClientAccess.of(mc).simulateUseItem(Hand.MAIN_HAND);
                                    if (mc.player.isUsingItem()
                                            && mc.player.getActiveItem().isOf(Items.BOW)
                                            && mc.player.getActiveHand() == Hand.MAIN_HAND) {
                                        mc.options.useKey.setPressed(true);
                                        bowCallback = cbb;
                                        targetPoint = hitPoint;
                                    } else {
                                        KeyBindAccess.of(mc.options.useKey).resetKeyState();
                                        cbb.run();
                                    }
                                    break shoot;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void onPostInputEvent(Event<Void> event) {
        if (checkNull()) return;
    }
}
