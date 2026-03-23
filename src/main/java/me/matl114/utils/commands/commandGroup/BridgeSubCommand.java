package me.matl114.utils.commands.commandGroup;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;

public class BridgeSubCommand extends DelegateSubCommand {
    public BridgeSubCommand(String name, CustomTabExecutor delegate) {
        super(name, delegate);
    }

    @Override
    public boolean onCustomCommand(CommandExecution sender, ArgumentReader arguments) {
        if (arguments.hasNext() && getName().equalsIgnoreCase(arguments.next())) {
            return super.onCustomCommand(sender, arguments);
        } else return false;
    }

    @Override
    public List<String> onCustomTabComplete(CommandExecution sender, ArgumentReader arguments) {
        if (arguments.hasNext()) {
            String input = arguments.next();
            if (getName().equalsIgnoreCase(input)) {
                return super.onCustomTabComplete(sender, arguments);
            } else if (!arguments.hasNext()) {
                final String inputLower = input.toLowerCase(Locale.ROOT);
                if (name.toLowerCase(Locale.ROOT).contains(inputLower)) {
                    return List.of(name);
                } else {
                    return List.of();
                }
            } else {
                return List.of();
            }
        } else {
            return List.of();
        }
    }

    @Override
    public Stream<String> onCustomHelp(CommandExecution sender, ArgumentReader arguments) {
        return super.onCustomHelp(sender, arguments).map(s -> getName() + " " + s);
        //        if (arguments.hasNext() && getName().equalsIgnoreCase(arguments.next())) {
        //            return super.onCustomHelp(sender, arguments);
        //        } else return Stream.empty();
    }
}
