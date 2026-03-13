package me.matl114.hacks.utils.entity;

import java.util.UUID;
import javax.annotation.Nonnull;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.world.GameMode;
import org.jspecify.annotations.Nullable;

public class CameraEntity extends AbstractClientPlayerEntity {
    ClientPlayerEntity player;
    GameMode mode;
    boolean moveable;

    public CameraEntity(ClientWorld clientWorld, @Nonnull ClientPlayerEntity player, GameMode mode, boolean moveable) {
        super(clientWorld, player.getGameProfile());
        // avoid id collision
        setId(-getId());
        this.mode = mode;
        this.moveable = moveable;
        setUuid(UUID.randomUUID());
        copyEquipments(player.getInventory());
        this.player = player;
        setPosition(player.getPos());
        setPitch(player.getPitch());
        setYaw(player.getYaw());
        resetPosition();
    }

    public PlayerInventory getInventory() {
        return this.player != null ? player.getInventory() : super.getInventory();
    }

    public void copyEquipments(PlayerInventory p) {
        // copy inventory before we set the delegate player
        getInventory().clone(p);
    }

    @Override
    public @Nullable GameMode getGameMode() {
        return mode;
    }

    @Override
    protected PlayerListEntry getPlayerListEntry() {
        return this.player != null ? this.player.networkHandler.getPlayerListEntry(this.player.getUuid()) : null;
    }

    public float getPitch() {
        return (!moveable && player != null) ? player.getPitch() : super.getPitch();
    }

    public float getYaw() {
        return (!moveable && player != null) ? player.getYaw() : super.getYaw();
    }

    @Override
    public void tick() {
        if (this.player != null && this.player.networkHandler.isLoaded()) {
            super.tick();
        }
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
    }

    public boolean isMainPlayer() {
        return moveable;
    }
}
