package me.matl114.utils.commands.commandGroup;

import com.google.common.base.Supplier;
import java.util.*;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.commands.interruption.*;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for root commands that can contain multiple sub-commands.
 * This class provides a complete command framework with sub-command management,
 * permission handling, argument parsing, and error handling capabilities.
 *
 * <p>AbstractMainCommand implements both ComplexCommandExecutor and InterruptionHandler,
 * providing a comprehensive command system that can handle complex command structures
 * with proper error handling and user feedback.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Sub-command registration and management</li>
 *   <li>Permission checking at both root and sub-command levels</li>
 *   <li>Automatic help generation</li>
 *   <li>Tab completion for sub-commands and arguments</li>
 *   <li>Error handling with user-friendly messages</li>
 *   <li>Plugin registration and unregistration</li>
 * </ul>
 *
 * <p>To use this class, extend it and implement the abstract methods.
 * The root command should be defined as a field named "mainCommand" in the subclass.</p>
 */
public class AbstractMainCommand implements SubCommand, InterruptionHandler {

    /** Internal reference to the root command */
    private final ListSubCommand root = new ListSubCommand("");

    {
        // this should be the first help command to be dispatched
        this.root.registerSub(new BridgeSubCommand(
                "help",
                SubCommand.taskBuilder()
                        .name("help")
                        .post(s -> s.executor(((var1, streamArgs, argsReader) -> {
                            showHelpCommand(var1, new ArgumentReader(getName(), argsReader.getRemainingArgs()));
                            return true;
                        })))
                        .build()));
    }

    public SubCommand.Builder<TreeSubCommand> mainBuilder() {
        return SubCommand.factoryBuilder((a, b, c) -> {
            var root = new TreeSubCommand(a, c);
            this.root.registerSub(root);
            return root;
        });
    }

    /**
     * Sends a message to the command sender with color code translation.
     *
     * @param sender The command sender to send the message to
     * @param message The message to send (supports & color codes)
     */
    protected void sendMessage(CommandExecution sender, String message) {
        sender.sendMessage(message);
    }

    protected void sendMessage(CommandExecution sender, Text message) {
        sender.sendMessage(message);
    }

    /**
     * Generates a root command with the specified name.
     * The root command uses a special "_operation" argument for sub-command selection.
     *
     * @param name The name of the root command
     * @return A SubCommand instance configured as the root command
     */
    @Deprecated
    protected SubCommand genMainCommand(String name) {
        return new TreeSubCommand(name);
    }

    /**
     * Registers a sub-command with this root command.
     *
     * @param command The sub-command to register
     */
    public void registerSub(SubCommand command) {
        if (this.root instanceof SubCommand.SubCommandCaller dispatcher) {
            dispatcher.registerSub(command);
        } else {
            throw new UnsupportedOperationException("Can not register");
        }
    }

    /**
     * Gets the name of the root command.
     *
     * @return The name of the root command
     */
    public String getMainName() {
        return root.getName();
    }

    public void setMainName(String name) {
        root.name = name;
    }

