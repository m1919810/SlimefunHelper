package me.matl114.utils.commands;

import com.google.common.base.Supplier;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import java.util.*;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.interruptions.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
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
public class AbstractMainCommand implements CustomTabExecutor, InterruptionHandler {

    /** Internal reference to the root command */
    private TreeSubCommand root;

    protected SubCommand.Builder<TreeSubCommand> mainBuilder() {
        return SubCommand.factoryBuilder((a, b, c) -> {
            root = new TreeSubCommand(a, c);
            root.subBuilder(SubCommand.taskBuilder())
                    .name("help")
                    .post(s -> s.executor(((var1, streamArgs, argsReader) -> {
                        showHelpCommand(var1, new ArgumentReader(getName(), argsReader.getRemainingArgs()));
                        return true;
                    })))
                    .complete();
            return root;
        });
    }

    /** Whether this command has been registered with the plugin */
    private boolean registered = false;

    /**
     * Sends a message to the command sender with color code translation.
     *
     * @param sender The command sender to send the message to
     * @param message The message to send (supports & color codes)
     */
    protected void sendMessage(PlayerEntity sender, String message) {
        Debug.chat(ChatUtils.stringToText(message));
    }

    /**
     * Sends a message to the command sender with color code translation.
     *
     * @param sender The command sender to send the message to
     * @param message The message to send (supports & color codes)
     */
    protected void sendMessage(PlayerEntity sender, Text message) {
        Debug.chat(message);
    }

    /**
     * Gets a sub-command by name (case-insensitive).
     *
     * @param name The name of the sub-command to find
     * @return The sub-command with the specified name, or null if not found
     */
    public SubCommand getSubCommand(String name) {
        return getMainCommand().getSubCommand(name);
    }

    public Collection<SubCommand> getSubCommands() {
        return getMainCommand().getSubCommands();
    }

    /**
     * Gets a list of visible sub-command names for display purposes.
     * Hidden sub-commands are filtered out.
     *
     * @return A list of visible sub-command names
     */
    public List<String> getDisplayedSubCommand() {
        return this.getSubCommands().stream()
                .filter(SubCommand::isVisiable)
                .map(SubCommand::getName)
                .toList();
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
        this.root.registerSub(command);
    }

    /**
     * Gets the root command for this command group.
     *
     * @return The root command SubCommand instance
     */
    public TreeSubCommand getMainCommand() {
        if (root == null) {
            throw new IllegalStateException("Access to root delegate before it is built");
        }
        return root;
    }

    /**
     * Gets the name of the root command.
     *
     * @return The name of the root command
     */
    public String getMainName() {
        return getMainCommand().getName();
    }

