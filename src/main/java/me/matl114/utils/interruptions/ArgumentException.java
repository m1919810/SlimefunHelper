package me.matl114.utils.interruptions;

import net.minecraft.entity.player.PlayerEntity;

public abstract class ArgumentException extends RuntimeException {
    public ArgumentException() {
        super();
    }

    public abstract void handleAbort(PlayerEntity sender, InterruptionHandler command);

    @Override
    public synchronized Throwable fillInStackTrace() {
        // override this method to avoid fill stacktrace when create Abort
        return this;
    }
}
