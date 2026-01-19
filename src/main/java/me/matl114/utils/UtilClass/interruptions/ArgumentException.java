package me.matl114.matlib.utils.command.interruption;

import me.matl114.matlib.common.lang.exceptions.Abort;
import me.matl114.matlib.common.lang.exceptions.RuntimeAbort;
import org.bukkit.command.CommandSender;

public abstract class ArgumentException extends RuntimeAbort {
    public ArgumentException() {
        super();
    }

    public abstract void handleAbort(CommandSender sender, InterruptionHandler command);
}
