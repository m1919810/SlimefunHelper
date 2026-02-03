package me.matl114.utils.impl.interruptions;

import lombok.AllArgsConstructor;
import me.matl114.utils.impl.commands.ArgumentReader;
import me.matl114.utils.impl.commands.SimpleCommandArgs;
import net.minecraft.entity.player.PlayerEntity;


import javax.annotation.Nullable;


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
