package me.matl114.utils.UtilClass.interruptions;

import lombok.AllArgsConstructor;
import me.matl114.utils.UtilClass.commands.ArgumentReader;
import net.minecraft.entity.player.PlayerEntity;


@AllArgsConstructor
public class ValueUnexpectedError extends ArgumentException {
    ArgumentReader argumentReader;
    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handleUnexpectedArgument(sender, argumentReader);
    }
}
