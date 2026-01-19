package me.matl114.utils.UtilClass.interruptions;

import java.util.Optional;
import lombok.AllArgsConstructor;
import me.matl114.utils.UtilClass.commands.ArgumentReader;
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
