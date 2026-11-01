package me.matl114.accessors.access;

import javax.annotation.Nonnull;
import me.matl114.accessors.events.ClientPlayerEntityAccess;
import net.minecraft.client.network.ClientPlayerEntity;

public interface ClientPlayerAccess extends ClientPlayerEntityAccess {

    @Nonnull
    public static ClientPlayerAccess of(@Nonnull ClientPlayerEntity player) {
        return (ClientPlayerAccess) player;
    }

    public boolean isForceNoFall();

    public void setForceNoFall(boolean fall);
}
