package me.matl114.utils.interruptions;

import me.matl114.utils.commands.ArgumentReader;
import net.minecraft.entity.player.PlayerEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public interface InterruptionHandler {
    public void handleTypeError(
        PlayerEntity sender, @Nullable ArgumentReader reader, @Nullable String argument, TypeError.BaseArgumentType type, String input);

    public void handleValueAbsent(PlayerEntity sender,@Nullable ArgumentReader reader , @Nonnull String argument);

    public void handleValueOutOfRange(
            PlayerEntity sender,
            @Nullable ArgumentReader reader,
            @Nullable String argument,
            TypeError.BaseArgumentType type,
            String range,
            @Nonnull String input);

    public void handleExecutorInvalid(PlayerEntity sender, boolean shouldConsole);

    public void handlePermissionDenied(PlayerEntity sender, String permission, ArgumentReader reader);

    public void handleLogicalError(PlayerEntity sender, String fullMessage);

    public void handleUnexpectedArgument(PlayerEntity sender, ArgumentReader reader);
}
