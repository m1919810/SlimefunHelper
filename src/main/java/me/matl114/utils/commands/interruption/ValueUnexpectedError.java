package me.matl114.utils.commands.interruption;

import lombok.AllArgsConstructor;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;

@AllArgsConstructor
public class ValueUnexpectedError extends ArgumentException {
    ArgumentReader argumentReader;

    @Override
    public void handleAbort(CommandExecution sender, InterruptionHandler command) {
        command.handleUnexpectedArgument(sender, argumentReader);
    }
}
