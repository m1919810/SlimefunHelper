package me.matl114.utils.commands.commandGroup;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.util.Language;
import org.jetbrains.annotations.NotNull;

public abstract class SubCommandImpl implements SubCommand {
    /** Help text lines for this sub-command */
    /** Should be like an array of string: <name> <argument> <argument> function, function, function**/
    String[] help;

    /** Argument template defining the expected parameters */
    SimpleCommandArgs template;

    /** Name of this sub-command */
    @Getter
    String name;

    @Setter
    String permission;

    public String permissionRequired() {
        return permission;
    }

    /**
     * Creates a new SubCommand with the specified name, argument template, and help text.
     *
     * @param name The name of the sub-command
     * @param argsTemplate The argument template defining expected parameters
     * @param help Help text lines for this sub-command
     */
    public SubCommandImpl(String name, SimpleCommandArgs argsTemplate, String... help) {
        this.name = name;
        this.template = argsTemplate;
        this.help = help;
    }

    /**
     * Creates a new SubCommand with the specified name, argument template, and help text.
     *
     * @param name The name of the sub-command
     * @param argsTemplate The argument template defining expected parameters
     * @param help Help text lines for this sub-command
     */
    public SubCommandImpl(String name, SimpleCommandArgs argsTemplate, List<String> help) {
        this(name, argsTemplate, help.toArray(String[]::new));
    }

    @NotNull
    @Override
    public ArgumentInputStream parseInput(CommandExecution execution, ArgumentReader args) {
        return template.parseInputStream(execution, args);
    }

    @Override
    public Stream<String> getHelp(String prefix) {
        return Arrays.stream(help).map(s -> prefix + Language.getInstance().get(s, s));
    }
}
