package me.matl114.utils.interruptions;

import me.matl114.utils.commands.ArgumentReader;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collection;

public class ValueOutOfRangeError extends ArgumentException {
    ArgumentReader reader;
    String name;
    String range;
    String value;
    TypeError.BaseArgumentType type;

    public ValueOutOfRangeError(ArgumentReader reader, String name, int from, int to, int input) {
        this.reader = reader;

        this.name = name;
        this.range = String.valueOf(from) + " ~ " + String.valueOf(to) + "(exclusive)";
        this.value = String.valueOf(input);
        this.type = TypeError.BaseArgumentType.INT;
    }

    public ValueOutOfRangeError(ArgumentReader reader, String name, float from, float to, float input) {
        this.reader = reader;

        this.name = name;
        this.range = String.valueOf(from) + " ~ " + String.valueOf(to) + "(exclusive)";
        this.value = String.valueOf(input);

        this.type = TypeError.BaseArgumentType.FLOAT;
    }

    public ValueOutOfRangeError(ArgumentReader reader, String name, String from, String to, String input, TypeError.BaseArgumentType type) {
        this.reader = reader;

        this.name = name;
        this.range = String.valueOf(from) + " ~ " + String.valueOf(to) + "(exclusive)";
        this.value = input;
        this.type = type;
    }

    public ValueOutOfRangeError(ArgumentReader reader, String name, Collection<String> options, String input, TypeError.BaseArgumentType type) {
        this.reader = reader;

        this.name = name;
        this.range = "[" + String.join(", ", options) + "]";
        this.value = input;
        this.type = type;
    }

    @Override
    public void handleAbort(PlayerEntity sender, InterruptionHandler command) {
        command.handleValueOutOfRange(sender, this.reader, this.name, this.type, this.range, this.value);
    }
}
