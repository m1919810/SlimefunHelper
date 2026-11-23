package me.matl114.accessors.access;

import java.util.Optional;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.ActionResult;
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

    public static record UseContext(ItemStack stack, ActionResult actionResult, Optional<BlockPos> placePos) {
        public boolean isEmpty() {
            return stack.isEmpty() || !(stack.getItem() instanceof BlockItem);
        }

        public boolean isAccepted() {
            return actionResult.isAccepted();
        }
    }

    static PlayerInteractBlockC2SPacketAccess of(PlayerInteractBlockC2SPacket packet) {
        return (PlayerInteractBlockC2SPacketAccess) packet;
    }
}
