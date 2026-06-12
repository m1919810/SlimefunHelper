package me.matl114.utils.commands.params.impl;

import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;

public class OptionalArgumentResult<T> extends AbstractArgumentResult<T> {
    private final String rawString;

    public OptionalArgumentResult(
            T result,
            ArgumentType<T> type,
            ArgumentReader reader,
            int startIndex,
            String rawString,
            boolean parseSuccess) {
        super(result, type, reader, startIndex);
        this.rawString = rawString;
        this.parseSuccess = parseSuccess;
    }

    @Override
    public String resultAsString() {
        return rawString;
    }
}