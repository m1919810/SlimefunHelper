package me.matl114.matlib.utils.command.interruption;

import lombok.AllArgsConstructor;
import me.matl114.matlib.utils.command.params.ArgumentReader;
import org.bukkit.command.CommandSender;

@AllArgsConstructor
public class ValueUnexpectedError extends ArgumentException {
    ArgumentReader argumentReader;
    @Override
    public void handleAbort(CommandSender sender, InterruptionHandler command) {
        command.handleUnexpectedArgument(sender, argumentReader);
    }
}
