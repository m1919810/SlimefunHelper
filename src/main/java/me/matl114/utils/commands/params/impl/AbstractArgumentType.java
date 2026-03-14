package me.matl114.matlib.utils.command.params.impl;

import lombok.Getter;
import lombok.Setter;
import me.matl114.matlib.utils.command.interruption.ArgumentException;
import me.matl114.matlib.utils.command.params.SimpleCommandArgs;
import me.matl114.matlib.utils.command.params.api.ArgumentType;
import me.matl114.matlib.utils.command.params.api.InputArgument;
import me.matl114.matlib.utils.command.params.api.TabResult;
import org.bukkit.command.CommandSender;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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

    public Stream<String> getTab(CommandSender sender, List<InputArgument<?>> args) {
        try {
            var stream = tabCompletor.completeOrEmpty(sender, args);
            if(!args.isEmpty()){
                InputArgument<?> result = args.get(args.size() - 1);
                String resultResult = ((result != null && result.tabbingString() != null) ? result.tabbingString() : "").toLowerCase(Locale.ROOT);
                stream = stream.filter(s -> s.toLowerCase(Locale.ROOT).contains(resultResult));
            }

            return stream;
        } catch (ArgumentException argumentException) {
            return Stream.empty();
        }
    }
}
