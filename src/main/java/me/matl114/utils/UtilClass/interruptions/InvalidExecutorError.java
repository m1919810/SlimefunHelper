package me.matl114.utils.UtilClass.interruptions;

import net.minecraft.entity.player.PlayerEntity;

public class InvalidExecutorError extends ArgumentException {
    boolean s;

    public InvalidExecutorError(boolean shouldConsoleExecute) {
        this.s = shouldConsoleExecute;
    }

    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handleExecutorInvalid(sender, s);
    }
}
