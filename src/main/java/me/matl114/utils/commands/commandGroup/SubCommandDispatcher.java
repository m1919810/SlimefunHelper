package me.matl114.matlib.utils.command.commandGroup;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import me.matl114.matlib.utils.command.interruption.ArgumentException;
import me.matl114.matlib.utils.command.interruption.PermissionDenyError;
import me.matl114.matlib.utils.command.interruption.ValueUnexpectedError;
import me.matl114.matlib.utils.command.params.ArgumentReader;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SubCommandDispatcher extends CustomTabExecutor, SubCommand.SubCommandCaller {
    default List<String> onCustomTabComplete(
            CommandSender sender, ArgumentReader arguments) {
        if (hasPermission(sender)) {
            var re = parseInput(arguments);
            if (!arguments.hasNext()) {
                List<String> provider = re.getTabComplete(sender);
                return provider == null ? new ArrayList<>() : provider;
            } else {
                var str = re.peekNext().resultAsString();
                SubCommand subCommand = getSubCommand(str);
                if (subCommand != null) {
                    re.next();
                    List<String> tab = subCommand.onCustomTabComplete(
                            sender, arguments); // parseInput(elseArg).getTabComplete();
                    if (tab != null) {
                        return tab;
                    }
                }
                List<String> tab = onDefaultTab(sender, arguments);
                if (tab != null) {
                    return tab;
                }
            }
        }

        return new ArrayList<>();
    }

    default List<String> onDefaultTab(CommandSender sender, ArgumentReader arguments) {
        var defaultCmd = getFallbackCommand();
        if (defaultCmd == null) {
            return List.of();
        } else {
            return defaultCmd.onCustomTabComplete(sender, arguments);
        }
    }

    @Override
    default boolean onCustomCommand(@NotNull CommandSender var1, ArgumentReader reader)
            throws ArgumentException {
        if (hasPermission(var1)) {
            if (reader.hasNext()) {
                String next = reader.next();
                SubCommand command = getSubCommand(next);
                if (command != null) {
                    // add permission check
                    return command.onCustomCommand(var1, reader);
                }
                // 没有对应的, 回退当前参数
                reader.stepBack();
                return onDefaultCommand(var1, reader);
            } else {
                // 认为在dispatch的时候值缺失算空串
                throw new ValueUnexpectedError(reader);
            }
            // not consume
        } else {
            throw new PermissionDenyError(permissionRequired(), reader);
        }
    }

    default boolean onDefaultCommand(@NotNull CommandSender var1, ArgumentReader reader)
            throws ArgumentException {
        var defaultCmd = getFallbackCommand();
        if (defaultCmd == null) {
            throw new ValueUnexpectedError(reader);
        } else {
            return defaultCmd.onCustomCommand(var1, reader);
        }
    }

    default Stream<String> onCustomHelp(CommandSender sender, ArgumentReader reader) {
        if (hasPermission(sender)) {
            if (reader.hasNext()) {
                String next = reader.peek();
                SubCommand command1 = getSubCommand(next);
                if (command1 != null) {
                    reader.next();
                    return command1.onCustomHelp(sender, reader);
                } else {
                    return getHelp(reader.getAlreadyReadCmdStr());
                }
            } else {
                return getHelp(reader.getAlreadyReadCmdStr());
            }
        } else {
            return Stream.empty();
        }
    }

    default Stream<String> getHelp(String prefix) {
        return Stream.concat(
                Streams.concat(getSubCommands().stream()
                        .map(cmd -> cmd.getHelp(prefix + cmd.getName() + " "))
                        .toArray(Stream[]::new)),
                getFallbackCommand() == null
                        ? Stream.empty()
                        : getFallbackCommand().getHelp(prefix));
    }
}
