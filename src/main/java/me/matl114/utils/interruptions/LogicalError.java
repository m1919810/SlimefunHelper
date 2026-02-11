package me.matl114.utils.interruptions;

import net.minecraft.entity.player.PlayerEntity;

public class LogicalError extends ArgumentException {
    String message;

    public LogicalError(String fullMessage) {
        this.message = fullMessage;
    }

    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handleLogicalError(sender, message);
    }
}
