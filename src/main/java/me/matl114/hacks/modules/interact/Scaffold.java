package me.matl114.hacks.modules.interact;

import java.util.*;
import java.util.List;
import me.matl114.accessors.moonrise.MoonriseBlockStateBaseAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.EmptyBlockView;

public class Scaffold extends BaseModule {
    //    public static final String[] ENABLE = {"interact-scaffold", "scaffold"};
    //    public static final String[] ENABLE_HOTKEY = {"interact-scaffold", "scaffold-hotkey"};
    //    public static final String[] INTERACT_SCAFFOLD_LEGAL = {"interact-scaffold", "legal-mode"};
    //    public static final String[] INTERACT_SCAFFOLD_TARGET_MODE = {"interact-scaffold", "legal-targeting"};
    //    public static final String[] INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE = {
    //        "interact-scaffold", "scaffold-cooldown-override"
    //    };

    public Scaffold() {
        bindFlag(enable);
    }

    List<Vec3i> searchOffsets;

    public void updateSearchRange(int range) {
        searchOffsets = new ArrayList<>();
        for (int x = -range; x <= range; x++) {
            for (int y = -3; y <= 0; y++) { // y <= 0
                for (int z = -range; z <= range; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // 过滤零点
                    searchOffsets.add(new Vec3i(x, y, z));
                }
            }
        }
        searchOffsets.sort(Comparator.comparingInt(
                v -> (int) Math.max(Math.max(Math.abs(v.getX()), Math.abs(v.getY())), Math.abs(v.getZ()))));
    }

    final ModulePath scaffold = makePath(Configs.INTERACT_CONFIG, "interact-scaffold");

    public final FlagRef enable = flagBuilder(scaffold.addEnable()).build();

    public final KeyBindRef keyBind = moduleEntry(scaffold.addHotkey(), new MultiKeyBind(), scaffold.addEnable())
            .build();

    //    public final FlagRef legal =
    //            flagBuilder(Configs.INTERACT_CONFIG, INTERACT_SCAFFOLD_LEGAL).build();

    public final EnumRef<Configs.LegalInteractMode> legalMode = builder(
                    scaffold.add("legal-targeting"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.USEITEM_PACKET)
            .build();

    public final FlagRef offhand = flagBuilder(scaffold.add("offhand-enable")).build();

    public final FlagRef swapHand = flagBuilder(scaffold.add("swap-hand")).build();

    public final IntRef expandYDepth = builder(scaffold.add("expand-interact-y-depth"), IntRef.TYPE)
            .defaultValue(0)
            .validator(Configs.intRange(0, 3))
            .build();

    public final IntRef expandInteractRange = builder(
                    Configs.INTERACT_CONFIG, makePath("interact-scaffold.expand-interact-range"), IntRef.TYPE)
            .defaultValue(1)
            .updateListener(this::updateSearchRange)
            .validator(Configs.intRange(0, 3))
            .build();

    //    public final IntRef cooldownOverride = builder(
    //                    Configs.INTERACT_CONFIG, INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE, IntRef.TYPE)
    //            .defaultValue(-1)
    //            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onRightClick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetReload);
    }

    private void placeBlockLegally(int hand, BlockHitResult result) {
        boolean offhandOk = offhand.get();
        Runnable callback = offhandOk
                ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(hand)
                : (swapHand.get()
                        ? InvExtra.INSTANCE.switchOrSwapInventoryIndexToHand(hand)
                        : InvExtra.INSTANCE.swapInventoryIndexToHand(hand));
        if (callback == null) {
            return;
        }
        try {
            if (mc.crosshairTarget instanceof BlockHitResult result1) {
                // same block same side
                // use vanilla crosshairtarget
                if (Objects.equals(result1.getBlockPos(), result.getBlockPos())
                        && Objects.equals(result1.getSide(), result.getSide())
                        && Objects.equals(result1.getType(), result.getType())) {
                    InteractionTasks.placeBlock(offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND, result1);
                    return;
                }
            }

            if (legalMode.get().isLegal()) {
                var mode = legalMode.get();
                // todo: delay movement fix
                InteractionTasks.handlePlaceMode(mode, result, offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND);
            } else {
                InteractionTasks.placeBlock(offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND, result);
            }
        } finally {
            callback.run();
        }
    }

    private Set<Item> availableItemBlocks;

