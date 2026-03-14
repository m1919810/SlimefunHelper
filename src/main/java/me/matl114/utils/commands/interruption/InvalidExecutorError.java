package me.matl114.matlib.utils.command.interruption;

import org.bukkit.command.CommandSender;

public class InvalidExecutorError extends ArgumentException {
    boolean s;

    public InvalidExecutorError(boolean shouldConsoleExecute) {
        this.s = shouldConsoleExecute;
    }

    @Override
    public void handleAbort(CommandSender sender, InterruptionHandler command) {
        command.handleExecutorInvalid(sender, s);
    }
}
