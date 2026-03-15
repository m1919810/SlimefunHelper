package me.matl114.utils.commands.interruption;

import me.matl114.utils.commands.params.api.CommandExecution;

public class LogicalError extends ArgumentException {
    String message;

    public LogicalError(String fullMessage) {
        this.message = fullMessage;
    }

    @Override
    public void handleAbort(CommandExecution sender, InterruptionHandler command) {
        command.handleLogicalError(sender, message);
    }
}
