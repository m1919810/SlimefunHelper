package me.matl114.accessors.hacks;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public interface PlayerInteractionAccess {
    public void sendStartBreakPacket(BlockPos pos, Direction direction);

    public void sendStopBreakPacket(BlockPos pos, Direction direction);

    public void syncSelectedHotbar(int x);

    public boolean preCalculateInstantBreak(BlockPos pos);

    public float calculateBreakingSpeed(BlockPos pos);

    public BlockPos getCurrentMiningPos();

    public void resetCurrentMiningPos();

    public BlockPos getCurrentFailBreakPos();

    public boolean setStartFailBreakPos(BlockPos pos);

    public float getFailBreakMiningProgress();

    public float predictCurrentMiningProgressWithTool(ItemStack tool);

    public float getCurrentMiningProgress(boolean shouldPredict);

    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager) {
        return (PlayerInteractionAccess) manager;
    }
}