    public String getName() {
        return getMainName();
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public String permissionRequired() {
        return getMainCommand().permissionRequired();
    }

    public ArgumentInputStream parseInput(ArgumentReader reader) {
        return (getMainCommand()).parseInput(reader);
    }

    /**
     * Handles command execution for the root command.
     * This method routes commands to appropriate sub-commands based on the first argument.
     *
     * <p>The execution flow:</p>
     * <ol>
     *   <li>Check root command permission</li>
     *   <li>Parse the first argument as sub-command name</li>
     *   <li>Find and validate the sub-command</li>
     *   <li>Check sub-command permission</li>
     *   <li>Execute the sub-command with remaining arguments</li>
     *   <li>Handle any ArgumentException errors</li>
     *   <li>Show help if no valid sub-command is found</li>
     * </ol>
     *
     * @param var1 The command sender
     * @param var2 The command being executed
     * @param var3 The command alias
     * @param var4 The command arguments
     * @return true if the command was executed successfully, false otherwise
     */
    public boolean onCommand(PlayerEntity var1, Command var2, String var3, String[] var4) {
        try {
            // return getMainCommand().onCustomCommand(var1, var2, new ArgumentReader(getMainName(), var4));
            return onCustomCommand(var1, var2, new ArgumentReader(getMainName(), var4).stepBack());
        } catch (ArgumentException ex) {
            ex.handleAbort(var1, this);
            return true;
        }
    }

    public boolean onCommandAsync(
            @NotNull PlayerEntity var1, @NotNull Command var2, @NotNull String var3, @NotNull String[] var4) {
        return onCommand(var1, var2, var3, var4);
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
            PlayerEntity sender,
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
    public void handleValueAbsent(PlayerEntity sender, @Nullable ArgumentReader reader, @Nonnull String argument) {
        StringBuilder builder = getArgumentPositionPrefix(reader);
        if (reader != null) {
            builder.append("&c值缺失: 并未输入参数\"").append(argument).append("\"的值");

        } else {
            builder.append("&c值缺失: 并未输入参数\"").append(argument).append("\"的值");
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
     * @param range The range description
     * @param input The invalid input that was provided
     */
    @Override
    public void handleValueOutOfRange(
            PlayerEntity sender,
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
    public void handleExecutorInvalid(PlayerEntity sender, boolean shouldConsole) {
        if (shouldConsole) {
            sendMessage(sender, "&c错误! 该指令只能在控制台执行");
        } else {
            sendMessage(sender, "&c该指令只能在游戏内执行!");
        }
    }

    public void handlePermissionDenied(
            PlayerEntity sender, String permission, @Nullable ArgumentReader commandNodeName) {
        if (commandNodeName == null) {
            noPermission(sender);
        } else {
            sendMessage(sender, "&c你没有权限使用: " + commandNodeName.getAlreadyReadArgStr());
        }
    }

    @Override
    public void handleUnexpectedArgument(PlayerEntity sender, ArgumentReader reader) {
        showHelpCommand(sender, reader);
    }

    /**
     * Handles logical errors during command execution.
     * Displays a user-friendly error message in Chinese.
     *
     * @param sender The command sender to send the error to
     * @param fullMessage The full error message
     */
    public void handleLogicalError(PlayerEntity sender, String fullMessage) {
        sendMessage(sender, "&c执行该指令时出现逻辑错误: " + fullMessage);
    }

    /**
     * Sends a permission denied message to the command sender.
     *
     * @param var1 The command sender to send the message to
     */
    protected void noPermission(PlayerEntity var1) {
        sendMessage(var1, "&c你没有权限使用该指令!");
    }

    public Stream<String> getHelp(String prefix) {
        return getMainCommand().getHelp(prefix + getName() + " ");
    }

    @Override
    public boolean onCustomCommand(@NotNull PlayerEntity var1, @Nullable Command apiUsage, ArgumentReader reader)
            throws ArgumentException {
        // mainName as first
        if (hasPermission(var1)) {
            if (reader.hasNext()) {
                if (getName().equalsIgnoreCase(reader.next())) {
                    return getMainCommand().onCustomCommand(var1, apiUsage, reader);
                } else {
                    throw new ValueUnexpectedError(reader);
                }
            } else {
                throw new ValueAbsentError(reader, "main_command");
            }
        } else {
            throw new PermissionDenyError(permissionRequired(), reader);
        }
    }

    @Override
    public List<String> onCustomTabComplete(
            PlayerEntity sender, @org.jetbrains.annotations.Nullable Command apiUsage, ArgumentReader arguments) {
        if (hasPermission(sender) && arguments.hasNext() && getName().equalsIgnoreCase(arguments.next())) {
            return getMainCommand().onCustomTabComplete(sender, apiUsage, arguments);
        } else return List.of();
    }

    @Override
    public Stream<String> onCustomHelp(PlayerEntity sender, ArgumentReader reader) {
        if (hasPermission(sender)) {
            if (reader.hasNext()) {
                // mainName as first
                if (getName().equalsIgnoreCase(reader.next()) && hasPermission(sender)) {
                    return getMainCommand().onCustomHelp(sender, reader);
                } else {
                    return Stream.empty();
                }

            } else {
                return Stream.empty();
            }
        } else {
            return Stream.empty();
        }
    }

    /**
     * Shows the help command with all visible sub-commands.
     * Displays the root command usage and help text for each sub-command.
     *
     * @param sender The command sender to show help to
     */
    protected void showHelpCommand(PlayerEntity sender, ArgumentReader command) {
        String already = command.getAlreadyReadArgStr();
        sendMessage(sender, "/%s 全部指令".formatted(already));
        onCustomHelp(sender, new ArgumentReader(command.getAlreadyReadArgs()))
                .forEach(s -> sendMessage(sender, "&a" + s));
    }

    /**
     * Handles tab completion for the root command and its sub-commands.
     * This method provides intelligent tab completion based on the current input.
     *
     * <p>The tab completion flow:</p>
     * <ol>
     *   <li>Check root command permission</li>
     *   <li>Parse input to determine current context</li>
     *   <li>If no sub-command is selected, show available sub-commands</li>
     *   <li>If a sub-command is selected, delegate to that sub-command</li>
     *   <li>Handle any exceptions gracefully</li>
     * </ol>
     *
     * @param var1 The command sender requesting tab completion
     * @param var2 The command being executed
     * @param var3 The command alias
     * @param var4 The command arguments
     * @return A list of tab completion suggestions
     */
    public List<String> onTabComplete(PlayerEntity var1, Command var2, String var3, String[] var4) {
        try {
            return onCustomTabComplete(var1, var2, new ArgumentReader(getName(), var4).stepBack());
        } catch (Throwable e) {
        }
        return List.of();
    }

    /**
     * Safely casts a PlayerEntity to a Player.
     * Throws InvalidExecutorError if the sender is not a Player.
     *
     * @param sender The command sender to cast
     * @return The Player instance
     * @throws InvalidExecutorError if the sender is not a Player
     */
    @Nonnull
    public PlayerEntity player(PlayerEntity sender) {
        if (sender instanceof PlayerEntity player) {
            return player;
        } else {
            throw new InvalidExecutorError(false);
        }
    }

    public void permissionDenied(String permission, @Nullable ArgumentReader argument) {
        throw new PermissionDenyError(permission, argument);
    }

    public void checkPermission(PlayerEntity sender, String permission, ArgumentReader argument) {
        if (true) {
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
     * Creates a supplier that provides the list of visible sub-command names.
     *
     * @return A supplier that returns the list of visible sub-command names
     */
    public Supplier<Stream<String>> subCommandsSupplier() {
        return () -> this.getSubCommands().stream().map(SubCommand::getName);
    }

    /**
     * Creates a supplier that provides online player names for tab completion.
     *
     * @return A supplier that returns a list of online player names
     */
    public static Supplier<Stream<String>> playerNameSupplier() {
        return () -> MinecraftClient.getInstance().world.getPlayers().stream()
                .map(PlayerEntity::getNameForScoreboard); // Bukkit.getOnlinePlayers().stream().map(Player::getName);
    }

    public static Supplier<Stream<String>> playerListNameSupplier() {
        return () -> MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(GameProfile::getName);
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
}
