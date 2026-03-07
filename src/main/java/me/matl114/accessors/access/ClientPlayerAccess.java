package me.matl114.accessors.access;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.matl114.accessors.events.ClientPlayerEntityAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.screen.ScreenHandler;

public interface ClientPlayerAccess extends ClientPlayerEntityAccess {

    @Nullable
    public HandledScreen getKeepedInv();

    @Nullable
    public ScreenHandler getKeepedInvHandler();

    public void clearKeepedInventory(boolean closeInv);

    default void resyncSprint() {
        setLastSprintFlag(!((Entity) this).isSprinting());
    }

    public void setLastSprintFlag(boolean lastSprint);

    default void resyncSneak() {
        setLastSneakFlag(!((ClientPlayerEntity) this).input.playerInput.sneak());
    }

    public void setLastSneakFlag(boolean lastSprint);

    default void resyncOnGround() {
        setLastOnGroundFlag(!((Entity) this).isOnGround());
    }

    public void setLastOnGroundFlag(boolean lastOnGround);

    public void resyncPos();

    public void resyncRot();
    //    public void resyncPos();
    //    public void resyncRot();
    //    public void syncLocationPackets();
    public boolean isContinueMoving();

    @Nonnull
    public static ClientPlayerAccess of(@Nonnull ClientPlayerEntity player) {
        return (ClientPlayerAccess) player;
    }
    // get the Screen object which handler related to the server(should)
    default HandledScreen getServerOpeningScreen() {
        if (getKeepedInv() != null) return getKeepedInv();
        else return MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> han ? han : null;
    }

    @Nonnull
    default ScreenHandler getServerScreenHandler() {
        if (getKeepedInvHandler() != null) return getKeepedInvHandler();
        else return ((ClientPlayerEntity) this).currentScreenHandler;
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
