package me.matl114.hacks.modules.combat;

import java.util.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.PacketMine;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

public class AnchorAura extends BaseModule {
    private static final float ANCHOR_POWER = 5.0F;
    private static final int ANCHOR_SEARCH_RADIUS = 5;
    private static final double NEARBY_TARGET_SEARCH_RADIUS = 1.0D;
    private static final double PREDICT_POSITION_SWITCH_DISTANCE = 1.0D;

    public AnchorAura() {
        super("AnchorAura");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.anchor-aura");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final EnumRef<Configs.LegalInteractMode> mode = builder(root.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

    public final FlagRef airplace =
            builder(root.add("air-place"), Boolean.class).defaultValue(false).build();

    public final IntRef range = intBuilder(root.add("range")).defaultValue(10).build();
    private List<Vec3i> interactRangeBlocks = new ArrayList<>();
    public final DoubleRef interactRange = doubleBuilder(root.add("interact-range"))
            .defaultValue(4.5)
            .updateListener(s -> interactRangeBlocks = MathUtils.create3DPointListInRange(s))
            .build();

    public final IntRef delay = intBuilder(root.add("delay"))
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef mul = intBuilder(root.add("multiply"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final DoubleRef selfDamageThreshold = doubleBuilder(root.add("self-damage-threshold"))
            .defaultValue(8.0D)
            .validator(Configs.doubleRange(0.0D, 1000.0D))
            .build();

    public final DoubleRef targetDamageThreshold = doubleBuilder(root.add("target-damage-threshold"))
            .defaultValue(16.0D)
            .validator(Configs.doubleRange(0.0D, 1000.0D))
            .build();

    public final FlagRef packetMineBridge =
            flagBuilder(root.add("packet-mine-bridge")).build();

    public final KeyBindRef packetMineBridgeHotkey = toggleHotkey(
                    root.add("packet-mine-bridge-hotkey"), new MultiKeyBind(), root.add("packet-mine-bridge"))
            .build();

    public final FlagRef place0TickSupply =
            flagBuilder(root.add("zero-tick-place-supply")).build();

    public final FlagRef use0TickSupply = flagBuilder(root.add("zero-tick-use")).build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    final TimerExecutor noSupplyExecutor = new TimerExecutor();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(CombatManager.getRequestEnableEvent(), this::onServiceExplosives);
        registerListener(Listener.getWorldSwitchPoint(), this::onSwitchWorld);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvents);
        registerListener(PacketMine.getPostPacketMine(), this::onPacketMine);
        registerListener(PacketMine.getPrePacketMine(), this::onPrePacketMine);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPreset);
    }

    public Map<BlockPos, AnchorCache> trackedAnchorPositions = new LinkedHashMap<>();
    public List<PlayerEntity> targetEntity = List.of();

    public void onServiceExplosives(Event<CombatManager.Service> event) {
        if (enable.get()) {
            event.context.enableExplosiveSearch(true);
        }
    }