    public int supplyBlock() {
        if (availableItemBlocks == null) {
            availableItemBlocks = new HashSet<>();
            for (var item : Registries.ITEM) {
                if (item instanceof BlockItem blockItem
                        && !blockItem.getBlock().getDefaultState().isAir()
                        && blockItem
                                .getBlock()
                                .getDefaultState()
                                .isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
                    availableItemBlocks.add(blockItem);
                }
            }
        }
        // do not consider offHand, because some game do not support
        IndexEntry<ItemStack> stackEntry =
                InventoryUtils.findPlayerItem((item) -> availableItemBlocks.contains(item.getItem()), true, false);
        return stackEntry == null ? -1 : stackEntry.index();

        // search block in backpack
    }

    public void onRightClick(Event<Void> rightClickEvent) {

        // check scaffold when player right pressed the mouse
        // todo: check this
        if (mc.player != null && enable.get()) {
            // check hand item
            int idx = supplyBlock();

            if (idx < 0) {
                return;
            }
            // Debug.chat("tick", ClientAccess.of(mc).getCooldown());
            // check if we can have any scaffold
            // todo add lerp to config
            Vec3d playerPos = mc.player.getPos(); // mc.player.getPos();
            // do not predict y level
            playerPos = new Vec3d(playerPos.x, mc.player.getY(), playerPos.z);

            BlockPos testPos1 = BlockPos.ofFloored(playerPos.subtract(0, 0.500001F, 0));
            BlockState blockState = mc.world.getBlockState(testPos1);
            // test if the supporting block can support player
            if (!blockState.isAir()
                    && !MoonriseBlockStateBaseAccess.of(blockState).isConstantCollisionShapeEmpty()) {

                return;
            }

            if (blockState.isReplaceable()) {
                BlockHitResult hitResult = guessTheBestPlacePositionForTargetingBlock(
                        playerPos.add(0, mc.player.dimensions.eyeHeight(), 0), testPos1);
                if (hitResult != null) {
                    // Debug.chat("interact", hitResult.getBlockPos(), hitResult.getSide(), hitResult.getPos());
                    placeBlockLegally(idx, hitResult);
                    // todo should we autostack

                    return;
                }
            }
        }
    }

    //    //fixme delete log
    //    //fixme lefthand work
    //    //fixme speed effect
    //    private int lastScaffoldTick = 0;
    //    public void onStopUseItem(Event<Hand> eventUseItem){
    //        if(Tasks.getTick() < lastScaffoldTick + 3){
    //            eventUseItem.cancel();
    //        }
    //    }

    public BlockHitResult guessTheBestPlacePositionForTargetingBlock(Vec3d predictedEyePos, BlockPos pos) {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hitResult = ((BlockHitResult) mc.crosshairTarget);
            BlockPos targetPos = hitResult.getBlockPos();
            Direction dir = hitResult.getSide();
            BlockPos estimatePlacingPos = targetPos.offset(dir);
            // use vanilla
            if (InteractUtils.canCubePlace(mc.player, estimatePlacingPos) && Objects.equals(estimatePlacingPos, pos)) {
                return hitResult;
            }
        }
        BlockHitResult hitResult;
        boolean enableAirPlace = !legalMode.get().isLegal();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isReplaceable() && InteractUtils.canCubePlace(mc.player, pos)) {
            hitResult = InteractionTasks.getPlaceSupportingResult(predictedEyePos, pos, enableAirPlace, enableAirPlace);
            if (hitResult != null) return hitResult;
        }

        for (var vec3d : searchOffsets) {
            if (vec3d.getY() >= -expandYDepth.get()) {
                BlockPos checkPos = pos.add(vec3d);
                state = mc.world.getBlockState(checkPos);
                // filter can place blocks
                if (state.isReplaceable() && InteractUtils.canCubePlace(mc.player, checkPos)) {
                    hitResult = InteractionTasks.getPlaceSupportingResult(
                            checkPos,
                            !legalMode.get().isLegal(),
                            !legalMode.get().isLegal());
                    if (hitResult != null) return hitResult;
                }
            }
        }

        return null;
    }

    public void onPresetReload(Event<EventContainer<ModulePreset>> event) {
        switch (event.context().getValue()) {
            case AC_GRIM_LEGACY -> legalMode.set(Configs.LegalInteractMode.LEGACY_SLIENT_ROT);
            case HACKING, VANILLA -> legalMode.set(Configs.LegalInteractMode.NONE);
            default -> legalMode.set(Configs.LegalInteractMode.DELAY_MOVEMENT);
        }
    }
}
