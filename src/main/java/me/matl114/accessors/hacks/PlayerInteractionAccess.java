package me.matl114.accessors.hacks;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public interface PlayerInteractionAccess {
    public void autoSendStopPacket();

    public void sendStartBreakPacket(BlockPos pos, Direction direction);

    public void sendStopBreakPacket(BlockPos pos, Direction direction);

    public boolean preCalculateInstantBreak(BlockPos pos);

    public float calculateBreakingSpeed(BlockPos pos);

    public BlockPos getCurrentMiningPos();

    public BlockPos getCurrentFailBreakPos();

    public float getFailBreakMiningProgress();

    public float getCurrentMiningProgress(boolean shouldPredict);

    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager) {
        return (PlayerInteractionAccess) manager;
    }
}
