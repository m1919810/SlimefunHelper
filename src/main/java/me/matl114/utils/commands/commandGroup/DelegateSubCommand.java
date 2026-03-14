package me.matl114.matlib.utils.command.commandGroup;

import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.matlib.utils.command.params.ArgumentReader;
import me.matl114.matlib.utils.command.params.SimpleCommandArgs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

@Accessors(fluent = true, chain = true)
@Getter
@Setter
public class DelegateSubCommand extends SubCommand {
    CustomTabExecutor delegate;

    public DelegateSubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        super(name, argsTemplate, help);
    }

    @Override
    public boolean onCustomCommand(CommandSender sender, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomCommand(sender, arguments);
        return false;
    }

    @Override
    public List<String> onCustomTabComplete(CommandSender sender, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomTabComplete(sender, arguments);
        return List.of();
    }

    @Override
    public Stream<String> onCustomHelp(CommandSender sender, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomHelp(sender, arguments);
        return Stream.empty();
    }
}
