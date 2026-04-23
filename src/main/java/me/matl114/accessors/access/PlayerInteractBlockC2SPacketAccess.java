package me.matl114.accessors.access;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public interface PlayerInteractBlockC2SPacketAccess {
    void setHand(Hand hand);

    void setBlockHitResult(BlockHitResult blockHitResult);

    void setSequence(int sequence);

    UseContext getUseContext();

    default boolean hasUseContext() {
        return getUseContext() != null;
    }

    void setUseContext(UseContext stack);

    public static record UseContext(ItemStack stack, BlockState oldState) {
        public boolean isEmpty() {
            return stack.isEmpty() || !(stack.getItem() instanceof BlockItem);
        }

        public BlockPos getPlaceBlockPos(Hand hand, BlockHitResult blockHitResult) {
            // optimize air place
            if (oldState.isAir()) {
                return blockHitResult.getBlockPos();
            } else {
                return new ItemPlacementContext(MinecraftClient.getInstance().player, hand, stack, blockHitResult)
                        .getBlockPos();
            }
        }
    }

    static PlayerInteractBlockC2SPacketAccess of(PlayerInteractBlockC2SPacket packet) {
        return (PlayerInteractBlockC2SPacketAccess) packet;
    }
}
