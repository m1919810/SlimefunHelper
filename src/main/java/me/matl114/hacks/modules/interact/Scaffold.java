package me.matl114.hacks.modules.interact;

import java.util.*;
import java.util.List;
import me.matl114.accessors.moonrise.MoonriseBlockStateBaseAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.RaycastUtils;
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
    public static final String[] ENABLE = {"interact-scaffold", "scaffold"};
    public static final String[] ENABLE_HOTKEY = {"interact-scaffold", "scaffold-hotkey"};
    public static final String[] INTERACT_SCAFFOLD_LEGAL = {"interact-scaffold", "legal-mode"};
    public static final String[] INTERACT_SCAFFOLD_TARGET_MODE = {"interact-scaffold", "legal-targeting"};
    public static final String[] INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE = {
        "interact-scaffold", "scaffold-cooldown-override"
    };

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

    public final FlagRef swapHand = flagBuilder(scaffold.add("swap-hand"))
            .build();

    public final IntRef expandYDepth = builder(
                    scaffold.add("expand-interact-y-depth"), IntRef.TYPE)
            .defaultValue(0)
            .validator(Configs.intRange(0, 3))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onRightClick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetReload);
    }

    private void placeBlockLegally(int hand, BlockHitResult result) {
        Runnable callback = swapHand.get()
                ? InvTasks.getInvExtra().switchOrSwapInventoryIndexToHand(hand)
                : InvTasks.getInvExtra().swapInventoryIndexToHand(hand);
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
                    InteractionTasks.placeBlock(Hand.MAIN_HAND, result1);
                    return;
                }
            }

            if (legalMode.get().isLegal()) {
                var mode = legalMode.get();
                // todo: delay movement fix
                InteractionTasks.handlePlaceMode(mode, result, Hand.MAIN_HAND);
            } else {
                InteractionTasks.placeBlock(Hand.MAIN_HAND, result);
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
            Vec3d playerPos = mc.player.getLerpedPos(2.0F); // mc.player.getPos();
            // do not predict y level
            playerPos = new Vec3d(playerPos.x, mc.player.getY(), playerPos.z);

            BlockPos testPos1 = BlockPos.ofFloored(playerPos.subtract(0, 0.500001F, 0));
            BlockState blockState = mc.world.getBlockState(testPos1);
            // test if the supporting block can support player
            if (!blockState.isAir()
                    && !MoonriseBlockStateBaseAccess.of(blockState).isConstantCollisionShapeEmpty()) {
                // if player is on a slab or something
                //  Debug.chat("has");
                //  Debug.chat("ret 1");
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
            // Debug.chat("nothing");

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

    public BlockHitResult guessTheBestPlacePositionForTargetingBlock(Vec3d predictedPos, BlockPos pos) {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hitResult = ((BlockHitResult) mc.crosshairTarget);
            BlockPos targetPos = hitResult.getBlockPos();
            Direction dir = hitResult.getSide();
            BlockPos estimatePlacingPos = targetPos.offset(dir);
            // use vanilla
            if (Objects.equals(estimatePlacingPos, pos)) {
                return hitResult;
            }
        }
        BlockHitResult hitResult = createHitNormal(predictedPos, pos);
        if (hitResult != null) return hitResult;
        if (!legalMode.get().isLegal()) {
            // not legal, we can airplace
            return RaycastUtils.createHitResult(pos.offset(Direction.DOWN), Direction.UP);
        }
        // todo find better block to place,
        // todo copy copy
        //
        //
        //
        for (var vec3d : searchOffsets) {
            if (vec3d.getY() >= -expandYDepth.get()) {
                BlockPos checkPos = pos.add(vec3d);
                BlockState state = mc.world.getBlockState(checkPos);
                // filter can place blocks
                if (state.isReplaceable()) {
                    //                    RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                    //                        2, new RenderTasks.BoxObject(Vec3d.of(checkPos),
                    // Vec3d.of(checkPos).add(1,1,1), Color.MAGENTA)));

                    hitResult = createHitNormal(predictedPos, checkPos);
                    if (hitResult != null) return hitResult;
                }
            }
        }

        return null;
    }

    public BlockHitResult createHitNormal(Vec3d predictedPos, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos testPos = pos.offset(direction);
            BlockState state = mc.world.getBlockState(testPos);
            // fixme donot place on liquid,
            // air liquidplace
            if (!state.isAir() && !state.isLiquid()) {
                Vec3d targetSeePos = pos.toCenterPos().offset(direction, 0.5);
                Vec3d iSee = mc.player.getEyePos().subtract(targetSeePos);
                if (iSee.dotProduct(direction.getDoubleVector()) < 0.0) {
                    return RaycastUtils.createHitResult(testPos, direction.getOpposite());
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
