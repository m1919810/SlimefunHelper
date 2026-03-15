package me.matl114.utils.commands.commandGroup;

import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;

@Accessors(fluent = true, chain = true)
@Getter
@Setter
public class DelegateSubCommand extends SubCommand {
    CustomTabExecutor delegate;

    public DelegateSubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        super(name, argsTemplate, help);
    }

    public DelegateSubCommand(String name, CustomTabExecutor delegate) {
        super(name, null, new String[] {});
        this.delegate = delegate;
    }

    @Override
    public boolean onCustomCommand(CommandExecution sender, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomCommand(sender, arguments);
        return false;
    }

    @Override
    public List<String> onCustomTabComplete(CommandExecution sender, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomTabComplete(sender, arguments);
        return List.of();
    }

    @Override
    public Stream<String> onCustomHelp(CommandExecution sender, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomHelp(sender, arguments);
        return Stream.empty();
    }
}
