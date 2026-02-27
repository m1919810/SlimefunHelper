package me.matl114.utils.commands;

import java.util.*;
import java.util.stream.Stream;
import net.minecraft.entity.player.PlayerEntity;

public class TreeSubCommand extends SubCommand implements SubCommandDispatcher, SubCommand.SubCommandCaller {

    private SubCommand fallBackCommand = null;
    private SimpleCommandArgs.TabResult fallbackTabSuggestor = SimpleCommandArgs.TabResult.EMPTY;
    private final Map<String, SubCommand> subCommands;

    public TreeSubCommand(String name, String... helpContent) {
        super(name, new SimpleCommandArgs("dispatch_" + name), helpContent);
        this.subCommands = new LinkedHashMap<String, SubCommand>();
        setTabCompletor("dispatch_" + name, this::onSubCommandSuggest);
    }

    public Stream<String> onSubCommandSuggest(PlayerEntity commandSender, List<InputArgument> argumentReader) {
        return Stream.concat(
                subCommands.keySet().stream(), fallbackTabSuggestor.completeOrEmpty(commandSender, argumentReader));
    }

    public Stream<String> getHelp(String prefix) {
        // the name should be included in the help
        return Stream.concat(Stream.of(help).map(s -> prefix + s), SubCommandDispatcher.super.getHelp(prefix));
    }

    @Override
    public void registerSub(SubCommand command) {
        this.subCommands.put(command.getName(), command);
    }

    public void setFallbackCommand(SubCommand fallbackCommand, SimpleCommandArgs.TabResult fallbackTabSuggestor) {
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

    public Stream<String> onCustomHelp(PlayerEntity sender, ArgumentReader arguments) {
        return SubCommandDispatcher.super.onCustomHelp(sender, arguments);
    }
}
