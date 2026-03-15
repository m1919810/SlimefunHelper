package me.matl114.utils.commands.params.impl;

import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.InputArgument;

public class StringArgumentResult extends AbstractArgumentResult<String> implements InputArgument<String> {
    public StringArgumentResult(String string, ArgumentType<String> type, ArgumentReader reader, int startIndex) {
        super(string, type, reader, startIndex);
    }

    @Override
    public String resultAsString() {
        return result;
    }
}
