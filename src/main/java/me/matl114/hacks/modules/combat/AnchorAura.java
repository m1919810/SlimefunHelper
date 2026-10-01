package me.matl114.hacks.modules.combat;

import java.util.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.BlockBreak;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class AnchorAura extends BaseModule {
    private static final float ANCHOR_POWER = 5.0F;
    private static final int ANCHOR_SEARCH_RADIUS = 5;
    private static final double NEARBY_TARGET_SEARCH_RADIUS = 1.0D;
    private static final double PREDICT_POSITION_SWITCH_DISTANCE = 0.5D;

    public AnchorAura() {
        super("AnchorAura");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.anchor-aura");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final EnumRef<LegalInteractMode> mode = builder(root.add("mode"), LegalInteractMode.class)
            .defaultValue(LegalInteractMode.NONE)
            .build();

    public final FlagRef airplace =
            builder(root.add("air-place"), Boolean.class).defaultValue(false).build();

    public final IntRef range = intBuilder(root.add("range")).defaultValue(10).build();
    private List<Vec3i> interactRangeBlocks = new ArrayList<>();
    public final DoubleRef interactRange = doubleBuilder(root.add("interact-range"))
            .defaultValue(4.5)
            .updateListener(s -> interactRangeBlocks = MathUtils.create3DPointListAroundPlayer(s))
            .build();

    public final IntRef delay = intBuilder(root.add("delay"))
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef mul = intBuilder(root.add("multiply"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef place0TickSupply =
            flagBuilder(root.add("zero-tick-place-supply")).build();

    public final FlagRef use0TickSupply = flagBuilder(root.add("zero-tick-use")).build();

    public final FlagRef eatingAbort = builder(root.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();

    public final DoubleRef selfFinalDamageThreshold = doubleBuilder(root.add("self-final-damage-threshold"))
            .defaultValue(4.0D)
            .validator(Configs.doubleRange(0.0D, 1000.0D))
            .build();

    public final DoubleRef targetDamageThreshold = doubleBuilder(root.add("target-damage-threshold"))
            .defaultValue(16.0D)
            .validator(Configs.doubleRange(0.0D, 1000.0D))
            .build();

    public final EnumRef<GhostHandMode> ghostHand = builder(root.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef logSupply = flagBuilder(root.add("notify-supply")).build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    public final ModulePath packetMine = root.add("bridge-packet-mine");

    public final FlagRef packetMineBridge = flagBuilder(packetMine.addEnable()).build();

    public final KeyBindRef packetMineBridgeHotkey = toggleHotkey(
                    packetMine.addHotkey(), new MultiKeyBind(), packetMine.addEnable())
            .build();

    public final ModulePath blocking = root.add("auto-block");

    public final FlagRef autoBlock = flagBuilder(blocking.addEnable()).build();

    public final NBTRef<EntrySet<Item>> blockingBlock = builder(
                    blocking.add("blocking-block"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.OBSIDIAN)))
            .build();

    public final DoubleRef blockReduce = doubleBuilder(blocking.add("block-reduce"))
            .defaultValue(0.9D)
            .validator(Configs.doubleRange(0.0D, 1.0D))
            .build();

    public final FlagRef zeroTickBlock = flagBuilder(blocking.add("zero-tick")).build();

    final TimerExecutor noSupplyExecutor = new TimerExecutor();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerRespawnPoint(), this::onSwitchWorld);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvents);
        registerListener(PacketMine.getPacketMineAction(), this::onPacketMine);
        registerListener(PacketMine.getPacketMineAction().getChannel(BlockBreak.Stage.PRE), this::onPrePacketMine);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPreset);
    }

    public Map<BlockPos, AnchorCache> trackedAnchorPositions = new LinkedHashMap<>();
    public List<PlayerEntity> targetEntity = List.of();
    private AnchorPlan pendingPlace;

    public void onSwitchWorld(Event<ClientPlayerEntity> event) {
        trackedAnchorPositions.clear();
        targetEntity = List.of();
        pendingPlace = null;
    }

    public void refreshTarget() {
        targetEntity = TargetSelector.INSTANCE.getAttackableEntities(range.get()).stream()
                .filter(PlayerEntity.class::isInstance)
                .map(PlayerEntity.class::cast)
                .filter(EntityUtils::isEntityValid)
                .filter(player -> player != mc.player)
                .toList();
    }

    public boolean checkSupplies() {
        boolean hasAnchor = supplyItem(Items.RESPAWN_ANCHOR) != null;
        boolean hasGlowStone = supplyItem(Items.GLOWSTONE) != null;
        if (!hasAnchor || !hasGlowStone) {
            if (logSupply.get()) {
                noSupplyExecutor.run(100, () -> {
                    if (!hasAnchor && !hasGlowStone) {
                        logI18N(
                                "message.module.anchor-arua.no-item.double",
                                Items.RESPAWN_ANCHOR.getName(),
                                Items.GLOWSTONE.getName());
                    } else if (!hasAnchor) {
                        logI18N("message.module.anchor-arua.no-item", Items.RESPAWN_ANCHOR.getName());
                    } else {
                        logI18N("message.module.anchor-arua.no-item", Items.GLOWSTONE.getName());
                    }
                });
            }

            return false;
        }
        return true;
    }

    public Map<PlayerEntity, Double> calculateAnchorDamage(BlockPos pos) {
        return calculateAnchorDamage(pos, null, true);
    }

    private Map<PlayerEntity, Double> calculateAnchorDamage(
            BlockPos pos, Map<PlayerEntity, Box> predictedBoxes, boolean usePredict) {
        return calculateAnchorDamage(pos, predictedBoxes, usePredict, Map.of());
    }

    private Map<PlayerEntity, Double> calculateAnchorDamage(
            BlockPos pos,
            Map<PlayerEntity, Box> predictedBoxes,
            boolean usePredict,
            Map<BlockPos, BlockState> overrides) {
        Map<PlayerEntity, Double> damageMap = new LinkedHashMap<>();
        if (mc.world == null || mc.player == null) {
            return damageMap;
        }
        Vec3d explosionPos = pos.toCenterPos();
        overrides = new HashMap<>(overrides);
        overrides.put(pos, Blocks.AIR.getDefaultState());
        ExplosionUtils.BlockStateAccess access = ExplosionUtils.fromWorldWithOverrides(mc.world, overrides);
        damageMap.put(mc.player, (double) ExplosionUtils.calculateExplosionRawDamage(
                ANCHOR_POWER, explosionPos, mc.player.getBoundingBox(), access, ExplosionUtils.ALL_TERRAIN));
        for (PlayerEntity target : targetEntity) {
            if (!EntityUtils.isEntityValid(target) || target == mc.player) {
                continue;
            }
            Box targetBox = getTargetDamageBox(target, predictedBoxes, usePredict);
            damageMap.put(target, (double) ExplosionUtils.calculateExplosionRawDamage(
                    ANCHOR_POWER, explosionPos, targetBox, access, ExplosionUtils.EXPLOSION_RESISTENCE));
        }
        return damageMap;
    }

    public void tickAnchorPosition() {
        Map<PlayerEntity, Box> predictedBoxes = new HashMap<>();

        var iter = trackedAnchorPositions.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            BlockPos pos = entry.getKey();
            BlockState blockState = mc.world.getBlockState(pos);
            if (blockState.getBlock() instanceof RespawnAnchorBlock
                    && InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)) {
                Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(pos, predictedBoxes, true);
                if (isSuitableExplodePos(pos, damageMap)) {
                    entry.getValue().damageCache = damageMap;
                    // do not override this
                    if (entry.getValue().powerLevel == 0) {
                        entry.getValue().powerLevel = blockState.get(RespawnAnchorBlock.CHARGES);
                    }
                } else {
                    iter.remove();
                }
            } else {
                iter.remove();
            }
        }
        BlockPos playerPos = mc.player.getBlockPos();
        for (var blocks : interactRangeBlocks) {
            BlockPos testPos = playerPos.add(blocks);
            BlockState state = mc.world.getBlockState(testPos);

            if (state.getBlock() instanceof RespawnAnchorBlock
                    && !trackedAnchorPositions.containsKey(testPos)
                    && InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), testPos)) {
                Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(testPos, predictedBoxes, true);
                if (isSuitableExplodePos(testPos, damageMap)) {
                    trackedAnchorPositions.put(
                            testPos, new AnchorCache(state.get(RespawnAnchorBlock.CHARGES), damageMap));
                }
            }
        }
    }

    private boolean isSuitableExplodePos(BlockPos pos, Map<PlayerEntity, Double> damageMap) {
        if (damageMap == null || damageMap.isEmpty()) {
            return false;
        }
        if (!isSelfDamageAcceptable(damageMap)) {
            return false;
        }
        return isTargetDamageAcceptable(damageMap);
    }

    private boolean isSelfDamageAcceptable(Map<PlayerEntity, Double> damageMap) {
        double selfDamage = damageMap.getOrDefault(mc.player, Double.POSITIVE_INFINITY);
        float finalDamage = DamageUtils.getFinalDamage(
                mc.player,
                (float) selfDamage,
                DamageUtils.createDamageSource(DamageTypes.PLAYER_EXPLOSION, null, null));
        return finalDamage <= selfFinalDamageThreshold.get();
    }

    private boolean isTargetDamageAcceptable(Map<PlayerEntity, Double> damageMap) {
        return getBestEnemyDamage(damageMap) >= targetDamageThreshold.get();
    }

    //    private boolean isSuitablePacketMineBridgePos(BlockPos pos, Map<PlayerEntity, Double> damageMap) {
    //        return damageMap != null
    //                && !damageMap.isEmpty()
    //                && getBestEnemyDamage(damageMap) > Double.NEGATIVE_INFINITY
    //                && hasNearbyMiningTarget(pos, damageMap);
    //    }

    public boolean isSuitablePosition(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return (state.isAir() || state.isLiquid() || state.isReplaceable())
                && CombatManager.INSTANCE.canBlockPlace(mc.player, pos, Blocks.RESPAWN_ANCHOR.getDefaultState());
    }

    int timer = 0;

    public void onPreInputEvents(Event<Void> event) {
        if (checkNull()) return;
        if (enable.get()) {
            if (!InteractUtils.canRespawnAnchorExplode(mc.world)) {
                logI18N(
                        "message.module.anchor-arua.invalid-dimension",
                        mc.world.getRegistryKey().getValue());
                enable.set(false);
                return;
            }
            refreshTarget();
            if (eatingAbort.get() && mc.player.isUsingItem()) {
                return;
            }
            ++timer;
            if (placePendingBlock()) {
                return;
            }
            tickAnchorPosition();
            if (timer >= delay.get() && !targetEntity.isEmpty()) {
                if (!checkSupplies()) return;
                if (!tickAnchorExplode()) {
                    if (tickAnchorPlace()) {
                        timer = 0;
                    }
                } else {
                    timer = 0;
                }
            }
        } else {
            trackedAnchorPositions.clear();
            targetEntity = List.of();
            pendingPlace = null;
        }
    }

    private boolean placePendingBlock() {
        if (pendingPlace == null) {
            return false;
        }
        AnchorPlan option = pendingPlace;
        pendingPlace = null;
        return placeBlockingBlock(option);
    }

    public boolean tickAnchorExplode() {
        List<Map.Entry<BlockPos, AnchorCache>> entries = new ArrayList<>(trackedAnchorPositions.entrySet());
        entries.sort(Map.Entry.comparingByValue(this::compareAnchorEntries));
        int multiply = mul.get();
        int success = 0;
        for (var i = 0; i < entries.size() && success < multiply; ++i) {
            BlockPos targetPos = entries.get(i).getKey();
            if (InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), targetPos)) {
                success += litBlockPos(targetPos, entries.get(i).getValue());
            }
        }
        return success >= multiply;
    }

    public IndexEntry<ItemStack> supplyItem(Item item) {
        return InventoryUtils.findPlayerItem(s -> s.isOf(item), ghostHand.get().getSearchSize(false), true, false);
    }

    public IndexEntry<ItemStack> supplyNoItem(Item item) {
        var re = InventoryUtils.findPlayerItem(
                s -> !s.isOf(item), ghostHand.get().getSearchSize(false), true, true);
        return re == null ? InventoryUtils.getSelectedItem() : re;
    }

    public int litBlockPos(BlockPos targetPos, AnchorCache cache) {
        BlockState state = mc.world.getBlockState(targetPos);
        int interactCount = 0;
        if (state.getBlock() instanceof RespawnAnchorBlock) {
            int level = cache.powerLevel;
            BlockHitResult hitResult = RaycastUtils.createRealHitResult(targetPos);
            if (level == 0) {
                var glowstone = supplyItem(Items.GLOWSTONE);
                if (glowstone == null) {
                    return interactCount;
                }
                var callback = InvExtra.INSTANCE.swapItemToHand(glowstone.index(), false, ghostHand.get());
                if (callback == null) {
                    return interactCount;
                }
                InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
                interactCount++;
                level = 1;
                cache.powerLevel = level;
                callback.run();
                if (!use0TickSupply.get()) {
                    return interactCount;
                }
            }
            if (level > 0) {
                var noGlowStone = supplyNoItem(Items.GLOWSTONE);
                var callback = InvExtra.INSTANCE.swapItemToHand(noGlowStone.index(), false, ghostHand.get());
                if (callback == null) {
                    return interactCount;
                }
                InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
                interactCount++;
                level = 0;
                cache.powerLevel = level;
                callback.run();
                if (place0TickSupply.get()) {
                    var anchor = supplyItem(Items.RESPAWN_ANCHOR);
                    if (anchor == null) {
                        return interactCount;
                    }
                    var callback2 = InvExtra.INSTANCE.swapItemToHand(anchor.index(), false, ghostHand.get());
                    if (callback2 == null) {
                        return interactCount;
                    }
                    mc.world.setBlockState(targetPos, Blocks.AIR.getDefaultState());
                    InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
                    interactCount++;
                    callback2.run();
                } else {
                    trackedAnchorPositions.remove(targetPos);
                }
            }
        }
        return interactCount;
    }

    public boolean tickAnchorPlace() {
        if (targetEntity.isEmpty()) {
            return false;
        }
        Map<PlayerEntity, Box> predictedBoxes = new HashMap<>();

        AnchorPlan bestOption = null;
        double bestOptionValue = Double.NEGATIVE_INFINITY;
        BlockPos currentPos = mc.player.getBlockPos();
        for (Vec3i delta : interactRangeBlocks) {
            BlockPos pos = currentPos.add(delta);
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)) {
                continue;
            }
            if (!isSuitablePosition(pos)) {
                continue;
            }
            FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                    pos, airplace.get(), !mode.get().isLegal());
            if (!InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                continue;
            }
            if (!InteractExtra.INSTANCE.isWithinInteractRange(
                    mc.player.getPos(), hitResult.val().getBlockPos())) {
                continue;
            }
            Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(pos, predictedBoxes, true);
            AnchorPlan option;
            if (!isTargetDamageAcceptable(damageMap)) {
                continue;
            }
            if (isSelfDamageAcceptable(damageMap)) {
                option = new AnchorPlan(pos, hitResult.val(), damageMap, Optional.empty());
            } else {
                if (!autoBlock.get()) {
                    continue;
                }
                option = findBlockingOption(pos, hitResult.val(), predictedBoxes);
                if (option == null) {
                    continue;
                }
            }

            double optionValue = getOptionValue(option);
            if (optionValue > bestOptionValue) {
                bestOption = option;
                bestOptionValue = optionValue;
            }
        }
        if (bestOption == null) return false;
        completeAnchorPlan(bestOption);
        return true;
    }

    private void completeAnchorPlan(AnchorPlan bestOption) {
        if (bestOption != null && placeAnchor(bestOption.anchorHitResult)) {
            updateAnchor(bestOption.anchorPos, bestOption.damageMap);
            if (bestOption.blockingPos.isPresent()) {
                if (zeroTickBlock.get()) {
                    placeBlockingBlock(bestOption);
                } else {
                    pendingPlace = bestOption;
                }
            }
        }
    }

    private AnchorPlan findBlockingOption(
            BlockPos anchorPos, BlockHitResult anchorHitResult, Map<PlayerEntity, Box> predictedBoxes) {
        IndexEntry<ItemStack> blockingItem = supplyBlockingBlock();
        if (blockingItem == null) {
            return null;
        }
        if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), anchorPos)) {
            return null;
        }

        AnchorPlan bestOption = null;
        double bestOptionValue = Double.NEGATIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            BlockPos blockingPos = anchorPos.offset(direction);
            // when it is in head, it's too late
            if (InteractionTasks.checkInHead(anchorPos, mc.player.getPos())
                    || !InteractionTasks.checkPositionPlace(anchorPos, direction, mc.player.getPos())) {
                continue;
            }

            BlockState blockingState = mc.world.getBlockState(blockingPos);
            if (!blockingState.isAir() && !blockingState.isLiquid() && !blockingState.isReplaceable()) {
                continue;
            }
            if (!CombatManager.INSTANCE.canCubePlace(mc.player, blockingPos)) {
                continue;
            }
            var newDamageMap = calculateAnchorDamage(
                    anchorPos, predictedBoxes, true, Map.of(blockingPos, Blocks.OBSIDIAN.getDefaultState()));
            if (!isSuitableExplodePos(anchorPos, newDamageMap)) {
                continue;
            }
            AnchorPlan option = new AnchorPlan(anchorPos, anchorHitResult, newDamageMap, Optional.of(blockingPos));

            double optionValue = getOptionValue(option);
            if (optionValue > bestOptionValue) {
                bestOption = option;
                bestOptionValue = optionValue;
            }
        }
        return bestOption;
    }

    private double getOptionValue(AnchorPlan option) {
        double value = getBestEnemyDamage(option.damageMap);
        return option.blockingPos.isEmpty() ? value : value * blockReduce.get();
    }

    public boolean placeAnchor(BlockPos pos) {
        FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                pos, airplace.get(), !mode.get().isLegal());
        if (InteractUtils.canInteractAndPlace(mc.player, hitResult)
                && InteractExtra.INSTANCE.isWithinInteractRange(
                        mc.player.getPos(), hitResult.val().getBlockPos())) {
            return placeAnchor(hitResult.val());
        }
        return false;
    }

    public boolean placeAnchor(BlockHitResult hitResult) {
        var entry = supplyItem(Items.RESPAWN_ANCHOR);
        if (entry == null) return false;
        var runnable = InvExtra.INSTANCE.swapItemToHand(entry.index(), false, ghostHand.get());
        if (runnable == null) return false;
        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
        runnable.run();
        return true;
    }

    private IndexEntry<ItemStack> supplyBlockingBlock() {
        EntrySet<Item> allowedBlocks = blockingBlock.get();
        if (allowedBlocks == null) {
            return null;
        }
        return InventoryUtils.findPlayerItem(
                stack -> stack.getItem() instanceof BlockItem
                        && !stack.isOf(Items.GLOWSTONE)
                        && allowedBlocks.test(stack.getItem()),
                ghostHand.get().getSearchSize(false),
                true,
                false);
    }

    private boolean placeBlockingBlock(AnchorPlan option) {
        if (option.blockingPos().isEmpty()) return false;
        BlockState anchorPos = mc.world.getBlockState(option.anchorPos());
        if (anchorPos.isAir() || anchorPos.isLiquid() || anchorPos.isReplaceable()) {
            return false;
        }
        BlockPos blockingPos = option.blockingPos().get();
        BlockState blockingState = mc.world.getBlockState(blockingPos);
        if (!blockingState.isAir() && !blockingState.isLiquid() && !blockingState.isReplaceable()) {
            return false;
        }
        Vec3d directionVec = Vec3d.of(blockingPos.subtract(option.anchorPos()));
        Direction direction = Direction.getFacing(directionVec);
        BlockHitResult hitResult = InteractionTasks.createHitResult(option.anchorPos(), direction);
        if (!InteractionTasks.checkPositionPlace(hitResult)) {
            return false;
        }
        IndexEntry<ItemStack> entry = supplyBlockingBlock();
        if (entry == null) {
            return false;
        }
        Runnable runnable = InvExtra.INSTANCE.swapItemToHand(entry.index(), false, ghostHand.get());
        if (runnable == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
        runnable.run();
        return true;
    }

    public void updateAnchor(BlockPos pos, Map<PlayerEntity, Double> damageMap) {
        Map<PlayerEntity, Double> safeDamageMap =
                damageMap == null ? calculateAnchorDamage(pos) : new LinkedHashMap<>(damageMap);
        trackedAnchorPositions.put(pos, new AnchorCache(0, safeDamageMap));
    }

    private Box getTargetDamageBox(PlayerEntity target, Map<PlayerEntity, Box> predictedBoxes, boolean usePredict) {
        if (!usePredict) {
            return target.getBoundingBox();
        }
        if (predictedBoxes == null) {
            return createPredictedTargetBox(target);
        }
        return predictedBoxes.computeIfAbsent(target, this::createPredictedTargetBox);
    }

    private Box createPredictedTargetBox(PlayerEntity target) {
        Box currentBox = target.getBoundingBox();
        Vec3d currentPos = target.getPos();
        Vec3d predictedPos =
                PositionPredict.INSTANCE.attackPredictArgument.get().predict(target);
        if (predictedPos == null
                || predictedPos.squaredDistanceTo(currentPos) <= MathUtils.s2(PREDICT_POSITION_SWITCH_DISTANCE)) {
            return currentBox;
        }
        Vec3d realMovement = MovTasks.simulateMovement(target, currentPos, predictedPos.subtract(currentPos), false);
        return currentBox.offset(realMovement);
    }

    private double getBestEnemyDamage(Map<PlayerEntity, Double> damageMap) {
        double best = Double.NEGATIVE_INFINITY;
        for (PlayerEntity target : targetEntity) {
            if (!EntityUtils.isEntityValid(target) || target == mc.player) {
                continue;
            }
            Double damage = damageMap.get(target);
            if (damage != null && damage > best) {
                best = damage;
            }
        }
        return best;
    }

    public void onPrePacketMine(Event<BlockBreak> eventPreMine) {
        if (enable.get()
                && !eventPreMine.isCancelled()
                && eventPreMine.canCancel()
                && eventPreMine.context().stage() == BlockBreak.Stage.PRE) {
            BlockPos pos = eventPreMine.context().blockPos();
            if (trackedAnchorPositions.containsKey(pos)
                    && mc.world.getBlockState(pos).getBlock() instanceof RespawnAnchorBlock) {
                eventPreMine.cancel();
            }
        }
    }

    public void onPacketMine(Event<BlockBreak> eventPostMine) {
        if (enable.get()
                && packetMineBridge.get()
                && !targetEntity.isEmpty()
                && eventPostMine.context().stage() == BlockBreak.Stage.POST) {
            // bridge
            float floatValue = eventPostMine.context().progress();
            if (floatValue > 0.7f) {
                BlockPos pos = eventPostMine.context().blockPos();
                SequencedActionManager.INSTANCE.appendBlockBreakPrediction(pos, Blocks.AIR.getDefaultState());
                Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(pos, null, false);

                if (isTargetDamageAcceptable(damageMap)) {
                    FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                            pos, airplace.get(), !mode.get().isLegal());
                    if (InteractUtils.canInteractAndPlace(mc.player, hitResult)
                            && InteractExtra.INSTANCE.isWithinInteractRange(
                                    mc.player.getPos(), hitResult.val().getBlockPos())) {
                        BlockHitResult hit = hitResult.val();
                        AnchorPlan plan;

                        if (isSelfDamageAcceptable(damageMap)) {
                            plan = new AnchorPlan(pos, hit, damageMap, Optional.empty());
                        } else {
                            plan = findBlockingOption(pos, hit, null);
                        }
                        if (plan != null) {
                            completeAnchorPlan(plan);
                        }
                    }
                }
            }
        }
    }

    private int compareAnchorEntries(AnchorCache first, AnchorCache second) {
        double firstEnemy = getBestEnemyDamage(first.damageCache);
        double secondEnemy = getBestEnemyDamage(second.damageCache);
        int enemyCompare = Double.compare(secondEnemy, firstEnemy);
        if (enemyCompare != 0) {
            return enemyCompare;
        }
        double firstSelf = first.damageCache.getOrDefault(mc.player, 0.0D);
        double secondSelf = second.damageCache.getOrDefault(mc.player, 0.0D);
        int selfCompare = Double.compare(firstSelf, secondSelf);
        if (selfCompare != 0) {
            return selfCompare;
        }
        return Integer.compare(first.powerLevel, second.powerLevel);
    }

    public void onPreset(Event<EventContainer<ModulePreset>> event) {
        mode.set(LegalInteractMode.getFromPreset(event.context.getValue()));
        airplace.set(!event.context.getValue().hasAC());
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnchorCache {
        int powerLevel;
        Map<PlayerEntity, Double> damageCache;
    }

    public static record AnchorPlan(
            BlockPos anchorPos,
            BlockHitResult anchorHitResult,
            Map<PlayerEntity, Double> damageMap,
            Optional<BlockPos> blockingPos) {}
}
