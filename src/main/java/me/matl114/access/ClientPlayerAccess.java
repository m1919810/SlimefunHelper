package me.matl114.access;

import me.matl114.utils.UtilClass.ProgressWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenHandler;

import javax.annotation.Nonnull;

public interface ClientPlayerAccess {
    public HandledScreen getKeepedInv();
    public ScreenHandler getKeepedInvHandler();
    public void clearKeepedInventory(boolean closeInv);
    public void syncPitchYaw();
    public void syncLocationPackets();
    public boolean isContinueMoving();
    public void addMovementPacketWrapper(ProgressWrapper<ClientPlayerEntity> wrapper);
    @Nonnull
    public static ClientPlayerAccess of(@Nonnull ClientPlayerEntity player) {
        return (ClientPlayerAccess) player;
    }
    //get the Screen object which handler related to the server(should)
    default HandledScreen getServerHandledScreen(){
        if(getKeepedInv() !=null)return getKeepedInv();
        else return MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> han?han:null;
    }
}
