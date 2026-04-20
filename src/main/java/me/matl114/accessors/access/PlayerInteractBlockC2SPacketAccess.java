package me.matl114.accessors.access;

import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public interface PlayerInteractBlockC2SPacketAccess {
    void setHand(Hand hand);

    void setBlockHitResult(BlockHitResult blockHitResult);

    void setSequence(int sequence);

    static PlayerInteractBlockC2SPacketAccess of(PlayerInteractBlockC2SPacket packet) {
        return (PlayerInteractBlockC2SPacketAccess) packet;
    }
}
