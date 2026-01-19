package me.matl114.matlib.utils.command.interruption;

import java.util.Optional;
import lombok.AllArgsConstructor;
import me.matl114.matlib.utils.command.params.ArgumentReader;
import org.bukkit.command.CommandSender;

@AllArgsConstructor
public class PermissionDenyError extends ArgumentException {
    String permission;
    ArgumentReader currentCommandInput;

    @Override
    public void handleAbort(CommandSender sender, InterruptionHandler command) {
        command.handlePermissionDenied(sender, permission, currentCommandInput);
    }
}
