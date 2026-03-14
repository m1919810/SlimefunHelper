package me.matl114.matlib.utils.command.interruption;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import me.matl114.matlib.common.lang.annotations.Note;
import me.matl114.matlib.utils.command.params.ArgumentReader;
import me.matl114.matlib.utils.command.params.SimpleCommandArgs;
import org.bukkit.command.CommandSender;

@Note("interrupt when no value present at this argument")
@AllArgsConstructor
public class ValueAbsentError extends ArgumentException {
    @Nullable ArgumentReader reader;

    String argument;


    @Override
    public void handleAbort(CommandSender sender, InterruptionHandler command) {
        command.handleValueAbsent(sender, reader, argument);
    }
}
