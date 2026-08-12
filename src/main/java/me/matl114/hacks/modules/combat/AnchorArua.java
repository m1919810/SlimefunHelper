package me.matl114.hacks.modules.combat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AnchorArua extends BaseModule {
    public AnchorArua() {
        super("AnchorArua");
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.anchor-arua");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final EnumRef<Configs.LegalInteractMode> mode = builder(root.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

    public final IntRef range = intBuilder(root.add("range")).defaultValue(10).build();

    public final DoubleRef interactRange =
            doubleBuilder(root.add("interact-range")).defaultValue(4.5).build();

    public final IntRef delay = intBuilder(root.add("delay"))
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef mul = intBuilder(root.add("multiply"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef packetMineBridge =
            flagBuilder(root.add("packet-mine-bridge")).build();

    public final FlagRef place0TickSupply =
            flagBuilder(root.add("zero-tick-place-supply")).build();

    public final FlagRef use0TickSupply = flagBuilder(root.add("zero-tick-use")).build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onSwitchWorld);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvents);
        registerListener(PacketMine.getPostPacketMine(), this::onPacketMine);
        registerListener(PacketMine.getPrePacketMine(), this::onPrePacketMine);
    }

    public List<BlockPos> trackedAnchorPositions = new ArrayList<>();
    public final Map<BlockPos, Integer> interactLevel = new ConcurrentHashMap<>();
    public Entity targetEntity;

    public void onSwitchWorld(Event<World> event) {
        trackedAnchorPositions.clear();
        interactLevel.clear();
    }

    public void refreshTarget() {
        targetEntity = TargetSelector.INSTANCE.searchAttackEntity(
                range.get(), true, (entity) -> entity instanceof PlayerEntity);
    }

    public void tickAnchorPosition() {
        List<BlockPos> arr = new ArrayList<>();
        for (BlockPos pos : trackedAnchorPositions) {
            BlockState blockState = mc.world.getBlockState(pos);
            if (blockState.getBlock() instanceof RespawnAnchorBlock
                    && new Box(pos).squaredMagnitude(mc.player.getEyePos()) < MathUtils.s2(interactRange.get())) {
                arr.add(pos);
            }
        }
        if (targetEntity != null) {
            arr.sort(Comparator.comparingDouble(s -> s.getSquaredDistance(targetEntity.getPos())));
        }
        trackedAnchorPositions = arr;
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
            if (++timer > delay.get()) {
                timer = 0;
                tickAnchorExplode();
                tickAnchorPlace();
            }
        } else {
            trackedAnchorPositions.clear();
            targetEntity = null;
        }
    }

    public void tickAnchorExplode() {
        int multiply = Math.min(trackedAnchorPositions.size(), mul.get());
        for (var i = 0; i < multiply; ++i) {
            BlockPos targetPos = trackedAnchorPositions.get(i);
            litBlockPos(targetPos);
        }
    }

    boolean noItem = false;

    public IndexEntry<ItemStack> supplyItem(Item item) {
        var re = InventoryUtils.findPlayerItem(s -> s.isOf(item), true, false);
        if (re == null) {
            if (!noItem) {
                noItem = true;
                logI18N("message.module.anchor-arua.no-item", item.getName());
            }
            return null;
        } else {
            return re;
        }
    }

    public IndexEntry<ItemStack> supplyNoItem(Item item) {
        var re = InventoryUtils.findPlayerItem(s -> !s.isOf(item), true, true);
        return re == null ? InventoryUtils.getSelectedItem() : re;
    }

    public BlockHitResult createLitHitResult(BlockPos hitPos) {
        Vec3d shouldFacing = hitPos.toCenterPos().subtract(mc.player.getEyePos());
        Direction direction = Direction.getFacing(shouldFacing).getOpposite();
        return new BlockHitResult(hitPos.toCenterPos().offset(direction, 0.5), direction, hitPos, false);
    }

    public void litBlockPos(BlockPos targetPos) {
        BlockState state = mc.world.getBlockState(targetPos);
        if (state.getBlock() instanceof RespawnAnchorBlock respawn) {
            int level = interactLevel.computeIfAbsent(targetPos, (bp) -> state.get(RespawnAnchorBlock.CHARGES));
            BlockHitResult hitResult = createLitHitResult(targetPos);
            if (level == 0) {
                var glowstone = supplyItem(Items.GLOWSTONE);
                if (glowstone == null) {
                    return;
                }
                var callback = InvExtra.INSTANCE.swapInventoryIndexToHand(glowstone.index());
                if (callback == null) return;
                InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
                level = 1;
                interactLevel.put(targetPos, level);
                callback.run();
                if (!use0TickSupply.get()) {
                    return;
                }
            }
            if (level > 0) {
                var noGlowStone = supplyNoItem(Items.GLOWSTONE);
                var callback = InvExtra.INSTANCE.swapInventoryIndexToHand(noGlowStone.index());
                if (callback == null) return;
                InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
                level = 0;
                interactLevel.put(targetPos, level);
                callback.run();
                if (place0TickSupply.get()) {
                    var anchor = supplyItem(Items.RESPAWN_ANCHOR);
                    if (anchor == null) return;
                    var callback2 = InvExtra.INSTANCE.swapInventoryIndexToHand(anchor.index());
                    if (callback2 == null) return;
                    mc.world.setBlockState(targetPos, Blocks.AIR.getDefaultState());
                    InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
                    callback2.run();
                }
            }
        }
    }

    public void onPrePacketMine(Event<PacketMine.Pre> eventPreMine) {
        if (enable.get() && !eventPreMine.isCancelled()) {
            BlockPos pos = eventPreMine.getArgs(0);
            if (trackedAnchorPositions.contains(pos)
                    && mc.world.getBlockState(pos).getBlock() instanceof RespawnAnchorBlock) {
                eventPreMine.cancel();
            }
        }
    }

    public void onPacketMine(Event<PacketMine.Post> eventPostMine) {
        if (enable.get() && packetMineBridge.get() && targetEntity != null) {
            // bridge
            float floatValue = eventPostMine.getArgs(1);
            if (floatValue > 0.7f) {
                BlockPos pos = eventPostMine.getArgs(0);
                if (pos.getSquaredDistance(targetEntity.getPos()) < Math.abs(4)) {
                    trackedAnchorPositions.removeIf(pos::equals);
                    placeAnchor(pos);
                }
            }
        }
    }

    public boolean placeAnchor(BlockPos pos) {
        FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                pos, !mode.get().isLegal(), !mode.get().isLegal());
        if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
            var entry = supplyItem(Items.RESPAWN_ANCHOR);
            if (entry == null) return false;
            var runnable = InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
            if (runnable == null) return false;
            mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
            InteractionTasks.handlePlaceMode(mode.get(), hitResult.val(), Hand.MAIN_HAND, swingHand.get());
            runnable.run();
            trackedAnchorPositions.addFirst(pos);
            return true;
        } else {
            return false;
        }
    }

    public void tickAnchorPlace() {
        // todo: position select
    }
}
