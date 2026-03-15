package me.matl114.utils.commands.params.impl;

import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;

public class EmptyArgumentResult<T> extends AbstractArgumentResult<T> {
    public EmptyArgumentResult(ArgumentType<T> type, ArgumentReader reader) {
        super(null, type, reader, reader.cursor());
        //
        isDefault = false;
    }

    @Override
    public String resultAsString() {
        return null;
    }
}
