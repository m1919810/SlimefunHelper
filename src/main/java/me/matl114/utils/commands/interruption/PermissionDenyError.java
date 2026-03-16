package me.matl114.utils.commands.interruption;

import lombok.AllArgsConstructor;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;

@AllArgsConstructor
public class PermissionDenyError extends ArgumentException {
    String permission;
    ArgumentReader currentCommandInput;

    @Override
    public void handleAbort(CommandExecution sender, InterruptionHandler command) {
        command.handlePermissionDenied(sender, permission, currentCommandInput);
    }

    @Override
    public boolean isConditionError() {
        return true;
    }
}
