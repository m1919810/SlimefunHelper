package me.matl114.access;

import me.matl114.utils.UtilClass.LegalMovementManager;
import me.matl114.utils.UtilClass.ProgressWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nonnull;

public interface ClientPlayerAccess extends LivingEntityAccess<ClientPlayerEntity> {
    public LegalMovementManager getLegalMovementManager();
    public HandledScreen getKeepedInv();
    public ScreenHandler getKeepedInvHandler();
    public void clearKeepedInventory(boolean closeInv);
    public void resyncSprint();
    public void resyncSneak();
    public void resyncPos();
    public void resyncRot();
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



    public boolean isForceNoFall();
    public void setForceNoFall(boolean fall);

    public abstract double getLastX();
    public abstract double getLastBaseY();
    public abstract double getLastZ();
    public abstract boolean getLastOnGround();
    public abstract float getLastPitch();
    public abstract float getLastYaw();
}
