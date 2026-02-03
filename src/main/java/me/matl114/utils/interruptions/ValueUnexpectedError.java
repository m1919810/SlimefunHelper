package me.matl114.utils.interruptions;

import lombok.AllArgsConstructor;
import me.matl114.utils.commands.ArgumentReader;
import net.minecraft.entity.player.PlayerEntity;


@AllArgsConstructor
public class ValueUnexpectedError extends ArgumentException {
    ArgumentReader argumentReader;
    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handleUnexpectedArgument(sender, argumentReader);
    }
}
