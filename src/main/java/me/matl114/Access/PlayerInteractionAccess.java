package me.matl114.Access;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public interface PlayerInteractionAccess  {
    public void autoSendStopPacket();
    public void sendStartPacket(BlockPos pos,Direction direction);
    public void sendStopPacket(BlockPos pos, Direction direction);
    public boolean preCalculateInstantBreak(BlockPos pos);
    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager){
        return (PlayerInteractionAccess)manager;
    }
}
