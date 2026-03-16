package me.matl114.utils.commands.commandGroup;

import java.util.*;
import java.util.stream.Stream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import me.matl114.utils.commands.params.api.TabResult;

public class TreeSubCommand extends SubCommandImpl implements SubCommandDispatcher, SubCommand.SubCommandCaller {
    private SubCommand fallBackCommand = null;
    private TabResult fallbackTabSuggestor = TabResult.EMPTY;
    private final Map<String, SubCommand> subCommands;

    public TreeSubCommand(String name, String... helpContent) {
        super(name, null, helpContent);
        this.template = new SimpleCommandArgs(SimpleCommandArgs.argumentBuilder()
                .name("dispatch_" + name)
                .tabCompletor(this::onSubCommandSuggest)
                .build());
        this.subCommands = new LinkedHashMap<String, SubCommand>();
    }

    public Stream<String> onSubCommandSuggest(
            CommandExecution CommandExecution, List<InputArgument<?>> argumentReader) {
        return Stream.concat(
                subCommands.keySet().stream(), fallbackTabSuggestor.completeOrEmpty(CommandExecution, argumentReader));
    }

    public Stream<String> getHelp(String prefix) {
        // the name should be included in the help
        return Stream.concat(Stream.of(help).map(s -> prefix + s), SubCommandDispatcher.super.getHelp(prefix));
    }

    @Override
    public void registerSub(SubCommand command) {
        this.subCommands.put(command.getName(), command);
    }

    public void setFallbackCommand(SubCommand fallbackCommand, TabResult fallbackTabSuggestor) {
        this.fallBackCommand = fallbackCommand;
        this.fallbackTabSuggestor = fallbackTabSuggestor;
    }

    @Override
    public SubCommand getSubCommand(String name) {
        return subCommands.get(name);
    }

    @Override
    public Collection<SubCommand> getSubCommands() {
        return subCommands.values();
    }

    @Override
    public SubCommand getFallbackCommand() {
        return this.fallBackCommand;
    }

    public Stream<String> onCustomHelp(CommandExecution sender, ArgumentReader arguments) {
        return SubCommandDispatcher.super.onCustomHelp(sender, arguments);
    }
}
