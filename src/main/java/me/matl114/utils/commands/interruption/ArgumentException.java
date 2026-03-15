package me.matl114.utils.commands.interruption;

import me.matl114.utils.commands.params.api.CommandExecution;

public abstract class ArgumentException extends RuntimeException {
    public ArgumentException() {
        super();
    }

    public abstract void handleAbort(CommandExecution sender, InterruptionHandler command);

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
