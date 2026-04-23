package me.matl114.hacks.modules.interact;

import com.google.common.util.concurrent.Runnables;
import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.accessors.moonrise.MoonriseBlockStateBaseAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
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

    public final FlagRef enable = flagBuilder(Configs.INTERACT_CONFIG, ENABLE).build();

    public final KeyBindRef keyBind = toggleHotkey(Configs.INTERACT_CONFIG, ENABLE_HOTKEY, new MultiKeyBind(), ENABLE)
            .build();

    public final FlagRef legal =
            flagBuilder(Configs.INTERACT_CONFIG, INTERACT_SCAFFOLD_LEGAL).build();

    public final EnumRef<Configs.LegalInteractMode> legalMode = builder(
                    Configs.INTERACT_CONFIG, INTERACT_SCAFFOLD_TARGET_MODE, Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.USEITEM_PACKET)
            .build();

    public final FlagRef keepInHand = flagBuilder(
                    Configs.INTERACT_CONFIG, makePath("interact-scaffold.keep-block-in-hand"))
            .build();

    public final IntRef expandYDepth = builder(
                    Configs.INTERACT_CONFIG, makePath("interact-scaffold.expand-interact-y-depth"), IntRef.TYPE)
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
    }

    private void placeBlockLegally(int hand, BlockHitResult result) {
        int selected = mc.player.getInventory().getSelectedSlot();
        boolean shouldInv = hand != mc.player.getInventory().getSelectedSlot();
        int swapped = -1;
        if (shouldInv) {
            if (hand < 9) {
                PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(hand);
            } else {
                MovTasks.getMovExtra().sendPacketsForInventoryAction();
                OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), hand);
                if (slotIndex.isPresent()) {
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            slotIndex.getAsInt(),
                            selected,
                            SlotActionType.SWAP,
                            mc.player);
                    swapped = slotIndex.getAsInt();
                } else return;
            }
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

            if (legal.get()) {
                var mode = legalMode.get();
                // todo: delay movement fix
                switch (mode) {
                    case USEITEM_PACKET -> placeBlockUseItem(Hand.MAIN_HAND, result);
                    case DELAY_MOVEMENT -> placeBlockDelayMovement(Hand.MAIN_HAND, result);
                    case MOVEMENT -> placeBlockMovement(Hand.MAIN_HAND, result);
                }
            } else {
                InteractionTasks.placeBlock(Hand.MAIN_HAND, result);
            }
        } finally {
            if (shouldInv && !keepInHand.get()) {
                PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
                if (swapped >= 0) {
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, swapped, selected, SlotActionType.SWAP, mc.player);
                }
            }
        }
    }

    private void placeBlockUseItem(Hand hand, BlockHitResult result) {
        Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                .toCenterPos()
                .subtract(mc.player.getEyePos())
                .normalize());
        mc.interactionManager.sendSequencedPacket(
                mc.world, (i) -> new PlayerInteractItemC2SPacket(hand, i, rotation.y, rotation.x));
        InteractionTasks.placeBlock(Hand.MAIN_HAND, result);

        return;
    }

    private void placeBlockDelayMovement(Hand hand, BlockHitResult result) {
        InteractionTasks.placeBlock(hand, result);
        InteractionTasks.addPostRotationCorrectTask(result.getBlockPos().toCenterPos(), Runnables.doNothing());
    }

    private void placeBlockMovement(Hand hand, BlockHitResult result) {
        Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                .toCenterPos()
                .subtract(mc.player.getEyePos())
                .normalize());
        mc.getNetworkHandler()
                .sendPacket(VPacket.newLookAndOnGround(
                        rotation.y, rotation.x, mc.player.isOnGround(), mc.player.horizontalCollision));
        InteractionTasks.placeBlock(hand, result);
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
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.getSelectedSlot();
        if (!item.isEmpty() && availableItemBlocks.contains(item.getItem())) {
            // make position estimate, 2ticks after current position

            return selecedSlot;
            // the supporting block cannot support the player
            // the supporting block can be replaced
        }
        if (mc.player.currentScreenHandler.syncId != mc.player.playerScreenHandler.syncId) {
            return -1;
        }
        for (var i = 0; i < pinv.size(); ++i) {
            ItemStack stack = pinv.getStack(i);
            if (!stack.isEmpty() && availableItemBlocks.contains(stack.getItem())) {
                //                if(keepInHand.get()){
                //                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                //                    OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(pinv, i);
                //                    if(slotIndex.isPresent()){
                //                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                // slotIndex.getAsInt(), selecedSlot, SlotActionType.SWAP, mc.player);
                //                        return selecedSlot;
                //                    }
                //                }else
                return i;
            }
        }
        return -1;

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
        if (!legal.get()) {
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
            case HACKING, VANILLA -> legal.set(false);
            default -> legal.set(true);
        }
    }
}
