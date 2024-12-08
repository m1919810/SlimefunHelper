package me.matl114.Access;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public interface PlayerInteractionAccess  {
    public void autoSendStopPacket();
    public void sendStopPacket(BlockPos pos, Direction direction);
    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager){
        return (PlayerInteractionAccess)manager;
    }
}
