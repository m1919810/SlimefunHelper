package me.matl114.matlib.utils.command.interruption;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.matl114.matlib.utils.command.params.ArgumentReader;
import org.bukkit.command.CommandSender;

public interface InterruptionHandler {
    public void handleTypeError(
            CommandSender sender, @Nullable ArgumentReader reader, @Nullable String argument, TypeError.BaseArgumentType type, String input);

    public void handleValueAbsent(CommandSender sender,@Nullable ArgumentReader reader , @Nonnull String argument);

    public void handleValueOutOfRange(
            CommandSender sender,
            @Nullable ArgumentReader reader,
            @Nullable String argument,
            TypeError.BaseArgumentType type,
            String from,
            String to,
            @Nonnull String input);

    public void handleExecutorInvalid(CommandSender sender, boolean shouldConsole);

    public void handlePermissionDenied(CommandSender sender, String permission, ArgumentReader reader);

    public void handleLogicalError(CommandSender sender, String fullMessage);

    public void handleUnexpectedArgument(CommandSender sender, ArgumentReader reader);
}
