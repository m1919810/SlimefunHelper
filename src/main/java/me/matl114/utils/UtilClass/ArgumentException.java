package me.matl114.utils.UtilClass;

import net.minecraft.client.network.ClientPlayerEntity;

public abstract class ArgumentException extends RuntimeException{
    public ArgumentException(){
        super();
    }
    public abstract void handleAbort(ClientPlayerEntity player, InterruptionHandler command);

    @Override
    public synchronized Throwable fillInStackTrace() {
        //override this method to avoid fill stacktrace when create Abort
        return this;
    }
}
