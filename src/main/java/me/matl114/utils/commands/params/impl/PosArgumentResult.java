package me.matl114.utils.commands.params.impl;

import java.util.Optional;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.types.ExecutePos;

public class PosArgumentResult extends AbstractArgumentResult<ExecutePos> {
    String rawString;

    public PosArgumentResult(
            Optional<ExecutePos> vector3d, ArgumentType<ExecutePos> type, ArgumentReader reader, int startIndex) {
        super(vector3d == null ? null : vector3d.orElse(null), type, reader, startIndex);
        if (vector3d == null) {
            rawString = null;
            parseSuccess = false;
        } else {
            if (isDefault && vector3d.isPresent()) {
                rawString = vector3d.get().asString();
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
