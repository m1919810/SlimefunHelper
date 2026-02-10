package me.matl114.accessors.events;

import javax.annotation.Nonnull;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.ProgressWrapper;
import net.minecraft.client.network.ClientPlayerEntity;

public interface ClientPlayerEntityAccess extends LivingEntityAccess<ClientPlayerEntity> {
    public LegalMovementManager getLegalMovementManager();

    public void addMovementPacketWrapper(ProgressWrapper<ClientPlayerEntity> wrapper);

    @Nonnull
    public static ClientPlayerEntityAccess of(@Nonnull ClientPlayerEntity player) {
        return (ClientPlayerEntityAccess) player;
    }
}
