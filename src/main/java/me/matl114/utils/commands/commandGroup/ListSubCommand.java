package me.matl114.utils.commands.commandGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import me.matl114.utils.commands.interruption.ArgumentException;
import me.matl114.utils.commands.interruption.DispatchFailureError;
import me.matl114.utils.commands.interruption.PermissionDenyError;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.TabResult;
import org.jetbrains.annotations.Nullable;

public class ListSubCommand implements SubCommand, SubCommand.SubCommandCaller {
    List<SubCommand> root = new ArrayList<>();
    String permissionNode;
    String name;

    public ListSubCommand(String name) {
        this.name = name;
    }

    @Override
    public void registerSub(SubCommand command) {
        root.add(command);
    }

    @Override
    public Collection<SubCommand> getSubCommands() {
        return root;
    }

    @Override
    public SubCommand getFallbackCommand() {
        return null;
    }

    @Override
    public void setFallbackCommand(SubCommand fallbackCommand, TabResult fallbackTabSuggestor) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String permissionRequired() {
        return permissionNode;
    }

    public ArgumentInputStream parseInput(CommandExecution execution, ArgumentReader reader) {
        return new ArgumentInputStream(execution, reader, List.of(), List.of());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean onCustomCommand(CommandExecution var1, ArgumentReader reader) {
        // mainName as first
        if (hasPermission(var1)) {
            ArgumentException catchExp = null;
            ArgumentReader currentReader;
            for (var command : root) {
                try {
                    if (command.onCustomCommand(var1, (currentReader = new ArgumentReader(reader)))) {
                        return true;
                    }
                } catch (ArgumentException e) {
                    // add Condition Error to check Permission and executor, they may success execute another command
                    if (e.isConditionError()) {
                        catchExp = e;
                    } else {
                        throw e;
                    }
                }
            }
            if (catchExp != null) {
                throw catchExp;
            } else {
                throw new DispatchFailureError(reader);
            }
        } else {
            throw new PermissionDenyError(permissionRequired(), reader);
        }
    }

    @Override
    public List<String> onCustomTabComplete(CommandExecution sender, ArgumentReader arguments) {
        if (hasPermission(sender)) {
            return root.stream()
                    .flatMap(s -> s.onCustomTabComplete(sender, new ArgumentReader(arguments)).stream())
                    .toList();
        } else return List.of();
    }

    @Override
    public Stream<String> onCustomHelp(CommandExecution sender, ArgumentReader arguments) {
        if (hasPermission(sender)) {
            return root.stream().flatMap(s -> s.onCustomHelp(sender, new ArgumentReader(arguments)));
        } else {
            return Stream.empty();
        }
    }

    @Override
    public Stream<String> getHelp(String prefix) {
        return root.stream().flatMap(s -> s.getHelp(prefix + getName() + " "));
    }

    @Override
    public void setPermission(String permission) {
        permissionNode = permission;
    }
}
