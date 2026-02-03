package me.matl114.utils.interruptions;

import lombok.AllArgsConstructor;
import me.matl114.utils.commands.ArgumentReader;
import net.minecraft.entity.player.PlayerEntity;

@AllArgsConstructor
public class PermissionDenyError extends ArgumentException {
    String permission;
    ArgumentReader currentCommandInput;

    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handlePermissionDenied(sender, permission, currentCommandInput);
    }
}
