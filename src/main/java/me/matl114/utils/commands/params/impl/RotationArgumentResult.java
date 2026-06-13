package me.matl114.utils.commands.params.impl;

import java.util.Optional;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.types.ExecuteRotation;

public class RotationArgumentResult extends AbstractArgumentResult<ExecuteRotation> {
    String rawString;

    public RotationArgumentResult(
            Optional<ExecuteRotation> rotation,
            ArgumentType<ExecuteRotation> type,
            ArgumentReader reader,
            int startIndex) {
        super(rotation == null ? null : rotation.orElse(null), type, reader, startIndex);
        if (rotation == null) {
            rawString = null;
            parseSuccess = false;
        } else {
            if (isDefault && rotation.isPresent()) {
                rawString = rotation.get().asString();
            } else {
                rawString = String.join(" ", this.reader.getArgsInRange(startIndex, endIndex));
            }
        }
    }

    @Override
    public String resultAsString() {
        return rawString;
    }
}
