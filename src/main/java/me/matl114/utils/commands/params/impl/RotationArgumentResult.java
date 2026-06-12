package me.matl114.utils.commands.params.impl;

import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.types.ExecuteRotation;

public class RotationArgumentResult extends AbstractArgumentResult<ExecuteRotation> {
    private final String rawString;

    public RotationArgumentResult(
            ExecuteRotation rotation,
            ArgumentType<ExecuteRotation> type,
            ArgumentReader reader,
            int startIndex,
            boolean parseSuccess) {
        super(rotation, type, reader, startIndex);
        this.parseSuccess = parseSuccess;
        this.rawString = rotation == null ? null : String.join(" ", reader.getArgsInRange(startIndex, endIndex));
    }

    @Override
    public String resultAsString() {
        return rawString;
    }
}