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
import org.jetbrains.annotations.Nullable;

@Setter
@Getter
@Accessors(fluent = true, chain = true)
public class TaskSubCommand extends SubCommand {
    CommandContext executor;

    public TaskSubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        super(name, argsTemplate, help);
    }

    @Override
    public List<String> onCustomTabComplete(CommandSender sender, ArgumentReader arguments) {
        var re = this.parseInput(arguments);
        if (arguments.hasNext()) {
            // already filled all the arguments so use executor to supply the extra args
            return executor == null ? List.of() : executor.supplyTab(sender, re, arguments);
        } else {
            return re.getTabComplete(sender);
        }
    }

    @Override
    public boolean onCustomCommand(CommandSender sender, ArgumentReader arguments) {
        return executor != null && executor.execute(sender, parseInput(arguments), arguments);
    }

    @Override
    public Stream<String> onCustomHelp(CommandSender sender, ArgumentReader arguments) {
        if (hasPermission(sender)) {
            return getHelp(arguments.getAlreadyReadCmdStr());
        } else {
            return Stream.empty();
        }
    }
}
