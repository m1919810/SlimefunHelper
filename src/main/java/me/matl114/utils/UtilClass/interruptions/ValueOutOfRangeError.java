package me.matl114.matlib.utils.command.interruption;

import me.matl114.matlib.utils.command.params.ArgumentReader;
import org.bukkit.command.CommandSender;

public class ValueOutOfRangeError extends ArgumentException {
    ArgumentReader reader;
    String name;
    String from;
    String to;
    String value;
    TypeError.BaseArgumentType type;

    public ValueOutOfRangeError(ArgumentReader reader, String name, int from, int to, int input) {
        this.reader = reader;

        this.name = name;
        this.from = String.valueOf(from);
        this.to = String.valueOf(to);
        this.value = String.valueOf(input);
        this.type = TypeError.BaseArgumentType.INT;
    }

    public ValueOutOfRangeError(ArgumentReader reader, String name, float from, float to, float input) {
        this.reader = reader;

        this.name = name;
        this.from = String.valueOf(from);
        this.to = String.valueOf(to);
        this.value = String.valueOf(input);

        this.type = TypeError.BaseArgumentType.FLOAT;
    }

    public ValueOutOfRangeError(ArgumentReader reader, String name, String from, String to, String input, TypeError.BaseArgumentType type) {
        this.reader = reader;

        this.name = name;
        this.from = from;
        this.to = to;
        this.value = input;
        this.type = type;
    }

    @Override
    public void handleAbort(CommandSender sender, InterruptionHandler command) {
        command.handleValueOutOfRange(sender, this.reader, this.name, this.type, this.from, this.to, this.value);
    }
}
