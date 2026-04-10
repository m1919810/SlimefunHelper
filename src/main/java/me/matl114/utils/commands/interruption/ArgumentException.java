package me.matl114.utils.commands.interruption;

import me.matl114.utils.RuntimeAbort;
import me.matl114.utils.commands.params.api.CommandExecution;

public abstract class ArgumentException extends RuntimeAbort {
    public ArgumentException() {
        super();
    }

    public abstract void handleAbort(CommandExecution sender, InterruptionHandler command);

    // if return true , this exception is thrown when condition check not pass
    public boolean isConditionError() {
        return false;
    }
}
