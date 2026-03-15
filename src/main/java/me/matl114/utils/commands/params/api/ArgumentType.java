package me.matl114.utils.commands.params.api;

import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import me.matl114.utils.commands.params.ArgumentReader;

public interface ArgumentType<T> {
    public String getArgsName();

    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args);
    // return null if not parsable, return default value if reader is not readable
    @Nullable
    public InputArgument<T> consume(CommandExecution sender, List<InputArgument<?>> args, ArgumentReader reader);
}
