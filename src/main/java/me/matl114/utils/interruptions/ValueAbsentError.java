package me.matl114.utils.interruptions;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import me.matl114.utils.commands.ArgumentReader;
import me.matl114.utils.commands.SimpleCommandArgs;
import net.minecraft.entity.player.PlayerEntity;

@AllArgsConstructor
public class ValueAbsentError extends ArgumentException {
    @Nullable
    ArgumentReader reader;

    String argument;

    public ValueAbsentError(@Nullable ArgumentReader reader, SimpleCommandArgs.Argument argument) {
        this(reader, argument.getArgsName());
    }

    public ValueAbsentError(SimpleCommandArgs.Argument argument) {
        this(null, argument.getArgsName());
    }

    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handleValueAbsent(sender, reader, argument);
    }
}
