package me.matl114.matlib.utils.command.params.api;

import me.matl114.matlib.utils.command.params.ArgumentReader;
import org.bukkit.command.CommandSender;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface ArgumentType<T> {
    public String getArgsName();

    public Stream<String> getTab(CommandSender sender, List<InputArgument<?>> args);
    // return null if not parsable, return default value if reader is not readable
    @Nullable
    public InputArgument<T> consume(ArgumentReader reader);
}
