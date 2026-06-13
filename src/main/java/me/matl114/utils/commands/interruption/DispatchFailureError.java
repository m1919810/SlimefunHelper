package me.matl114.utils.commands.interruption;

import lombok.Setter;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;

public class DispatchFailureError extends ArgumentException {
    public DispatchFailureError(ArgumentReader reader) {
        this.argumentReader = reader;
    }

    ArgumentReader argumentReader;

    @Setter
    boolean condition = true;

    @Override
    public void handleAbort(CommandExecution sender, InterruptionHandler command) {
        command.handleDispatchFailure(sender, argumentReader);
    }

    @Override
    public boolean isConditionError() {
        return condition;
    }
}
