package me.matl114.utils.commands.commandGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;

@Setter
@Getter
@Accessors(fluent = true, chain = true)
public class TaskSubCommand extends SubCommand {
    CommandContext executor;

    public TaskSubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        super(name, argsTemplate, help);
    }

    @Override
    public List<String> onCustomTabComplete(CommandExecution sender, ArgumentReader arguments) {
        List<String> collectLore = new ArrayList<>();
        if (hasPermission(sender)) {
            var re = this.parseInput(sender, arguments);
            re.getTabComplete(sender).forEach(collectLore::add);
            if (arguments.hasNext()) {
                // already filled all the arguments so use executor to supply the extra args
                if (executor != null) {
                    collectLore.addAll(executor.supplyTab(sender, re, arguments));
                }
            }
        }
        return collectLore;
    }

    @Override
    public boolean onCustomCommand(CommandExecution sender, ArgumentReader arguments) {
        return executor != null && executor.execute(sender, parseInput(sender, arguments), arguments);
    }

    @Override
    public Stream<String> onCustomHelp(CommandExecution sender, ArgumentReader arguments) {
        if (hasPermission(sender)) {
            return getHelp(arguments.getAlreadyReadCmdStr());
        } else {
            return Stream.empty();
        }
    }
}
