package me.matl114.hacks.modules.combat;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.hacks.utils.config.EntrySet;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
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
import net.minecraft.world.World;

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

    public final EnumRef<Configs.LegalInteractMode> mode = builder(root.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

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

    public final FlagRef placeAfterAttack = builder(root.add("place-after-attack"), Boolean.class)
            .defaultValue(true)
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

    public final DoubleRef selfDamageThreshold = doubleBuilder(root.add("self-damage-threshold"))
            .defaultValue(8.0D)
            .validator(Configs.doubleRange(0.0D, 1000.0D))
            .build();

    public final DoubleRef targetDamageThreshold = doubleBuilder(root.add("target-damage-threshold"))
            .defaultValue(16.0D)
            .validator(Configs.doubleRange(0.0D, 1000.0D))
            .build();

    public final FlagRef usePredictor = builder(root.add("damage-use-predictor"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef notifySupply =
            builder(root.add("notify-supply"), Boolean.class).defaultValue(true).build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
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
    List<BlockPos> pendingBases = new ArrayList<>();
    Map<EndCrystalEntity, Integer> postRemovals = new HashMap<>();
    Map<BlockPos, Integer> trackedGlasses = new ConcurrentHashMap<>();
    int timer = 0;
    RenderCollector<Box> debugRender = RenderCollectors.createBoxCollector(true, false, false);

    public void onWorldSwitch(Event<World> event) {
        currentTarget = List.of();
        postRemovals.clear();
        trackedGlasses.clear();
        timer = 0;
    }

    public boolean attackCrystal(EndCrystalEntity entity) {
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
            tickTrackedGlasses();
            // constantly attack, only delay when place
            boolean attack = tickAttack();
            tickPendingBase();

            if ((++timer > delay.get() || (placeAfterAttack.get() && attack)) && !currentTarget.isEmpty()) {
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
        }
    }

    public void onRender3D(Event<MatrixStack> event) {
        if (enable.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                debugRender.render3D(event.context);
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
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
        if (currentTarget.isEmpty()) {
            return false;
        }
        List<Map.Entry<EndCrystalEntity, Map<PlayerEntity, Double>>> candidates = new ArrayList<>();
        for (EndCrystalEntity crystal : findAttackableCrystals()) {
            if (postRemovals.containsKey(crystal)) {
                continue;
            }
            if (!EntityUtils.isEntityValid(crystal)) {
                continue;
            }
            Map<PlayerEntity, Double> damageMap = calculateCrystalDamage(crystal.getPos());
            if (isSuitableExplodePos(damageMap)) {
                candidates.add(Map.entry(crystal, damageMap));
            }
        }
        candidates.sort(Map.Entry.comparingByValue(this::compareCrystalEntries));
        boolean attack = false;
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
        for (var re : pendingBases) {
            if (isWithinInteractRange(re) && isBasePlacedCrystalInAttackRange(re) && canPlaceCrystal(re)) {
                tryPlaceCrystal(re);
            }
        }
        pendingBases.clear();
    }

    public void tickPlace() {
        if (currentTarget.isEmpty()) {
            return;
        }
        boolean legal = mode.get().isLegal();

        EndCrystalOption bestOption = null;
        double bestEnemyDamage = Double.NEGATIVE_INFINITY;
        double bestSelfDamage = Double.POSITIVE_INFINITY;
        boolean canAutoBase = autoBase.get();
        if (canAutoBase) {
            // check
            if (supplyItem(Items.OBSIDIAN) == null) {
                supplyBaseExecutor.run(100, () -> {
                    if (notifySupply.get()) {
                        logI18N("message.module.crystal-aura.no-item.base");
                    }
                });
                canAutoBase = false;
            }
        }
        BlockPos currentPlayerPos = mc.player.getBlockPos();
        for (Vec3i delta : interactRangeBlocks) {
            BlockPos pos = currentPlayerPos.add(delta);
            if (!isWithinInteractRange(pos)) {
                continue;
            }
            if (!isBasePlacedCrystalInAttackRange(pos)) {
                continue;
            }
            if (hasCrystalBlockingEntity(pos)) {
                continue;
            }
            boolean canPlaceCrystal = canPlaceCrystal(pos);
            EndCrystalOption option;
            if (canPlaceCrystal) {
                option = new DirectPlace(pos);
            } else if (canAutoBase && isSuitableBasePos(pos)) {
                FlagEntry<BlockHitResult> hitResultEntry =
                        InteractionTasks.getPlaceSupportingResult(pos, airplaceBase.get(), !legal);
                if (InteractUtils.canInteractAndPlace(mc.player, hitResultEntry)
                        && isWithinInteractRange(hitResultEntry.val().getBlockPos())) {
                    option = new AutoBase(pos, hitResultEntry.val());
                } else {
                    continue;
                }
            } else {
                continue;
            }

            Map<PlayerEntity, Double> damageMap = canPlaceCrystal
                    ? calculateCrystalDamage(getCrystalExplosionPos(pos))
                    : calculateCrystalDamage(
                            getCrystalExplosionPos(pos), Map.of(pos, Blocks.OBSIDIAN.getDefaultState()));
            if (!isSuitableExplodePos(damageMap)) {
                continue;
            }

            double enemyDamage = getBestEnemyDamage(damageMap);
            double selfDamage = damageMap.getOrDefault(mc.player, Double.POSITIVE_INFINITY);
            double damageValue = enemyDamage * (option.needBase() ? baseReduce.get() : 1.0D);
            if (damageValue > bestEnemyDamage || (damageValue == bestEnemyDamage && selfDamage < bestSelfDamage)) {
                bestOption = option;
                bestEnemyDamage = damageValue;
                bestSelfDamage = selfDamage;
            }
        }

        if (bestOption == null) {
            return;
        }
        debugRender.submit(new Box(bestOption.basePos()), ColorUtils.withAlphaInt(Color.MAGENTA, 255));
        if (bestOption.needBase()) {
            placeBase(bestOption.basePos(), ((AutoBase) bestOption).basePlaceResult());
        } else {
            tryPlaceCrystal(bestOption.basePos());
        }
    }

    private Map<PlayerEntity, Double> calculateCrystalDamage(Vec3d explosionPos) {
        return calculateCrystalDamage(explosionPos, Map.of());
    }

    private Map<PlayerEntity, Double> calculateCrystalDamage(Vec3d explosionPos, Map<BlockPos, BlockState> overrides) {
        Map<PlayerEntity, Double> damageMap = new LinkedHashMap<>();
        if (mc.world == null || mc.player == null) {
            return damageMap;
        }
        var access = overrides.isEmpty()
                ? ExplosionUtils.fromWorld(mc.world)
                : ExplosionUtils.fromWorldWithOverrides(mc.world, overrides);
        damageMap.put(mc.player, (double) ExplosionUtils.calculateExplosionRawDamage(
                ExplosionUtils.END_CRYSTAL_POWER,
                explosionPos,
                mc.player.getBoundingBox(),
                access,
                ExplosionUtils.EXPLOSION_RESISTENCE));
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

    private boolean isSuitableAttackPos(Map<PlayerEntity, Double> damageMap) {
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
        if (selfDamage > selfDamageThreshold.get()) {
            return false;
        }
        return getBestEnemyDamage(damageMap) >= targetDamageThreshold.get();
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

    private boolean isWithinInteractRange(BlockPos pos) {
        return new Box(pos).squaredMagnitude(mc.player.getEyePos()) <= MathUtils.s2(interactRange.get());
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
        return EntityUtils.isEntityValid(entity)
                && (!(entity instanceof EndCrystalEntity crystal) || !postRemovals.containsKey(crystal));
    }

    private boolean hasCrystalBlockingEntity(BlockPos crystalPos) {
        return !mc.world
                .getOtherEntities(null, new Box(crystalPos), this::canBlockCrystalPlace)
                .isEmpty();
    }

    private boolean canPlaceCrystalAtPos(BlockPos crystalPos, Map<BlockPos, BlockState> overrides) {
        ExplosionUtils.BlockStateAccess stateAccess = ExplosionUtils.fromWorldWithOverrides(mc.world, overrides);
        BlockState crystalState = stateAccess.getBlockState(crystalPos);
        if (crystalState == null || !crystalState.isAir()) {
            return false;
        }
        BlockState baseState = stateAccess.getBlockState(crystalPos.down());
        if (baseState == null || !isCrystalBase(baseState)) {
            return false;
        }
        return !hasCrystalBlockingEntity(crystalPos);
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
        return InventoryUtils.findPlayerItem(stack -> stack.isOf(item), true, false);
    }

    private IndexEntry<ItemStack> supplyFillBlock() {
        return InventoryUtils.findPlayerItem(
                stack -> {
                    if (!(stack.getItem() instanceof BlockItem blockItem)
                            || !fillWhiteList.get().test(stack.getItem())) {
                        return false;
                    }
                    return true;
                },
                true,
                false);
    }

    private IndexEntry<ItemStack> supplyCrystalItem() {
        return InventoryUtils.findPlayerItem(stack -> stack.isOf(Items.END_CRYSTAL), true, false);
    }

    private boolean shouldKeepTrackedGlass(BlockPos glassPos) {
        if (fillIgnoreDamage.get()) {
            return true;
        }
        Map<BlockPos, BlockState> overrides =
                Map.of(glassPos, Blocks.AIR.getDefaultState(), glassPos.down(), Blocks.OBSIDIAN.getDefaultState());

        Map<PlayerEntity, Double> damageMap = calculateCrystalDamage(glassPos.toBottomCenterPos(), overrides);
        return isSuitableAttackPos(damageMap);
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
        Runnable callback = offhand.get()
                ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(entry.index())
                : InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
        if (callback == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(
                mode.get(),
                RaycastUtils.createHitResult(basePos, mc.player.getEyePos()),
                offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND,
                swingHand.get());
        callback.run();
        return true;
    }

    private boolean placeBase(BlockPos pos, BlockHitResult hitResult) {
        IndexEntry<ItemStack> entry = supplyItem(Items.OBSIDIAN);
        if (entry == null) {
            return false;
        }
        Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
        if (callback == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
        callback.run();
        if (zeroTickBase.get()
                && DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(
                        mode.get().canMultiRotPlace())) {
            tryPlaceCrystal(pos);
        } else {
            pendingBases.add(pos);
        }
        return true;
    }

    private boolean placeFill(BlockPos pos, BlockHitResult hitResult) {
        IndexEntry<ItemStack> entry = supplyFillBlock();
        if (entry == null) {
            return false;
        }
        Runnable callback = offhand.get()
                ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(entry.index())
                : InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
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
        BlockPos basePos = pos.down();
        boolean olderThanTwoTick = Tasks.getTick() - placeTick > 2;
        Map<BlockPos, BlockState> overrides = Map.of(pos, Blocks.AIR.getDefaultState());
        if (!olderThanTwoTick
                || !isWithinInteractRange(basePos)
                || !isBasePlacedCrystalInAttackRange(basePos)
                || !canPlaceCrystalAtPos(pos, overrides)
                || !isSuitableExplodePos(calculateCrystalDamage(pos.toBottomCenterPos(), overrides))) {
            eventPreMine.cancel();
        }
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
        if (!canBridgeFillPos(pos)) {
            return;
        }
        // within range check, do not check crystal range, because human can move
        if (!isWithinInteractRange(pos.down())) {
            return;
        }
        pos = pos.toImmutable();
        Map<BlockPos, BlockState> overrides = new HashMap<>();
        overrides.put(pos, Blocks.AIR.getDefaultState());
        // help fix PacketMine fakeAirMine
        mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
        if (trackedGlasses.containsKey(pos)) {
            if (canPlaceCrystalAtPos(pos, overrides)) {
                trackedGlasses.remove(pos);
                BlockPos checkBaseAndEntity = pos.down();
                tryPlaceCrystal(checkBaseAndEntity);
                return;
            }
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
            if (!isSuitableAttackPos(damageMap)) {
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
        mode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
        airplaceBase.set(!event.context.getValue().hasAC());
    }

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
