package me.matl114.utils.commands;

import com.google.common.collect.Streams;
import com.mojang.brigadier.Command;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import me.matl114.utils.interruptions.ArgumentException;
import me.matl114.utils.interruptions.PermissionDenyError;
import me.matl114.utils.interruptions.ValueUnexpectedError;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SubCommandDispatcher extends CustomTabExecutor, SubCommand.SubCommandCaller {
    default List<String> onCustomTabComplete(
            PlayerEntity sender, @Nullable Command apiUsage, ArgumentReader arguments) {
        if (hasPermission(sender)) {
            var re = parseInput(arguments);
            if (!arguments.hasNext()) {
                List<String> provider = re.getTabComplete(sender);
                return provider == null ? new ArrayList<>() : provider;
            } else {
                var str = re.peekNext().result();
                SubCommand subCommand = getSubCommand(str);
                if (subCommand != null) {
                    re.next();
                    List<String> tab = subCommand.onCustomTabComplete(
                            sender, apiUsage, arguments); // parseInput(elseArg).getTabComplete();
                    if (tab != null) {
                        return tab;
                    }
                }
                List<String> tab = onDefaultTab(sender, apiUsage, arguments);
                if (tab != null) {
                    return tab;
                }
            }
        }

        return new ArrayList<>();
    }

    default List<String> onDefaultTab(PlayerEntity sender, @Nullable Command apiUsage, ArgumentReader arguments) {
        var defaultCmd = getFallbackCommand();
        if (defaultCmd == null) {
            return List.of();
        } else {
            return defaultCmd.onCustomTabComplete(sender, apiUsage, arguments);
        }
    }

    @Override
    default boolean onCustomCommand(@NotNull PlayerEntity var1, @Nullable Command apiUsage, ArgumentReader reader)
            throws ArgumentException {
        if (hasPermission(var1)) {
            if (reader.hasNext()) {
                String next = reader.next();
                SubCommand command = getSubCommand(next);
                if (command != null) {
                    // add permission check
                    return command.onCustomCommand(var1, apiUsage, reader);
                }
                // 没有对应的, 回退当前参数
                reader.stepBack();
                return onDefaultCommand(var1, apiUsage, reader);
            } else {
                // 认为在dispatch的时候值缺失算空串
                throw new ValueUnexpectedError(reader);
            }
            // not consume
        } else {
            throw new PermissionDenyError(permissionRequired(), reader);
        }
    }

    default boolean onDefaultCommand(@NotNull PlayerEntity var1, @Nullable Command apiUsage, ArgumentReader reader)
            throws ArgumentException {
        var defaultCmd = getFallbackCommand();
        if (defaultCmd == null) {
            throw new ValueUnexpectedError(reader);
        } else {
            return defaultCmd.onCustomCommand(var1, apiUsage, reader);
        }
    }

    default Stream<String> onCustomHelp(PlayerEntity sender, ArgumentReader reader) {
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
