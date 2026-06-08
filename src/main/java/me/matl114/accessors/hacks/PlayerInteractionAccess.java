package me.matl114.accessors.hacks;

import javax.annotation.Nullable;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public interface PlayerInteractionAccess {
    public void startMiningBlock(BlockPos pos, Direction direction);

    public void sendBreakPacket(BlockPos pos, Direction direction);

    public void syncSelectedHotbar(int x);

    public boolean preCalculateInstantBreak(BlockPos pos);

    public float calculateBreakingSpeed(BlockPos pos);

    public BlockPos getCurrentMiningPos();

    public void resetCurrentMiningPos();

    public BlockPos getCurrentFailBreakPos();

    public boolean isFailBreakEmpty();

    public boolean beginFailBreak(BlockPos pos);

    public boolean moveCurrentMiningToFailBreak();

    public void clearFailBreak();

    public float getFailBreakMiningProgress();

    public float predictCurrentMiningProgressWithTool(ItemStack tool);

    public float getCurrentMiningProgress(boolean shouldPredict);

    default void sendStartBreakPacket(BlockPos pos, Direction direction) {
        startMiningBlock(pos, direction);
    }

    default void sendStopBreakPacket(BlockPos pos, Direction direction) {
        sendBreakPacket(pos, direction);
    }

    default boolean setStartFailBreakPos(@Nullable BlockPos pos) {
        if (pos == null) {
            clearFailBreak();
            return true;
        }
        return beginFailBreak(pos);
    }

    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager) {
        return (PlayerInteractionAccess) manager;
    }
}
