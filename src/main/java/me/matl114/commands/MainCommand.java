package me.matl114.commands;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.utils.Debug;
import me.matl114.utils.commands.commandGroup.AbstractMainCommand;
import me.matl114.utils.commands.commandGroup.BridgeSubCommand;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.client.MinecraftClient;

public class MainCommand extends AbstractMainCommand {
    public static void init() {
        reloadCommand();
    }

    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void reloadCommand() {
        new MainCommand();
        if (mc.player != null) {
            Debug.chat("Command Successfully reloaded");
        }
    }

    public MainCommand() {
        REGISTERED_COMMANDS = this;
        if (COMMAND_BOOTSTRAPS != null) {
            COMMAND_BOOTSTRAPS.forEach(s -> s.onCommandReload(this));
        }
    }

    {
        // this should be the first help command to be dispatched
        registerSub(new BridgeSubCommand(
                "help",
                SubCommand.taskBuilder()
                        .name("help")
                        .post(s -> s.executor(new CommandContext() {
                            @Override
                            public boolean execute(
                                    CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
                                ArgumentReader reader = new ArgumentReader(argsReader.getRemainingArgs());
                                reader.stepAll();
                                showHelpCommand(var1, reader);
                                return true;
                            }

                            @Override
                            public List<String> supplyTab(
                                    CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
                                ArgumentReader reader = new ArgumentReader(argsReader.getRemainingArgs());
                                reader.stepAll();
                                return onCustomTabComplete(var1, argsReader);
                            }
                        }))
                        .build()));
    }

    private static MainCommand REGISTERED_COMMANDS;
    private static final List<Bootstrap> COMMAND_BOOTSTRAPS = new ArrayList<>();

    public static void registerSubCommands(String name, Supplier<AbstractMainCommand> commandSupplier) {
        registerCommandBootstrap((main) -> {
            main.registerAsSubCommand(name, commandSupplier.get());
        });
    }

    public static void registerCommands(Supplier<AbstractMainCommand> commandSupplier) {
        registerCommandBootstrap((main) -> {
            main.registerAsCommand(commandSupplier.get());
        });
    }

    public static void registerCommandBootstrap(Bootstrap bootStrap) {
        COMMAND_BOOTSTRAPS.add(bootStrap);
        if (REGISTERED_COMMANDS != null) {
            bootStrap.onCommandReload(REGISTERED_COMMANDS);
        }
    }

    public static void unregisterCommandBootstrap(Predicate<Bootstrap> bootstrap) {
        COMMAND_BOOTSTRAPS.removeIf(bootstrap);
    }

    public void registerAsSubCommand(String dispatchName, AbstractMainCommand main) {
        this.registerSub(new BridgeSubCommand(dispatchName, main));
    }

    public void registerAsCommand(AbstractMainCommand main) {
        this.registerSub(main);
    }

    public static interface Bootstrap {
        public void onCommandReload(MainCommand command);
    }

    // our client commands
    public static void parseClientCommand(Event<String> commandEvent) {
        String command = commandEvent.context();
        if (command.startsWith("!!")) {
            dispatchClientCommand(command.substring(2));
            commandEvent.cancel();
            return;
        } else if (command.startsWith("/!!")) {
            dispatchClientCommand(command.substring(3));
            commandEvent.cancel();
            return;
        }
    }

    public static void dispatchClientCommand(String command) {
        if (mc.player != null) {
            String[] args = command.split(" ");
            if (args.length == 0) return;
            try {
                if (REGISTERED_COMMANDS.onCommand(mc.player, "", args)) {
                    return;
                }
            } catch (Throwable e) {
                Debug.chat("Unexpected Error occurred :", e.getMessage());
                Debug.info(e);
            }
        }
    }

    public static void dispatchCommand(String[] args) {
        if (mc.player != null) {
            if (args.length == 0) return;
            try {
                if (REGISTERED_COMMANDS.onCommand(mc.player, "", args)) {
                    return;
                }
            } catch (Throwable e) {
                Debug.chat("Unexpected Error occurred :", e.getMessage());
                Debug.info(e);
            }
        }
    }

    public static List<String> callTabCompletion(String[] command) {
        if (mc.player != null) {
            List<String> val = REGISTERED_COMMANDS.onTabComplete(mc.player, "", command);
            if (val != null && !val.isEmpty()) {
                return val;
            }
        }
        return List.of();
    }

    public static boolean isClientCommand(String command) {
        return command.startsWith("!!") || command.startsWith("/!!");
    }

    public static CompletableFuture<Suggestions> tabCompleteClientCommand(String command, int cursorAt) {
        if (command.startsWith("!!")) {
            return dispatchTabComplete(command.substring(2), cursorAt - 2, false);
        } else if (command.startsWith("/!!")) {
            return dispatchTabComplete(command.substring(3), cursorAt - 3, true);
        }
        return null;
    }

    public static CompletableFuture<Suggestions> dispatchTabComplete(String command, int cursorAt, boolean withPrefix) {
        if (cursorAt < 0) {
            // handle !!
            return null;
        }
        String trueCommand = command.substring(0, cursorAt);
        int lastBlank = -1;
        int prefixLen = 2 + (withPrefix ? 1 : 0);
        StringRange tabCompleteRange;
        List<String> args = new ArrayList<>();
        while (true) {
            int nextBlank = trueCommand.indexOf(" ", lastBlank + 1);
            if (nextBlank == -1) {
                args.add(trueCommand.substring(lastBlank + 1));
                tabCompleteRange = new StringRange(prefixLen + lastBlank + 1, prefixLen + trueCommand.length());
                break;
            }
            args.add(trueCommand.substring(lastBlank + 1, nextBlank));
            lastBlank = nextBlank;
        }
        List<String> tabList = callTabCompletion(args.toArray(String[]::new));
        List<Suggestion> suggestionList =
                tabList.stream().map(i -> new Suggestion(tabCompleteRange, i)).toList();
        Suggestions suggestions = new Suggestions(tabCompleteRange, suggestionList);
        return CompletableFuture.completedFuture(suggestions);
    }

    static {
        Listener.getChatSend().registerHandler(MainCommand::parseClientCommand);
    }
}
