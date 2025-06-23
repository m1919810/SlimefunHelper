package me.matl114.utils.UtilClass;

import lombok.AllArgsConstructor;
import net.minecraft.client.network.ClientPlayerEntity;

@AllArgsConstructor
public class ValueAbsentError extends ArgumentException {
    String argument;
    public ValueAbsentError(SimpleCommandArgs.Argument argument){
        this(argument.getArgsName());
    }
    @Override
    public void handleAbort(ClientPlayerEntity sender, InterruptionHandler command) {
        command.handleValueAbsent(sender, argument);
    }
}

