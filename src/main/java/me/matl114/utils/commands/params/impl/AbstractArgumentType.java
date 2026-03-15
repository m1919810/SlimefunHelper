package me.matl114.utils.commands.params.impl;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import me.matl114.utils.commands.interruption.ArgumentException;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import me.matl114.utils.commands.params.api.TabResult;

public abstract class AbstractArgumentType<T> implements ArgumentType<T> {
    @Getter
    private final String argsName;

    @Getter
    @Setter
    public TabResult tabCompletor = TabResult.EMPTY;

    @Getter
    @Setter
    protected T defaultValue = null;

    public AbstractArgumentType(String argsName) {
        this.argsName = argsName;
    }

    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
        try {
            var stream = tabCompletor.completeOrEmpty(sender, args);
            return filterTab(stream, args);
        } catch (ArgumentException argumentException) {
            return Stream.empty();
        }
    }

    protected Stream<String> filterTab(Stream<String> stream, List<InputArgument<?>> args) {
        if (!args.isEmpty()) {
            InputArgument<?> result = args.get(args.size() - 1);
            String resultResult = ((result != null && result.tabbingString() != null) ? result.tabbingString() : "")
                    .toLowerCase(Locale.ROOT);
            stream = stream.filter(s -> s.toLowerCase(Locale.ROOT).contains(resultResult));
        }
        return stream;
    }
}