    public String getName() {
        return root.getName();
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public String permissionRequired() {
        return root.permissionRequired();
    }

    public void setPermissionRequired(String required) {
        root.setPermission(required);
    }

    public ArgumentInputStream parseInput(CommandExecution execution, ArgumentReader reader) {
        return new ArgumentInputStream(execution, reader, List.of(), List.of());
    }

    private StringBuilder getArgumentPositionPrefix(ArgumentReader reader) {
        return reader == null ? new StringBuilder() : new StringBuilder("&f" + reader.getAlreadyReadCmdStr() + "&c<--");
    }

    /**
     * Handles type errors during argument parsing.
     * Displays a user-friendly error message in Chinese.
     *
     * @param sender The command sender to send the error to
     * @param argument The argument name that caused the error (may be null)
     * @param type The expected argument type
     * @param input The invalid input that was provided
     */
    public void handleTypeError(
            CommandExecution sender,
            @Nullable ArgumentReader reader,
            @Nullable String argument,
            TypeError.BaseArgumentType type,
            String input) {
        StringBuilder builder = getArgumentPositionPrefix(reader);
        if (argument != null) {
            builder.append("&c类型错误:参数\"")
                    .append(argument)
                    .append("\"需要输入一个")
                    .append(type.getDisplayNameZHCN())
                    .append(",但是输入了:")
                    .append(input);
        } else {
            builder.append("&c类型错误: 需要输入一个")
                    .append(type.getDisplayNameZHCN())
                    .append(",但是输入了:")
                    .append(input);
        }
        sendMessage(sender, builder.toString());
    }

    /**
     * Handles missing argument values.
     * Displays a user-friendly error message in Chinese.
     *
     * @param sender The command sender to send the error to
     * @param argument The argument name that is missing a value
     */
    public void handleValueAbsent(CommandExecution sender, @Nullable ArgumentReader reader, @Nonnull String argument) {
        StringBuilder builder = getArgumentPositionPrefix(reader);
        if (reader != null) {
            builder.append("&c值缺失: 并未输入参数\"").append(argument).append("\"的值");

        } else {
            builder.append("&c值缺失: 并未输入参数\"").append(argument).append("\"的值");
        }
        sendMessage(sender, builder.toString());
    }

    public void handleValueParseFailure(
            CommandExecution sender, @Nullable ArgumentReader reader, @Nonnull String argument) {
        StringBuilder builder = getArgumentPositionPrefix(reader);
        if (reader != null) {
            builder.append("&c值缺失: 参数\"").append(argument).append("\"解析失败");

        } else {
            builder.append("&c值缺失: 参数\"").append(argument).append("\"解析失败");
        }
        sendMessage(sender, builder.toString());
    }

    /**
     * Handles argument values that are out of the expected range.
     * Displays a user-friendly error message in Chinese.
     *
     * @param sender The command sender to send the error to
     * @param argument The argument name that caused the error (may be null)
     * @param type The argument type
     * @param range The description of allowed value
     * @param input The invalid input that was provided
     */
    @Override
    public void handleValueOutOfRange(
            CommandExecution sender,
            @Nullable ArgumentReader reader,
            @Nullable String argument,
            TypeError.BaseArgumentType type,
            String range,
            @Nonnull String input) {
        var builder = getArgumentPositionPrefix(reader);
        if (argument != null) {
            builder.append("&c值不在范围内: 参数 %s 输入了类型: %s, 需要在范围 %s 之间, 但是输入了%s"
                    .formatted(argument, type.getDisplayNameZHCN(), range, input));
        } else {
            builder.append(
                    "&c值不在范围内: 输入了类型: %s, 需要在范围 %s 之间, 但是输入了 %s".formatted(type.getDisplayNameZHCN(), range, input));
        }
        sendMessage(sender, builder.toString());
    }

    /**
     * Handles invalid executor errors (console vs player).
     * Displays a user-friendly error message in Chinese.
     *
     * @param sender The command sender to send the error to
     * @param shouldConsole Whether the command should be executed by console
     */
    @Override
    public void handleExecutorInvalid(CommandExecution sender, boolean shouldConsole) {
        if (shouldConsole) {
            sendMessage(sender, "&c错误! 该指令只能在控制台执行");
        } else {
            sendMessage(sender, "&c该指令只能在游戏内执行!");
        }
    }

    public void handlePermissionDenied(
            CommandExecution sender, String permission, @Nullable ArgumentReader commandNodeName) {
        if (commandNodeName == null) {
            noPermission(sender);
        } else {
            sendMessage(sender, "&c你没有权限使用: " + commandNodeName.getAlreadyReadArgStr());
        }
    }

    @Override
    public void handleDispatchFailure(CommandExecution sender, ArgumentReader reader) {
        showHelpCommand(sender, reader);
    }

    /**
     * Handles logical errors during command execution.
     * Displays a user-friendly error message in Chinese.
     *
     * @param sender The command sender to send the error to
     * @param fullMessage The full error message
     */
    public void handleLogicalError(CommandExecution sender, String fullMessage) {
        sendMessage(sender, "&c执行该指令时出现逻辑错误: " + fullMessage);
    }

    /**
     * Sends a permission denied message to the command sender.
     *
     * @param var1 The command sender to send the message to
     */
    protected void noPermission(CommandExecution var1) {
        sendMessage(var1, "&c你没有权限使用该指令!");
    }

    public Stream<String> getHelp(String prefix) {
        return root.getHelp(prefix);
    }

    @Override
    public boolean onCustomCommand(@NotNull CommandExecution var1, ArgumentReader reader) throws ArgumentException {
        // mainName as first
        return root.onCustomCommand(var1, reader);
    }

    @Override
    public List<String> onCustomTabComplete(CommandExecution sender, ArgumentReader arguments) {
        return root.onCustomTabComplete(sender, arguments);
    }

    @Override
    public Stream<String> onCustomHelp(CommandExecution sender, ArgumentReader reader) {
        return root.onCustomHelp(sender, reader);
    }

    /**
     * Shows the help command with all visible sub-commands.
     * Displays the root command usage and help text for each sub-command.
     *
     * @param sender The command sender to show help to
     */
    protected void showHelpCommand(CommandExecution sender, ArgumentReader command) {
        String already = command.getAlreadyReadArgStr();
        sender.sendMessage("/%s 全部指令".formatted(already));
        onCustomHelp(sender, new ArgumentReader(command.getAlreadyReadArgs()))
                .forEach(s -> sendMessage(sender, "&a" + s));
    }

    /**
     * Safely casts a CommandSender to a Player.
     * Throws InvalidExecutorError if the sender is not a Player.
     *
     * @param sender The command sender to cast
     * @return The Player instance
     * @throws InvalidExecutorError if the sender is not a Player
     */
    @Nonnull
    public PlayerEntity player(CommandExecution sender) {
        if (sender.getExecutor() instanceof PlayerEntity player) {
            return player;
        } else {
            throw new InvalidExecutorError(false);
        }
    }

    public void permissionDenied(String permission, @Nullable ArgumentReader argument) {
        throw new PermissionDenyError(permission, argument);
    }

    public void checkPermission(CommandExecution sender, String permission, ArgumentReader argument) {
        if (sender.hasPermission(permission)) {
            return;
        } else {
            throw new PermissionDenyError(permission, argument);
        }
    }

    /**
     * Generates a SimpleCommandArgs instance with the specified argument names.
     *
     * @param args The argument names
     * @return A SimpleCommandArgs instance configured with the specified arguments
     */
    public static SimpleCommandArgs genArgument(String... args) {
        return new SimpleCommandArgs(args);
    }

    /**
     * Creates a supplier that provides common number values for tab completion.
     *
     * @return A supplier that returns a list of common number values
     */
    public static Supplier<Stream<String>> numberSupplier() {
        return () -> Stream.of("0", "1", "16", "64", "114514", "2147483647");
    }

    /**
     * Creates a supplier that provides common float values for tab completion.
     *
     * @return A supplier that returns a list of common float values
     */
    public static Supplier<Stream<String>> floatSupplier() {
        return () -> Stream.of("0.0", "1.0", "2.0", "3.0", "3.14159", "1.57079", "6.283185");
    }

    /**
     * Creates a supplier that provides online player names for tab completion.
     *
     * @return A supplier that returns a list of online player names
     */
    public static Supplier<Stream<String>> playerNameSupplier() {
        return () -> EntityUtils.getWorldPlayerNames(true);
    }

    public static void checkArgument(boolean argument, String... msg) {
        if (!argument) {
            throw new LogicalError(String.join(" ", msg));
        }
    }

    public static void checkNonnull(Object object, String... msg) {
        if (object == null) {
            throw new LogicalError(String.join(" ", msg));
        }
    }

    @Override
    public void setPermission(String permission) {
        root.setPermission(permission);
    }

    public ListSubCommand getMainCommand() {
        return root;
    }

    public boolean onCommand(PlayerEntity var1, String var3, String[] var4) {
        CommandExecution execution = CommandExecution.sender(var1);
        try {
            // return getMainCommand().onCustomCommand(var1, var2, new ArgumentReader(getMainName(), var4));
            return onCustomCommand(execution, new ArgumentReader(getMainName(), var4));
        } catch (ArgumentException ex) {
            ex.handleAbort(execution, this);
            return true;
        }
    }

    public List<String> onTabComplete(PlayerEntity var1, String var3, String[] var4) {
        CommandExecution execution = CommandExecution.sender(var1);
        try {
            return onCustomTabComplete(execution, new ArgumentReader(getName(), var4));
        } catch (Throwable e) {
        }
        return List.of();
    }
}
