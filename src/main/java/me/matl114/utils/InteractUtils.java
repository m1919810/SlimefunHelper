package me.matl114.utils;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class InteractUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Nullable
    public static BlockState getBlockPlacement(
            Block block, PlayerEntity player, World world, BlockHitResult blockHitResult) {
        Item blockItem = block.asItem();
        return blockItem instanceof BlockItem blockItem1
                ? getBlockPlacement(blockItem1, player, world, blockHitResult)
                : null;
    }

    @Nullable
    public static BlockState getBlockPlacement(
            BlockItem blockItem, PlayerEntity player, World world, BlockHitResult blockHitResult) {
        ItemPlacementContext placement =
                new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(blockItem), blockHitResult);
        placement = blockItem.getPlacementContext(placement);
        return blockItem.getPlacementState(placement);
    }

    public static boolean canCubePlace(PlayerEntity player, BlockPos pos) {
        // cube
        World world = player.getEntityWorld();
        BlockState state = Blocks.STONE.getDefaultState();
        return state.canPlaceAt(world, pos) && world.canPlace(state, pos, ShapeContext.ofPlacement(player));
    }

    public static BlockPos getCurrentPlacePos(PlayerEntity player, BlockHitResult blockHitResult) {
        ItemPlacementContext placement =
                new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(Blocks.STONE), blockHitResult);
        return placement.getBlockPos();
    }

    public static boolean canCubePlace(PlayerEntity player, BlockHitResult state) {
        BlockPos pos = getCurrentPlacePos(player, state);
        return canCubePlace(player, pos);
    }

    public static ActionResult simulateInteract(EntityHitResult entityHitResult) {
        ActionResult actionResult = mc.interactionManager.interactEntityAtLocation(
                mc.player, entityHitResult.getEntity(), entityHitResult, Hand.MAIN_HAND);
        if (!actionResult.isAccepted()) {
            actionResult = mc.interactionManager.interactEntity(mc.player, entityHitResult.getEntity(), Hand.MAIN_HAND);
        }

        if (actionResult instanceof ActionResult.Success) {
            ActionResult.Success success = (ActionResult.Success) actionResult;
            if (success.swingSource() == ActionResult.SwingSource.CLIENT) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
        return actionResult;
    }
}