    public void onSwitchWorld(Event<World> event) {
        trackedAnchorPositions.clear();
        targetEntity = List.of();
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
            return false;
        }
        return true;
    }

    public Map<PlayerEntity, Double> calculateAnchorDamage(BlockPos pos) {
        return calculateAnchorDamage(pos, null, true);
    }

    private Map<PlayerEntity, Double> calculateAnchorDamage(
            BlockPos pos, Map<PlayerEntity, Box> predictedBoxes, boolean usePredict) {
        Map<PlayerEntity, Double> damageMap = new LinkedHashMap<>();
        if (mc.world == null || mc.player == null) {
            return damageMap;
        }
        Vec3d explosionPos = pos.toCenterPos();
        Map<BlockPos, BlockState> overrides = Map.of(pos, Blocks.AIR.getDefaultState());
        var access = ExplosionUtils.fromWorldWithOverrides(mc.world, overrides);
        damageMap.put(mc.player, (double) ExplosionUtils.calculateExplosionRawDamage(
                ANCHOR_POWER, explosionPos, mc.player.getBoundingBox(), access, ExplosionUtils.EXPLOSION_RESISTENCE));
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
        if (trackedAnchorPositions.isEmpty()) {
            return;
        }
        Map<PlayerEntity, Box> predictedBoxes = new HashMap<>();

        var iter = trackedAnchorPositions.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            BlockPos pos = entry.getKey();
            BlockState blockState = mc.world.getBlockState(pos);
            if (blockState.getBlock() instanceof RespawnAnchorBlock
                    && new Box(pos).squaredMagnitude(mc.player.getEyePos()) < MathUtils.s2(interactRange.get())) {
                Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(pos, predictedBoxes, true);
                if (isSuitableExplodePos(pos, damageMap)) {
                    entry.getValue().damageCache = damageMap;
                    entry.getValue().powerLevel = blockState.get(RespawnAnchorBlock.CHARGES);
                } else {
                    iter.remove();
                }
            } else {
                iter.remove();
            }
        }
        for (var blocks : CombatManager.INSTANCE.trackedExplosives.entrySet()) {
            BlockPos pos = blocks.getKey();
            BlockState state = blocks.getValue();
            if (!trackedAnchorPositions.containsKey(pos)
                    && new Box(pos).squaredMagnitude(mc.player.getEyePos()) < MathUtils.s2(interactRange.get())) {
                Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(pos, predictedBoxes, true);
                if (isSuitableExplodePos(pos, damageMap)) {
                    trackedAnchorPositions.put(pos, new AnchorCache(state.get(RespawnAnchorBlock.CHARGES), damageMap));
                }
            }
        }
    }

    private boolean isSuitableExplodePos(BlockPos pos, Map<PlayerEntity, Double> damageMap) {
        if (damageMap == null || damageMap.isEmpty()) {
            return false;
        }
        double selfDamage = damageMap.getOrDefault(mc.player, Double.POSITIVE_INFINITY);
        if (selfDamage > selfDamageThreshold.get()) {
            return false;
        }
        return getBestEnemyDamage(damageMap) >= targetDamageThreshold.get();
    }

    private boolean isSuitablePacketMineBridgePos(BlockPos pos, Map<PlayerEntity, Double> damageMap) {
        return damageMap != null
                && !damageMap.isEmpty()
                && getBestEnemyDamage(damageMap) > Double.NEGATIVE_INFINITY
                && damageMap.getOrDefault(mc.player, Double.POSITIVE_INFINITY) <= selfDamageThreshold.get()
                && hasNearbyMiningTarget(pos, damageMap);
    }

    public boolean isSuitablePosition(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return (state.isAir() || state.isLiquid() || state.isReplaceable())
                && InteractUtils.canBlockPlace(mc.player, pos, Blocks.RESPAWN_ANCHOR.getDefaultState());
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
            tickAnchorPosition();
            if (++timer > delay.get() && !targetEntity.isEmpty()) {
                timer = 0;
                if (!checkSupplies()) return;
                if (!tickAnchorExplode()) {
                    tickAnchorPlace();
                }
            }
        } else {
            trackedAnchorPositions.clear();
            targetEntity = List.of();
        }
    }

    public boolean tickAnchorExplode() {
        List<Map.Entry<BlockPos, AnchorCache>> entries = new ArrayList<>(trackedAnchorPositions.entrySet());
        entries.sort(Map.Entry.comparingByValue(this::compareAnchorEntries));
        int multiply = mul.get();
        int success = 0;
        for (var i = 0; i < entries.size() && success < multiply; ++i) {
            BlockPos targetPos = entries.get(i).getKey();
            success += litBlockPos(targetPos, entries.get(i).getValue());
        }
        return success >= multiply;
    }

    public IndexEntry<ItemStack> supplyItem(Item item) {
        return InventoryUtils.findPlayerItem(s -> s.isOf(item), true, false);
    }

    public IndexEntry<ItemStack> supplyNoItem(Item item) {
        var re = InventoryUtils.findPlayerItem(s -> !s.isOf(item), true, true);
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
                var callback = InvExtra.INSTANCE.swapInventoryIndexToHand(glowstone.index());
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
                var callback = InvExtra.INSTANCE.swapInventoryIndexToHand(noGlowStone.index());
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
                    var callback2 = InvExtra.INSTANCE.swapInventoryIndexToHand(anchor.index());
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

    public void onPrePacketMine(Event<PacketMine.Pre> eventPreMine) {
        if (enable.get() && !eventPreMine.isCancelled()) {
            BlockPos pos = eventPreMine.getArgs(0);
            if (trackedAnchorPositions.containsKey(pos)
                    && mc.world.getBlockState(pos).getBlock() instanceof RespawnAnchorBlock) {
                eventPreMine.cancel();
            }
        }
    }

    public void onPacketMine(Event<PacketMine.Post> eventPostMine) {
        if (enable.get() && packetMineBridge.get() && !targetEntity.isEmpty()) {
            // bridge
            float floatValue = eventPostMine.getArgs(1);
            if (floatValue > 0.7f) {
                BlockPos pos = eventPostMine.getArgs(0);
                mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(pos, null, false);
                if (isSuitablePacketMineBridgePos(pos, damageMap)) {
                    if (placeAnchor(pos)) {
                        updateAnchor(pos, damageMap);
                    }
                }
            }
        }
    }

    public boolean isWithinInteractRange(BlockPos pos) {
        return new Box(pos).squaredMagnitude(mc.player.getEyePos()) < MathUtils.s2(interactRange.get());
    }

    public boolean placeAnchor(BlockPos pos) {
        FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                pos, airplace.get(), !mode.get().isLegal());
        if (InteractUtils.canInteractAndPlace(mc.player, hitResult)
                && isWithinInteractRange(hitResult.val().getBlockPos())) {
            return placeAnchor(pos, hitResult.val());
        }
        return false;
    }

    public boolean placeAnchor(BlockPos pos, BlockHitResult hitResult) {
        var entry = supplyItem(Items.RESPAWN_ANCHOR);
        if (entry == null) return false;
        var runnable = InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
        if (runnable == null) return false;
        mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
        runnable.run();
        return true;
    }

    public void updateAnchor(BlockPos pos, Map<PlayerEntity, Double> damageMap) {
        Map<PlayerEntity, Double> safeDamageMap =
                damageMap == null ? calculateAnchorDamage(pos) : new LinkedHashMap<>(damageMap);
        trackedAnchorPositions.put(pos, new AnchorCache(0, safeDamageMap));
    }

    public void tickAnchorPlace() {
        if (targetEntity.isEmpty()) {
            return;
        }
        Map<PlayerEntity, Box> predictedBoxes = new HashMap<>();

        BlockPos bestPos = null;

        FlagEntry<BlockHitResult> bestHitResult = null;
        Map<PlayerEntity, Double> bestDamageMap = null;
        double bestEnemyDamage = Double.NEGATIVE_INFINITY;
        BlockPos currentPos = mc.player.getBlockPos();
        for (Vec3i delta : interactRangeBlocks) {
            BlockPos pos = currentPos.add(delta);
            if (!isWithinInteractRange(pos)) {
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
            Map<PlayerEntity, Double> damageMap = calculateAnchorDamage(pos, predictedBoxes, true);
            // ...
            if (!isSuitableExplodePos(pos, damageMap)) {
                continue;
            }

            double enemyDamage = getBestEnemyDamage(damageMap);
            if (enemyDamage > bestEnemyDamage) {
                bestPos = pos;
                bestHitResult = hitResult;
                bestDamageMap = damageMap;
                bestEnemyDamage = enemyDamage;
            }
        }
        if (bestPos != null) {
            if (InteractUtils.canInteractAndPlace(mc.player, bestHitResult)
                    && isWithinInteractRange(bestHitResult.val().getBlockPos())
                    && placeAnchor(bestPos, bestHitResult.val())) {
                updateAnchor(bestPos, bestDamageMap);
            }
        }
    }

    private Box getTargetDamageBox(PlayerEntity target, Map<PlayerEntity, Box> predictedBoxes, boolean usePredict) {
        if (!usePredict || predictedBoxes == null) {
            return target.getBoundingBox();
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

    private boolean hasNearbyMiningTarget(BlockPos pos, Map<PlayerEntity, Double> damageMap) {
        if (targetEntity.isEmpty()) {
            return false;
        }
        PlayerEntity player = targetEntity.stream()
                .filter(s -> s != mc.player)
                .min(Comparator.comparingDouble(s -> pos.getSquaredDistance(s.getPos())))
                .orElse(null);
        if (player == null) {
            return false;
        }
        return damageMap.getOrDefault(player, 0.0D) > targetDamageThreshold.get();
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
        mode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
        airplace.set(!event.context.getValue().hasAC());
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnchorCache {
        int powerLevel;
        Map<PlayerEntity, Double> damageCache;
    }
}
