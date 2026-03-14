package me.matl114.matlib.utils.command.commandGroup;

import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import me.matl114.matlib.common.lang.annotations.Internal;
import me.matl114.matlib.utils.command.params.ArgumentReader;
import me.matl114.matlib.utils.command.params.SimpleCommandArgs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

public class WrapperSubCommand extends SubCommand {

    /** Executor responsible for handling command execution */
    @Internal("use for wrapper")
    TabExecutor executor = null;
    Command commandInternal = null;
    /**
     * Sets a custom executor for this sub-command.
     * The executor will handle both command execution and tab completion.
     * This is used to wrap some other TabExecutors into our command system
     * you can use SubCommand as a Wrapper
     *
     * @param executor The TabExecutor to use for this sub-command
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setDelegateTabExecutor(@Nonnull TabExecutor executor) {
        this.executor = executor;
        this.commandInternal = new Command(name) {
            @Override
            public boolean execute(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) {
                return WrapperSubCommand.this.executor.onCommand(commandSender, this, s, strings);
            }
        };
        return this;
    }

    public TabExecutor getDelegateTabExecutor() {
        return executor;
    }

    @Override
    public boolean onCustomCommand(CommandSender sender,ArgumentReader arguments) {
        return executor != null
                && executor.onCommand(sender, this.commandInternal, arguments.getAlreadyReadArgStr(), arguments.getRemainingArgs());
    }

    @Override
    public List<String> onCustomTabComplete(CommandSender sender, ArgumentReader arguments) {
        if (executor != null) {
            return executor.onTabComplete(
                    sender, this.commandInternal, arguments.getAlreadyReadArgStr(), arguments.getRemainingArgs());
        } else {
            return List.of();
        }
    }

    public WrapperSubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        super(name, argsTemplate, help);
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
