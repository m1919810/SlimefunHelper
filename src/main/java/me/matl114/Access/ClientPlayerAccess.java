package me.matl114.Access;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenHandler;

import javax.annotation.Nonnull;

public interface ClientPlayerAccess {
    public HandledScreen getKeepedInv();
    public ScreenHandler getKeepedInvHandler();
    public void clearKeepedInventory(boolean closeInv);
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
