package me.matl114.matlib.utils.command.params.impl;

import me.matl114.matlib.utils.command.params.ArgumentReader;
import me.matl114.matlib.utils.command.params.SimpleCommandArgs;
import me.matl114.matlib.utils.command.params.api.ArgumentType;
import me.matl114.matlib.utils.command.params.api.InputArgument;

public class StringArgumentResult extends AbstractArgumentResult<String> implements InputArgument<String> {
    public StringArgumentResult(String string, ArgumentType<String> type, ArgumentReader reader, int startIndex) {
        super(string, type, reader, startIndex);

    }

    @Override
    public String resultAsString() {
        return result;
    }
}
