package me.matl114.accessors.events;

import javax.annotation.Nonnull;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

public interface ClientPlayerEntityAccess extends LivingEntityAccess<ClientPlayerEntity> {
    public LegalMovementManager getLegalMovementManager();

    public void onPlayerInputPackets();

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

    public void resyncMovementPacket();

    public void resyncInput();

    @Nonnull
    public static ClientPlayerEntityAccess of(@Nonnull ClientPlayerEntity player) {
        return (ClientPlayerEntityAccess) player;
    }
}
