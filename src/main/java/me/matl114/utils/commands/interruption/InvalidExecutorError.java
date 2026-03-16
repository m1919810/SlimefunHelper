package me.matl114.utils.commands.interruption;

import me.matl114.utils.commands.params.api.CommandExecution;

public class InvalidExecutorError extends ArgumentException {
    boolean s;

    public InvalidExecutorError(boolean shouldConsoleExecute) {
        this.s = shouldConsoleExecute;
    }

    @Override
    public void handleAbort(CommandExecution sender, InterruptionHandler command) {
        command.handleExecutorInvalid(sender, s);
    }

    @Override
    public boolean isConditionError() {
        return true;
    }
}
