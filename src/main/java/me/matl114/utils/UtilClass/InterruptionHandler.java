package me.matl114.utils.UtilClass;

import net.minecraft.client.network.ClientPlayerEntity;

import javax.annotation.Nonnull;

public interface InterruptionHandler {
    public void handleTypeError(ClientPlayerEntity sender, String argument, TypeError.BaseArgumentType type, String input);
    public void handleValueAbsent(ClientPlayerEntity sender,@Nonnull String argument);
    public void handleLogicalError(ClientPlayerEntity sender, String fullMessage);
}
