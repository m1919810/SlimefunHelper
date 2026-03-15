package me.matl114.utils.commands.interruption;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;

public interface InterruptionHandler {
    public void handleTypeError(
            CommandExecution sender,
            @Nullable ArgumentReader reader,
            @Nullable String argument,
            TypeError.BaseArgumentType type,
            String input);

    public void handleValueAbsent(CommandExecution sender, @Nullable ArgumentReader reader, @Nonnull String argument);

    public void handleValueParseFailure(
            CommandExecution sender, @Nullable ArgumentReader reader, @Nonnull String argument);

    public void handleValueOutOfRange(
            CommandExecution sender,
            @Nullable ArgumentReader reader,
            @Nullable String argument,
            TypeError.BaseArgumentType type,
            String range,
            @Nonnull String input);

    public void handleExecutorInvalid(CommandExecution sender, boolean shouldConsole);

    public void handlePermissionDenied(CommandExecution sender, String permission, ArgumentReader reader);

    public void handleLogicalError(CommandExecution sender, String fullMessage);

    public void handleUnexpectedArgument(CommandExecution sender, ArgumentReader reader);
}
