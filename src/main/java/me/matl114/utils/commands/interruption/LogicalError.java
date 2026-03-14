package me.matl114.matlib.utils.command.interruption;

import org.bukkit.command.CommandSender;

public class LogicalError extends ArgumentException {
    String message;

    public LogicalError(String fullMessage) {
        this.message = fullMessage;
    }

    @Override
    public void handleAbort(CommandSender sender, InterruptionHandler command) {
        command.handleLogicalError(sender, message);
    }
}
