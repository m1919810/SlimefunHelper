package me.matl114.utils.UtilClass;

import net.minecraft.client.network.ClientPlayerEntity;

public class LogicalError extends ArgumentException {
    String message;
    public LogicalError(String fullMessage){
        this.message = fullMessage;
    }

    @Override
    public void handleAbort(ClientPlayerEntity sender, InterruptionHandler command) {
        command.handleLogicalError(sender, message);
    }
}

