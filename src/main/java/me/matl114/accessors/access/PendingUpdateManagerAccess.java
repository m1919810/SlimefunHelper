package me.matl114.accessors.access;

import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.util.math.BlockPos;

public interface PendingUpdateManagerAccess {
    public static PendingUpdateManagerAccess of(PendingUpdateManager manager) {
        return (PendingUpdateManagerAccess) manager;
    }

    public Optional<BlockState> getPendingBlockState(int sequenceNumber, BlockPos pos);
}
