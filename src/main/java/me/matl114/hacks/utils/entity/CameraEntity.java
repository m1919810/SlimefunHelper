package me.matl114.hacks.utils.entity;

import java.util.UUID;
import javax.annotation.Nonnull;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.world.GameMode;

public class CameraEntity extends AbstractClientPlayerEntity {
    PlayerEntity player;
    GameMode mode;
    boolean moveable;
    PlayerListEntry entry;

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
        super.isSpectator();
    }

    @Override
    public boolean isSpectator() {
        return mode == GameMode.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        return mode == GameMode.CREATIVE;
    }

    @Override
    protected PlayerListEntry getPlayerListEntry() {
        return this.player != null
                ? MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(this.player.getUuid())
                : null;
    }

    public float getPitch() {
        return (!moveable && player != null) ? player.getPitch() : super.getPitch();
    }

    public float getYaw() {
        return (!moveable && player != null) ? player.getYaw() : super.getYaw();
    }

    @Override
    public void tick() {
        if ((!(this.player instanceof ClientPlayerEntity clientPlayer) || clientPlayer.networkHandler.isLoaded())) {
            if (!this.moveable) {
                this.setPitch(this.player.getPitch());
                this.setYaw(this.player.getYaw());
                this.setHeadYaw(this.player.getHeadYaw());
                this.setBodyYaw(this.player.getBodyYaw());
                this.setPosition(this.player.getPos());
            }
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

    public boolean canMoveVoluntarily() {
        return moveable;
    }
}
