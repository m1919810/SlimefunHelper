package me.matl114.utils.impl.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import com.mojang.brigadier.Command;
import me.matl114.utils.impl.interruptions.ArgumentException;
import me.matl114.utils.impl.interruptions.PermissionDenyError;
import me.matl114.utils.impl.interruptions.ValueUnexpectedError;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SubCommandDispatcher extends CustomTabExecutor, SubCommand.SubCommandCaller {
    default List<String> onCustomTabComplete(PlayerEntity sender, @Nullable Command apiUsage, ArgumentReader arguments){
        if(hasPermission(sender)){
            var re = parseInput(arguments);
            if (!arguments.hasNext()) {
                List<String> provider = re.getTabComplete(sender);
                return provider == null ? new ArrayList<>() : provider;
            } else {
                SubCommand subCommand = getSubCommand(re.nextArg());
                if (subCommand != null) {
                    List<String> tab = subCommand.onCustomTabComplete(sender, apiUsage, arguments);  //parseInput(elseArg).getTabComplete();
                    if (tab != null) {
                        return tab;
                    }
                }
            }
        }

        return new ArrayList<>();
    }

    @Override
    default boolean onCustomCommand(@NotNull PlayerEntity var1, @Nullable Command apiUsage, ArgumentReader reader) throws ArgumentException {
        if(hasPermission(var1)){
            if(reader.hasNext()) {
                String next = reader.next();
                SubCommand command = getSubCommand(next);
                if (command != null) {
                    // add permission check
                    return command.onCustomCommand(var1, apiUsage, reader);
                }
                //没有对应的
                throw new ValueUnexpectedError(reader.stepBack());
            }else{
                //认为在dispatch的时候值缺失算空串
                throw new ValueUnexpectedError(reader);
            }
            // not consume
        }else{
            throw new PermissionDenyError(permissionRequired(), reader);
        }

    }

    default Stream<String> onCustomHelp(PlayerEntity sender, ArgumentReader reader){
        if(hasPermission(sender)){
            if(reader.hasNext()) {
                String next = reader.peek();
                SubCommand command1 = getSubCommand(next);
                if (command1 != null) {
                    reader.next();
                    return command1.onCustomHelp(sender, reader);
                } else {
                    return getHelp(reader.getAlreadyReadCmdStr());
                }
            }else{
                return getHelp(reader.getAlreadyReadCmdStr());
            }
        }else{
            return Stream.empty();
        }
    }

    default Stream<String> getHelp(String prefix){
        return Streams.concat(getSubCommands().stream().map(cmd -> cmd.getHelp(prefix + cmd.getName() + " ")).toArray(Stream[]::new));
    }
}
