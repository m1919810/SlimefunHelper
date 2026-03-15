package me.matl114.utils.commands.interruption;

import javax.annotation.Nullable;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;

public class ValueAbsentError extends ArgumentException {
    public ValueAbsentError(ArgumentReader reader, String argument) {
        this.reader = reader;
        this.argument = argument;
    }

    @Nullable
    ArgumentReader reader;

    String argument;

    @Override
    public void handleAbort(CommandExecution sender, InterruptionHandler command) {
        command.handleValueAbsent(sender, reader, argument);
    }
}
