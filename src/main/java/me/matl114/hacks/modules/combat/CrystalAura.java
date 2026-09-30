package me.matl114.hacks.modules.combat;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.EventContainer;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public class CrystalAura extends BaseModule {
    private static final int CRYSTAL_SEARCH_RADIUS = 5;

    public CrystalAura() {
        super("CrystalAura");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.crystal-aura");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final EnumRef<LegalInteractMode> mode = builder(root.add("mode"), LegalInteractMode.class)
            .defaultValue(LegalInteractMode.NONE)
            .build();

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

    public final FlagRef placeAfterAttack = builder(root.add("place-after-attack"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef eatingAbort = builder(root.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef autoBase =
            builder(root.add("auto-base"), Boolean.class).defaultValue(true).build();

    public final FlagRef offhand =
            builder(root.add("offhand"), Boolean.class).defaultValue(false).build();

    public final FlagRef airplaceBase =
            builder(root.add("air-place"), Boolean.class).defaultValue(false).build();

    public final FlagRef zeroTickBase = builder(root.add("zero-tick-base"), Boolean.class)
            .defaultValue(false)
            .build();

    public final DoubleRef baseReduce = builder(root.add("base-value-reduce"), DoubleRef.TYPE)
            .defaultValue(0.9D)
            .build();

    public final FlagRef autoBlock =
            builder(root.add("auto-block"), Boolean.class).defaultValue(false).build();

    public final NBTRef<EntrySet<Item>> blockingBlock = builder(root.add("blocking-block"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.OBSIDIAN)))
            .build();

    public final DoubleRef blockReduce = builder(root.add("block-reduce"), DoubleRef.TYPE)
            .defaultValue(0.9D)
            .validator(Configs.doubleRange(0.0D, 1.0D))
            .build();

    public final FlagRef zeroTickBlock = builder(root.add("zero-tick-block"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef scaffoldBlock = builder(root.add("scaffold-block"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef autoFill =
            builder(root.add("auto-fill"), Boolean.class).defaultValue(false).build();

    public final KeyBindRef hotkeyFill = toggleHotkey(
                    root.add("auto-fill-hotkey"), new MultiKeyBind(), root.add("auto-fill"))
            .build();

    public final NBTRef<EntrySet<Item>> fillWhiteList = builder(root.add("fill-white-list"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.GLASS, Items.OAK_LEAVES)))
            .build();

    public final FlagRef fillIgnoreDamage = builder(root.add("fill-ignore-damage"), Boolean.class)
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

    public final FlagRef usePredictor = builder(root.add("damage-use-predictor"), Boolean.class)
            .defaultValue(true)
            .build();

    public final EnumRef<GhostHandMode> ghostHand = builder(root.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef notifySupply =
            builder(root.add("notify-supply"), Boolean.class).defaultValue(true).build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerRespawnPoint(), this::onWorldSwitch);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(Listener.getPostHandleInputEvents(), this::onPostInputEvent);
        registerListener(PacketMine.getPrePacketMine(), this::onPrePacketMine);
        registerListener(PacketMine.getPostPacketMine(), this::onPostPacketMine);
        registerListener(CombatManager.getRequestEnableEvent(), this::onRequestCombatService);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPreset);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
    }

    TimerExecutor supplyEndCrystalExecutor = new TimerExecutor();
    TimerExecutor supplyBaseExecutor = new TimerExecutor();
    TimerExecutor supplyFillBlockExecutor = new TimerExecutor();
    List<PlayerEntity> currentTarget = List.of();
    List<CrystalPlan> pendingBases = new ArrayList<>();
    Queue<CrystalPlan> delayQueue = new ArrayDeque<>();
    Map<Entity, Integer> postRemovals = new HashMap<>();
    Map<BlockPos, Integer> trackedGlasses = new ConcurrentHashMap<>();
    int timer = 0;
    RenderCollector<Box> debugRender = RenderCollectors.createBoxCollector(true, false, false);
    boolean nextHitSkipDamageTest;

    public void onWorldSwitch(Event<ClientPlayerEntity> event) {
        currentTarget = List.of();
        postRemovals.clear();
        trackedGlasses.clear();
        timer = 0;
        pendingBases.clear();
        delayQueue.clear();
    }

    public boolean attackCrystal(Entity entity) {
        if (!Attack.INSTANCE.attackEntity(entity)) {
            postRemovals.put(entity, Tasks.getTick());
            return true;
        }
        return false;
    }

    public void onPreInputEvent(Event<Void> event) {
        if (checkNull()) {
            return;
        }
        debugRender.clear();
        // 3 ticks for network lag
        postRemovals
                .entrySet()
                .removeIf(entity ->
                        !EntityUtils.isEntityValid(entity.getKey()) || Tasks.getTick() > entity.getValue() + 3);
        if (enable.get()) {
            refreshTargets();
            if (eatingAbort.get() && mc.player.isUsingItem()) {
                return;
            }
            tickTrackedGlasses();
            // constantly attack, only delay when place
            boolean attack = tickAttack();
            tickDelayQueue();
            tickPendingBase();

            if ((++timer >= delay.get() || (placeAfterAttack.get() && attack)) && !currentTarget.isEmpty()) {
                timer = 0;
                if (checkSupplies()) {
                    tickPlace();
                }
            }
        } else {
            currentTarget = List.of();
            postRemovals.clear();
            trackedGlasses.clear();
            timer = 0;
            pendingBases.clear();
            delayQueue.clear();
        }
    }

    public void onPostInputEvent(Event<Void> event) {
        if (checkNull()) {
            return;
        }
        if (enable.get() && autoFill.get()) {
            if (eatingAbort.get() && mc.player.isUsingItem()) {
                return;
            }
            if (currentTarget.isEmpty()) {
                return;
            }
            if (supplyCrystalItem() == null) {
                return;
            }
            BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)) return;
            BlockState currentState = mc.world.getBlockState(pos);
            if (!currentState.isAir()) return;
            if (!canBridgeFillPos(pos)) {
                return;
            }
            // within range check, do not check crystal range, because human can move
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos.down())) {
                return;
            }
            if (!checkFillPlace(pos)) {
                return;
            }
            // help fix PacketMine fakeAirMine
            if (supplyFillBlock() == null) {
                supplyFillBlockExecutor.run(100, () -> {
                    if (notifySupply.get()) {
                        logI18N("message.module.crystal-aura.no-item.fill-block");
                    }
                });
                return;
            }
            Map<BlockPos, BlockState> overrideStates = new HashMap<>();
            overrideStates.put(pos, Blocks.AIR.getDefaultState());
            BlockPos failBreak =
                    PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
            if (failBreak != null) {
                overrideStates.put(failBreak, Blocks.AIR.getDefaultState());
            }
            if (!fillIgnoreDamage.get()) {
                Map<PlayerEntity, Double> damageMap = calculateCrystalDamage(pos.toBottomCenterPos(), overrideStates);
                if (!isSuitableAttackExplodePos(damageMap)) {
                    return;
                }
            }
            FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                    pos, airplaceBase.get(), !mode.get().isLegal());
            if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                placeFill(pos, hitResult.val());
            }
        }
    }

    public void onRender3D(Event<Render3D> event) {
        if (enable.get()) {
            RenderUtils.startDrawVirtual(event.context.stack());
            try {
                debugRender.render3D(event.context.stack());
            } finally {
                RenderUtils.stopDrawVirtual(event.context.stack());
            }
        }
    }

    private boolean checkSupplies() {
        var endCrystal = supplyCrystalItem() != null;
        if (!endCrystal) {
            supplyEndCrystalExecutor.run(100, () -> {
                if (notifySupply.get()) {
                    logI18N("message.module.crystal-aura.no-item.crystal");
                }
            });
            return false;
        }
        return true;
    }

    private List<EndCrystalEntity> findAttackableCrystals() {
        double attackRange = CombatExtra.INSTANCE.getAttackRange();
        return CombatManager.INSTANCE.trackedEndCrystals.stream()
                .filter(EntityUtils::isEntityValid)
                .filter(crystal -> !postRemovals.containsKey(crystal))
                .filter(crystal -> TargetSelector.INSTANCE.isTargetInRange(crystal, attackRange, 0))
                .toList();
    }

    public boolean tickAttack() {
        boolean skipTick = nextHitSkipDamageTest;
        nextHitSkipDamageTest = false;
        if (currentTarget.isEmpty()) {
            return false;
        }
        boolean attack = false;

        List<Map.Entry<EndCrystalEntity, Map<PlayerEntity, Double>>> candidates = new ArrayList<>();
        for (EndCrystalEntity crystal : findAttackableCrystals()) {
            if (postRemovals.containsKey(crystal)) {
                continue;
            }
            if (!EntityUtils.isEntityValid(crystal)) {
                continue;
            }
            Map<PlayerEntity, Double> damageMap = calculateCrystalDamage(crystal.getPos());
            if (isSuitableExplodePos(damageMap) && (skipTick || isSuitableAttackExplodePos(damageMap))) {
                candidates.add(Map.entry(crystal, damageMap));
            }
        }
        candidates.sort(Map.Entry.comparingByValue(this::compareCrystalEntries));
        for (var crystalEntry : candidates) {
            EndCrystalEntity crystal = crystalEntry.getKey();
            debugRender.submit(crystal.getBoundingBox(), ColorUtils.withAlphaInt(Color.MAGENTA, 255));
            if (!attackCrystal(crystal)) {
                break;
            }
            attack = true;
        }
        return attack;
    }

    public void refreshTargets() {
        if (TargetSelector.INSTANCE == null) {
            currentTarget = List.of();
            return;
        }
        currentTarget = TargetSelector.INSTANCE.getAttackableEntities(range.get()).stream()
                .filter(PlayerEntity.class::isInstance)
                .map(PlayerEntity.class::cast)
                .filter(EntityUtils::isEntityValid)
                .filter(player -> player != mc.player)
                .toList();
    }

    public void tickPendingBase() {
        for (CrystalPlan plan : pendingBases) {
            BlockPos basePos = plan.option().basePos();
            if (InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), basePos)
                    && isBasePlacedCrystalInAttackRange(basePos)
                    && canPlaceCrystal(basePos)) {
                placeBlockingAndCrystal(plan);
            }
        }
        pendingBases.clear();
    }

    private void tickDelayQueue() {
        int queueSize = delayQueue.size();
        for (int index = 0; index < queueSize; ++index) {
            CrystalPlan plan = delayQueue.poll();
            if (plan == null) {
                continue;
            }
            BlockPos basePos = plan.option().basePos();
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), basePos)
                    || !isBasePlacedCrystalInAttackRange(basePos)) {
                continue;
            }
            if (plan.blockingPos().isPresent()) {
                if (placeBlockingAndCrystal(plan)) {
                    return;
                }
            } else {
                if (tryPlaceCrystal(basePos)) {
                    return;
                }
            }
        }
    }

    public void tickPlace() {
        if (currentTarget.isEmpty()) {
            return;
        }
        boolean legal = mode.get().isLegal();

        CrystalPlan bestPlan = null;
        double bestEnemyDamage = Double.NEGATIVE_INFINITY;
        double bestSelfDamage = Double.POSITIVE_INFINITY;
        Map<PlayerEntity, Double> bestDamageMap = Map.of();
        Set<EndCrystalEntity> currentPreRemovals = Set.of();
        boolean canAutoBase = autoBase.get();
        if (canAutoBase) {
            if (supplyItem(Items.OBSIDIAN) == null) {
                supplyBaseExecutor.run(100, () -> {
                    if (notifySupply.get()) {
                        logI18N("message.module.crystal-aura.no-item.base");
                    }
                });
                canAutoBase = false;
            }
        }
        List<EndCrystalEntity> currentInAttackRange = findAttackableCrystals();
        BlockPos currentPlayerPos = mc.player.getBlockPos();
        double collisionDamage = -1.0D;
        for (Vec3i delta : interactRangeBlocks) {
            BlockPos pos = currentPlayerPos.add(delta);
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)) {
                continue;
            }
            if (!isBasePlacedCrystalInAttackRange(pos)) {
                continue;
            }

            var entities = new ArrayList<>(getCrystalBlockingEntity(pos.up()));
            Set<EndCrystalEntity> entitySet = new HashSet<>();
            Set<Entity> entitySet2 = new HashSet<>();
            entities.removeIf(s -> {
                if (s instanceof EndCrystalEntity end
                        && TargetSelector.INSTANCE.isWithinAttackRange(mc.player.getPos(), end)
                        && isSuitableExplodePos(calculateCrystalDamage(end.getPos()))) {
                    entitySet.add(end);
                    return true;
                } else if (canExplodeRemoveEntity(s)) {
                    entitySet2.add(s);
                    return true;
                } else {
                    return false;
                }
            });
            if (!entities.isEmpty()) {
                continue;
            }

            boolean canPlaceCrystal = canPlaceCrystalAt(pos.up(), Map.of());
            EndCrystalOption option;
            if (canPlaceCrystal) {
                option = new DirectPlace(pos);
            } else if (canAutoBase && isSuitableBasePos(pos)) {
                FlagEntry<BlockHitResult> hitResultEntry =
                        InteractionTasks.getPlaceSupportingResult(pos, airplaceBase.get(), !legal);
                if (InteractUtils.canInteractAndPlace(mc.player, hitResultEntry)
                        && InteractExtra.INSTANCE.isWithinInteractRange(
                                mc.player.getPos(), hitResultEntry.val().getBlockPos())) {
                    option = new AutoBase(pos, hitResultEntry.val());
                } else {
                    continue;
                }
            } else {
                continue;
            }

            CrystalPlan plan = new CrystalPlan(option, Optional.empty());
            Map<PlayerEntity, Double> damageMap =
                    calculateCrystalDamage(getCrystalExplosionPos(pos), getDamageOverrides(plan));
            if (!isSuitableExplodePos(damageMap)) {
                if (!autoBlock.get()) {
                    continue;
                }
                plan = findBlockingPlan(option);
                if (plan == null) {
                    continue;
                }
                damageMap = calculateCrystalDamage(getCrystalExplosionPos(pos), getDamageOverrides(plan));
                if (!isSuitableExplodePos(damageMap)) {
                    continue;
                }
            }

            double enemyDamage = getBestEnemyDamage(damageMap);
            double selfDamage = damageMap.getOrDefault(mc.player, Double.POSITIVE_INFINITY);
            double damageValue = getPlanValue(plan, enemyDamage);
            if (damageValue > bestEnemyDamage || (damageValue == bestEnemyDamage && selfDamage < bestSelfDamage)) {
                if (!entitySet2.isEmpty()) {
                    collisionDamage = Math.max(damageValue, collisionDamage);
                } else {
                    bestPlan = plan;
                    bestEnemyDamage = damageValue;
                    bestSelfDamage = selfDamage;
                    bestDamageMap = damageMap;
                    currentPreRemovals = entitySet;
                }
            }
        }

        if (bestPlan == null) {
            return;
        }
        if (!isSuitableAttackExplodePos(bestDamageMap)) {
            if (collisionDamage > bestEnemyDamage) {
                if (!currentPreRemovals.isEmpty()) {
                    for (var re : currentPreRemovals) {
                        if (!attackCrystal(re)) {
                            return;
                        }
                    }
                    return;
                }
                if (!currentInAttackRange.isEmpty()) {
                    for (var re : currentInAttackRange) {
                        if (!attackCrystal(re)) {
                            return;
                        }
                    }
                    return;
                }
                nextHitSkipDamageTest = true;
            } else {
                return;
            }
        }

        debugRender.submit(new Box(bestPlan.option().basePos()), ColorUtils.withAlphaInt(Color.MAGENTA, 255));
        if (!currentPreRemovals.isEmpty()) {
            for (var re : currentPreRemovals) {
                if (!attackCrystal(re)) {
                    return;
                }
            }
        }
        if (bestPlan.option().needBase()) {
            placeBase(bestPlan);
        } else {
            placeBlockingAndCrystal(bestPlan);
        }
    }

    private Map<BlockPos, BlockState> getDamageOverrides(CrystalPlan plan) {
        Map<BlockPos, BlockState> overrides = new HashMap<>();
        if (plan.option().needBase()) {
            overrides.put(plan.option().basePos(), Blocks.OBSIDIAN.getDefaultState());
        }
        plan.blockingPos().ifPresent(pos -> overrides.put(pos, Blocks.OBSIDIAN.getDefaultState()));
        return overrides;
    }

    private double getPlanValue(CrystalPlan plan, double enemyDamage) {
        double value = enemyDamage;
        if (plan.option().needBase()) {
            value *= baseReduce.get();
        }
        if (plan.blockingPos().isPresent()) {
            value *= blockReduce.get();
        }
        return value;
    }

    private CrystalPlan findBlockingPlan(EndCrystalOption option) {
        IndexEntry<ItemStack> blockingItem = supplyBlockingBlock();
        if (blockingItem == null) {
            return null;
        }

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
        BlockPos crystalPos = option.basePos().up();
        CrystalPlan bestHorizontalPlan = null;
        CrystalPlan bestHorizontalScaffoldPlan = null;
        CrystalPlan bestTopPlan = null;
        double bestHorizontalValue = Double.NEGATIVE_INFINITY;
        double bestHorizontalScaffoldValue = Double.NEGATIVE_INFINITY;
        double bestTopValue = Double.NEGATIVE_INFINITY;
        for (Direction direction : directions) {
            BlockPos blockingPos = crystalPos.offset(direction);
            BlockState blockingState = mc.world.getBlockState(blockingPos);
            if (!(blockingState.isAir() || blockingState.isLiquid() || blockingState.isReplaceable())) {
                continue;
            }
            if (InteractionTasks.checkInHead(blockingPos, mc.player.getPos())
                    || !InteractionTasks.checkPositionPlace(crystalPos, direction, mc.player.getPos())) {
                continue;
            }

            CrystalPlan plan = new CrystalPlan(option, Optional.of(blockingPos));
            boolean directPlace = InteractUtils.canInteractAndPlace(mc.player, getBlockingPlaceResult(plan));
            boolean scaffoldPlace = !directPlace && scaffoldBlock.get() && direction.getAxis() != Direction.Axis.Y;
            if (!directPlace && !scaffoldPlace) {
                continue;
            }

            Map<PlayerEntity, Double> damageMap =
                    calculateCrystalDamage(getCrystalExplosionPos(option.basePos()), getDamageOverrides(plan));
            if (!isSuitableExplodePos(damageMap)) {
                continue;
            }
            double value = getPlanValue(plan, getBestEnemyDamage(damageMap));
            if (directPlace) {
                if (direction.getAxis() == Direction.Axis.Y) {
                    if (value > bestTopValue) {
                        bestTopPlan = plan;
                        bestTopValue = value;
                    }
                } else if (value > bestHorizontalValue) {
                    bestHorizontalPlan = plan;
                    bestHorizontalValue = value;
                }
            } else if (value > bestHorizontalScaffoldValue) {
                bestHorizontalScaffoldPlan = plan;
                bestHorizontalScaffoldValue = value;
            }
        }
        if (bestHorizontalPlan != null) {
            return bestHorizontalPlan;
        }
        if (bestHorizontalScaffoldPlan != null) {
            return bestHorizontalScaffoldPlan;
        }
        return bestTopPlan;
    }

    private Map<PlayerEntity, Double> calculateCrystalDamage(Vec3d explosionPos) {
        return calculateCrystalDamage(explosionPos, Map.of());
    }

    private Map<PlayerEntity, Double> calculateCrystalDamage(Vec3d explosionPos, Map<BlockPos, BlockState> overrides) {
        Map<PlayerEntity, Double> damageMap = new LinkedHashMap<>();
        if (mc.world == null || mc.player == null) {
            return damageMap;
        }
        ExplosionUtils.BlockStateAccess access = overrides.isEmpty()
                ? ExplosionUtils.fromWorld(mc.world)
                : ExplosionUtils.fromWorldWithOverrides(mc.world, overrides);
        damageMap.put(mc.player, (double) ExplosionUtils.calculateExplosionRawDamage(
                ExplosionUtils.END_CRYSTAL_POWER,
                explosionPos,
                mc.player.getBoundingBox(),
                access,
                ExplosionUtils.ALL_TERRAIN));
        for (PlayerEntity target : currentTarget) {
            if (!EntityUtils.isEntityValid(target) || target == mc.player) {
                continue;
            }
            Vec3d targetPos = target.getPos();
            Vec3d realMovement;
            if (usePredictor.get()) {
                Vec3d predictionPos =
                        PositionPredict.INSTANCE.attackPredictArgument.get().predict(target);

                if (predictionPos.squaredDistanceTo(targetPos) > MathUtils.s2(0.5)) {
                    Vec3d movement = predictionPos.subtract(targetPos);
                    realMovement = MovTasks.simulateMovement(target, targetPos, movement, false);
                } else {
                    realMovement = Vec3d.ZERO;
                }
            } else {
                realMovement = Vec3d.ZERO;
            }

            damageMap.put(target, (double) ExplosionUtils.calculateExplosionRawDamage(
                    ExplosionUtils.END_CRYSTAL_POWER,
                    explosionPos,
                    target.getBoundingBox().offset(realMovement),
                    access,
                    ExplosionUtils.EXPLOSION_RESISTENCE));
        }
        return damageMap;
    }

    private Vec3d getCrystalExplosionPos(BlockPos basePos) {
        return basePos.up().toBottomCenterPos();
    }

    private boolean isSuitableAttackExplodePos(Map<PlayerEntity, Double> damageMap) {
        if (damageMap == null || damageMap.isEmpty()) {
            return false;
        }
        return getBestEnemyDamage(damageMap) >= targetDamageThreshold.get();
    }

    private boolean isSuitableExplodePos(Map<PlayerEntity, Double> damageMap) {
        if (damageMap == null || damageMap.isEmpty()) {
            return false;
        }
        double selfDamage = damageMap.getOrDefault(mc.player, Double.POSITIVE_INFINITY);
        float finalDamage = DamageUtils.getFinalDamage(
                mc.player,
                (float) selfDamage,
                DamageUtils.createDamageSource(DamageTypes.PLAYER_EXPLOSION, null, mc.player));
        if (finalDamage > selfFinalDamageThreshold.get()) {
            return false;
        }
        return true;
    }

    private double getBestEnemyDamage(Map<PlayerEntity, Double> damageMap) {
        double best = Double.NEGATIVE_INFINITY;
        for (PlayerEntity target : currentTarget) {
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

    private boolean isBasePlacedCrystalInAttackRange(BlockPos basePos) {
        Vec3d place = basePos.up().toBottomCenterPos();
        Box estimatedEndCrystalBox = new Box(place.x - 1, place.y, place.z - 1, place.x + 1, place.y + 2, place.z + 1);
        return estimatedEndCrystalBox.squaredMagnitude(mc.player.getEyePos())
                <= MathUtils.s2(CombatExtra.INSTANCE.getAttackRange());
    }

    private boolean isCrystalBase(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    private boolean canBlockCrystalPlace(Entity entity) {

        return EntityUtils.isEntityValid(entity) && (!postRemovals.containsKey(entity));
    }

    private boolean canExplodeRemoveEntity(Entity entity) {
        return entity instanceof EndCrystalEntity
                || entity instanceof BlockAttachedEntity
                || entity instanceof ExperienceOrbEntity
                || entity instanceof ArmorStandEntity;
    }

    private List<Entity> getCrystalBlockingEntity(BlockPos crystalPos) {
        return mc.world.getOtherEntities(null, new Box(crystalPos).stretch(0, 1, 0), this::canBlockCrystalPlace);
    }

    private boolean canPlaceCrystalAt(BlockPos crystalPos, Map<BlockPos, BlockState> overrides) {
        ExplosionUtils.BlockStateAccess stateAccess = ExplosionUtils.fromWorldWithOverrides(mc.world, overrides);
        BlockState crystalState = stateAccess.getBlockState(crystalPos);
        if (crystalState == null || !crystalState.isAir()) {
            return false;
        }
        BlockState baseState = stateAccess.getBlockState(crystalPos.down());
        if (baseState == null || !isCrystalBase(baseState)) {
            return false;
        }
        return true;
    }

    private boolean canPlaceCrystalAtPos(BlockPos crystalPos, Map<BlockPos, BlockState> overrides) {

        return canPlaceCrystalAt(crystalPos, overrides)
                && getCrystalBlockingEntity(crystalPos).isEmpty();
    }

    private boolean canPlaceCrystal(BlockPos basePos) {
        return canPlaceCrystalAtPos(basePos.up(), Map.of());
    }

    private boolean isSuitableBasePos(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return (state.isAir() || state.isLiquid() || state.isReplaceable())
                && InteractUtils.canBlockPlace(mc.player, pos, Blocks.OBSIDIAN.getDefaultState())
                && canPlaceCrystalAtPos(pos.up(), Map.of(pos, Blocks.OBSIDIAN.getDefaultState()));
    }

    private IndexEntry<ItemStack> supplyItem(Item item) {
        return InventoryUtils.findPlayerItem(
                stack -> stack.isOf(item), ghostHand.get().getSearchSize(offhand.get()), true, false);
    }

    private boolean isFillBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !fillWhiteList.get().test(stack.getItem())) {
            return false;
        }
        return true;
    }

    private IndexEntry<ItemStack> supplyFillBlock() {
        return InventoryUtils.findPlayerItem(
                this::isFillBlock, ghostHand.get().getSearchSize(offhand.get()), true, false);
    }

    private IndexEntry<ItemStack> supplyCrystalItem() {
        return InventoryUtils.findPlayerItem(
                stack -> stack.isOf(Items.END_CRYSTAL), ghostHand.get().getSearchSize(offhand.get()), true, false);
    }

    private IndexEntry<ItemStack> supplyBlockingBlock() {
        EntrySet<Item> allowedBlocks = blockingBlock.get();
        if (allowedBlocks == null) {
            return null;
        }
        return InventoryUtils.findPlayerItem(
                stack -> stack.getItem() instanceof BlockItem && allowedBlocks.test(stack.getItem()),
                ghostHand.get().getSearchSize(false),
                true,
                false);
    }

    private boolean shouldKeepTrackedGlass(BlockPos glassPos) {
        if (fillIgnoreDamage.get()) {
            return true;
        }
        Map<BlockPos, BlockState> overrides =
                Map.of(glassPos, Blocks.AIR.getDefaultState(), glassPos.down(), Blocks.OBSIDIAN.getDefaultState());

        Map<PlayerEntity, Double> damageMap = calculateCrystalDamage(glassPos.toBottomCenterPos(), overrides);
        return isSuitableAttackExplodePos(damageMap);
    }

    private boolean isTrackedGlassState(BlockState state) {
        if (state == null || state.isAir() || state.isLiquid()) {
            return false;
        }
        Item item = state.getBlock().asItem();
        return item != Items.AIR && fillWhiteList.get().test(item);
    }

    private void tickTrackedGlasses() {
        if (trackedGlasses.isEmpty()) {
            return;
        }
        if (currentTarget.isEmpty()) {
            trackedGlasses.clear();
            return;
        }
        trackedGlasses.keySet().removeIf(s -> s.getSquaredDistance(mc.player.getPos()) > MathUtils.s2(8));
        trackedGlasses.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            BlockState state = mc.world.getBlockState(pos);
            return !isTrackedGlassState(state) || !shouldKeepTrackedGlass(pos);
        });
    }

    private boolean tryPlaceCrystal(BlockPos basePos) {
        if (!canPlaceCrystal(basePos)) {
            return false;
        }
        IndexEntry<ItemStack> entry = supplyCrystalItem();
        if (entry == null) {
            return false;
        }
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(entry.index(), offhand.get(), ghostHand.get());
        if (callback == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(
                mode.get(),
                InteractionTasks.createHitResult(basePos, mc.player.getPos()),
                offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND,
                swingHand.get());
        callback.run();
        return true;
    }

    private boolean placeBase(CrystalPlan plan) {
        if (!(plan.option() instanceof AutoBase autoBase)) {
            return false;
        }
        IndexEntry<ItemStack> entry = supplyItem(Items.OBSIDIAN);
        if (entry == null) {
            return false;
        }
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(entry.index(), offhand.get(), ghostHand.get());
        if (callback == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(
                mode.get(),
                autoBase.basePlaceResult(),
                offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND,
                swingHand.get());
        callback.run();
        if (zeroTickBase.get()
                && DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(
                        mode.get().canMultiRotPlace())) {
            placeBlockingAndCrystal(plan);
            return true;
        }
        pendingBases.add(plan);
        return true;
    }

    private FlagEntry<BlockHitResult> getBlockingPlaceResult(CrystalPlan plan) {
        Optional<BlockPos> blockingPos = plan.blockingPos();
        if (blockingPos.isEmpty()) {
            return null;
        }
        return InteractionTasks.getPlaceSupportingResult(
                blockingPos.get(), airplaceBase.get(), !mode.get().isLegal());
    }

    private boolean placeBlockingBlock(CrystalPlan plan, IndexEntry<ItemStack> entry) {
        FlagEntry<BlockHitResult> hitResultEntry = getBlockingPlaceResult(plan);
        if (!InteractUtils.canInteractAndPlace(mc.player, hitResultEntry)) {
            return false;
        }
        return placeBlockingBlock(plan, entry, hitResultEntry.val());
    }

    private boolean placeBlockingBlock(CrystalPlan plan, IndexEntry<ItemStack> entry, BlockHitResult hitResult) {
        if (hitResult == null || !canPlaceBlockingBlockAtHit(plan, entry.val(), hitResult)) {
            return false;
        }
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(entry.index(), false, ghostHand.get());
        if (callback == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
        callback.run();
        return true;
    }

    private boolean canPlaceBlockingBlockAtHit(CrystalPlan plan, ItemStack stack, BlockHitResult hitResult) {
        BlockPos blockingPos = plan.blockingPos().orElse(null);
        if (blockingPos == null
                || !(stack.getItem() instanceof BlockItem)
                || InteractionTasks.checkInHead(blockingPos, mc.player.getPos())
                || !InteractUtils.canInteractAndPlace(mc.player, new FlagEntry<>(false, hitResult))) {
            return false;
        }
        BlockState currentState = mc.world.getBlockState(blockingPos);
        if (!(currentState.isAir() || currentState.isLiquid() || currentState.isReplaceable())) {
            return false;
        }
        BlockState placementState = InteractUtils.getBlockPlacement(mc.player, Hand.MAIN_HAND, stack, hitResult);
        return placementState != null && InteractUtils.canBlockPlace(mc.player, blockingPos, placementState);
    }

    private boolean placeBlockingAndCrystal(CrystalPlan plan) {
        if (plan.blockingPos().isEmpty()) {
            return tryPlaceCrystal(plan.option().basePos());
        }
        return switch (placeBlockingBlock(plan)) {
            case 2 -> {
                if (zeroTickBlock.get()
                        && DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(
                                mode.get().canMultiRotPlace())) {
                    yield tryPlaceCrystal(plan.option().basePos());
                } else {
                    delayQueue.add(new CrystalPlan(plan.option(), Optional.empty()));
                    yield true;
                }
            }
            case 1 -> {
                delayQueue.add(plan);
                yield true;
            }
            default -> {
                yield false;
            }
        };
    }

    private boolean canContinuePlaceBlockingBlock(CrystalPlan plan) {
        BlockState stateBase = mc.world.getBlockState(plan.option().basePos());
        if (!isCrystalBase(stateBase)) {
            return false;
        }
        if (plan.blockingPos().isEmpty()) return false;
        BlockState targetState = mc.world.getBlockState(plan.blockingPos().get());
        if (!targetState.isAir() && !targetState.isLiquid() && !targetState.isReplaceable()) {
            return false;
        }
        return true;
    }

    private int placeBlockingBlock(CrystalPlan plan) {
        IndexEntry<ItemStack> entry = supplyBlockingBlock();
        if (entry == null) {
            return 0;
        }
        if (!canContinuePlaceBlockingBlock(plan)) {
            return 0;
        }

        BlockPos basePos = plan.option().basePos();
        BlockPos targetPos = plan.blockingPos().orElseThrow();
        boolean zeroTick = zeroTickBlock.get()
                && DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(
                        mode.get().canMultiRotPlace());
        var currentBlockingPlace = getBlockingPlaceResult(plan);
        if (!InteractUtils.canInteractAndPlace(mc.player, currentBlockingPlace)) {
            if (!scaffoldBlock.get()) {
                return 0;
            }
            BlockPos downPos = targetPos.down();
            if (downPos.getY() == basePos.getY() && basePos.getSquaredDistance(downPos) == 1) {
                if (InteractUtils.canCubePlace(mc.player, downPos)) {
                    if (!placeScaffoldBlock(plan, entry)) {
                        return 0;
                    }
                    currentBlockingPlace = getBlockingPlaceResult(plan);
                    if (!InteractUtils.canInteractAndPlace(mc.player, currentBlockingPlace)) {
                        return 0;
                    }
                    if (!zeroTick) {
                        return 1;
                    }
                } else {
                    return 0;
                }
            } else {
                return 0;
            }
        }
        if (placeBlockingBlock(plan, entry)) {
            return 2;
        }
        return 0;
    }

    private boolean finishBlockingPlace(CrystalPlan plan) {
        CrystalPlan crystalPlan = new CrystalPlan(plan.option(), Optional.empty());
        if (zeroTickBlock.get()
                && DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(
                        mode.get().canMultiRotPlace())) {
            return tryPlaceCrystal(crystalPlan.option().basePos());
        }
        delayQueue.add(crystalPlan);
        return true;
    }

    private Direction getBlockingDirection(CrystalPlan plan) {
        BlockPos blockingPos = plan.blockingPos().orElse(null);
        if (blockingPos == null) {
            return null;
        }
        BlockPos crystalPos = plan.option().basePos().up();
        for (Direction direction : Direction.values()) {
            if (crystalPos.offset(direction).equals(blockingPos)) {
                return direction;
            }
        }
        return null;
    }

    private BlockHitResult getScaffoldPlaceResult(CrystalPlan plan) {
        Direction direction = getBlockingDirection(plan);
        if (direction == null || direction.getAxis() == Direction.Axis.Y) {
            return null;
        }
        BlockPos basePos = plan.option().basePos();
        BlockPos blockingPos = plan.blockingPos().orElse(null);
        if (blockingPos == null || !blockingPos.down().equals(basePos.offset(direction))) {
            return null;
        }
        return new BlockHitResult(basePos.toCenterPos().offset(direction, 0.5D), direction, basePos, false);
    }

    private BlockHitResult getScaffoldBlockingHitResult(CrystalPlan plan) {
        BlockHitResult scaffoldHitResult = getScaffoldPlaceResult(plan);
        if (scaffoldHitResult == null) {
            return null;
        }
        BlockPos blockingPos = plan.blockingPos().orElse(null);
        if (blockingPos == null) {
            return null;
        }
        return new BlockHitResult(
                blockingPos.down().toCenterPos().offset(Direction.UP, 0.5D), Direction.UP, blockingPos.down(), false);
    }

    private boolean placeScaffoldBlock(CrystalPlan plan, IndexEntry<ItemStack> entry) {
        BlockHitResult hitResult = getScaffoldPlaceResult(plan);
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(entry.index(), false, ghostHand.get());
        if (callback == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
        callback.run();
        return true;
    }

    private boolean placeFill(BlockPos pos, BlockHitResult hitResult) {
        IndexEntry<ItemStack> entry = supplyFillBlock();
        if (entry == null) {
            return false;
        }
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(entry.index(), offhand.get(), ghostHand.get());
        if (callback == null) {
            return false;
        }
        mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
        InteractionTasks.handlePlaceMode(
                mode.get(), hitResult, offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get());
        callback.run();
        trackedGlasses.put(pos, Tasks.getTick());
        return true;
    }

    private boolean canBridgeFillPos(BlockPos crystalPos) {
        BlockState baseState = mc.world.getBlockState(crystalPos.down());
        boolean availableBase = baseState.isOf(Blocks.OBSIDIAN) || baseState.isOf(Blocks.BEDROCK);
        if (!availableBase && autoBase.get()) {
            if (baseState.isAir()) {
                var re = InteractionTasks.getPlaceSupportingResult(
                        crystalPos.down(), airplaceBase.get(), !mode.get().isLegal());
                if (InteractUtils.canInteractAndPlace(mc.player, re)
                        && InteractUtils.canBlockPlace(
                                mc.player, crystalPos.down(), Blocks.OBSIDIAN.getDefaultState())) {
                    availableBase = true;
                }
            }
        }
        return availableBase && !mc.world.getBlockState(crystalPos).isLiquid();
    }

    public void onPrePacketMine(Event<PacketMine.Pre> eventPreMine) {
        if (!enable.get() || eventPreMine.isCancelled()) {
            return;
        }
        BlockPos pos = eventPreMine.getArgs(0);
        Integer placeTick = trackedGlasses.get(pos);
        if (placeTick == null) {
            return;
        }
        // tracked glass
        if (SequencedActionManager.INSTANCE.isWaitingBlockUseOnResponse(
                s -> s.placingBlockPos().isPresent()
                        && Objects.equals(pos, s.placingBlockPos().get())
                        && isFillBlock(s.handItem()))) {
            eventPreMine.cancel();
            return;
        }
        BlockPos basePos = pos.down();
        boolean olderThanTwoTick = Tasks.getTick() - placeTick >= 2;
        Map<BlockPos, BlockState> overrides = Map.of(pos, Blocks.AIR.getDefaultState());
        if (olderThanTwoTick
                || !InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), basePos)
                || !canPlaceCrystalAtPos(pos, overrides)
                || !isSuitableExplodePos(calculateCrystalDamage(pos.toBottomCenterPos(), overrides))) {
            eventPreMine.cancel();
        }
    }

    private boolean checkFillPlace(BlockPos pos) {
        for (var dir : MathUtils.HORIZONTALS) {
            BlockPos nearBox = pos.offset(dir);
            Box nearBb = new Box(nearBox);
            if (mc.world.getBlockState(nearBox).getBlock().getBlastResistance() < 600
                    && currentTarget.stream().anyMatch(s -> s.getBoundingBox().intersects(nearBb))) {
                return true;
            }
        }
        return false;
    }

    public void onPostPacketMine(Event<PacketMine.Post> eventPostMine) {
        if (!enable.get()) {
            return;
        }
        BlockPos pos = eventPostMine.getArgs(0);
        float progress = eventPostMine.getArgs(1);
        if (progress <= 0.7F) {
            return;
        }
        if (currentTarget.isEmpty()) {
            return;
        }
        if (!autoFill.get()) {
            return;
        }
        if (eatingAbort.get() && mc.player.isUsingItem()) {
            return;
        }
        if (!canBridgeFillPos(pos)) {
            return;
        }
        // within range check, do not check crystal range, because human can move
        if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos.down())) {
            return;
        }
        pos = pos.toImmutable();
        Map<BlockPos, BlockState> overrides = new HashMap<>();
        overrides.put(pos, Blocks.AIR.getDefaultState());
        // help fix PacketMine fakeAirMine
        mc.world.handleBlockUpdate(pos, Blocks.AIR.getDefaultState(), 3);
        if (trackedGlasses.containsKey(pos)) {
            if (canPlaceCrystalAtPos(pos, overrides)) {
                trackedGlasses.remove(pos);
                BlockPos checkBaseAndEntity = pos.down();
                tryPlaceCrystal(checkBaseAndEntity);
                return;
            }
        }
        if (!checkFillPlace(pos)) {
            return;
        }
        if (supplyFillBlock() == null) {
            supplyFillBlockExecutor.run(100, () -> {
                if (notifySupply.get()) {
                    logI18N("message.module.crystal-aura.no-item.fill-block");
                }
            });
            return;
        }
        if (!fillIgnoreDamage.get()) {
            Map<PlayerEntity, Double> damageMap = calculateCrystalDamage(pos.toBottomCenterPos(), overrides);
            if (!isSuitableAttackExplodePos(damageMap)) {
                return;
            }
        }
        FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                pos, airplaceBase.get(), !mode.get().isLegal());
        if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
            placeFill(pos, hitResult.val());
        }
    }

    private int compareCrystalEntries(Map<PlayerEntity, Double> first, Map<PlayerEntity, Double> second) {
        double firstEnemy = getBestEnemyDamage(first);
        double secondEnemy = getBestEnemyDamage(second);
        int enemyCompare = Double.compare(secondEnemy, firstEnemy);
        if (enemyCompare != 0) {
            return enemyCompare;
        }
        double firstSelf = first.getOrDefault(mc.player, Double.POSITIVE_INFINITY);
        double secondSelf = second.getOrDefault(mc.player, Double.POSITIVE_INFINITY);
        return Double.compare(firstSelf, secondSelf);
    }

    private void onRequestCombatService(Event<CombatManager.Service> event) {
        if (enable.get()) {
            event.context().enableExplosiveSearch(true);
        }
    }

    public void onPreset(Event<EventContainer<ModulePreset>> event) {
        mode.set(LegalInteractMode.getFromPreset(event.context.getValue()));
        airplaceBase.set(!event.context.getValue().hasAC());
    }

    public record CrystalPlan(EndCrystalOption option, Optional<BlockPos> blockingPos) {}

    public static sealed interface EndCrystalOption permits AutoBase, DirectPlace {
        public BlockPos basePos();

        public boolean needBase();
    }

    public static record AutoBase(BlockPos basePos, BlockHitResult basePlaceResult) implements EndCrystalOption {

        @Override
        public boolean needBase() {
            return true;
        }
    }

    public static record DirectPlace(BlockPos basePos) implements EndCrystalOption {

        @Override
        public boolean needBase() {
            return false;
        }
    }
}
