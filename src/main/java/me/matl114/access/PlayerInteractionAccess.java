package me.matl114.access;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public interface PlayerInteractionAccess  {
    public void autoSendStopPacket();
    public void sendStartBreakPacket(BlockPos pos, Direction direction);
    public void sendStopBreakPacket(BlockPos pos, Direction direction);
    public boolean preCalculateInstantBreak(BlockPos pos);
    public float calculateBreakingSpeed(BlockPos pos);
    static PlayerInteractionAccess of(ClientPlayerInteractionManager manager){
        return (PlayerInteractionAccess)manager;
    }
    public RecipeEntry<?> getLastlyCrafted();
    public void setLastlyCrafted(RecipeEntry<?> recipe);
    public void toggleRecipeLock();
    public boolean getRecipeLock();
}
