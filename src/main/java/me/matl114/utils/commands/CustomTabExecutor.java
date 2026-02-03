package me.matl114.utils.commands;

import com.mojang.brigadier.Command;
import net.minecraft.entity.player.PlayerEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Stream;

public interface CustomTabExecutor {
    /**
     * Returns the permission required to use this main command.
     * Override this method to specify the required permission.
     * Return null for no permission requirement.
     *
     * @return The permission string, or null if no permission is required
     */
    @Nullable
    public abstract String permissionRequired();

    /**
     * Checks if the sender has permission to use this sub-command.
     * By default, returns true (no permission required).
     * Override this method to implement custom permission logic.
     *
     * @param sender The command sender to check
     * @return true if the sender has permission, false otherwise
     */
    default boolean hasPermission(PlayerEntity sender) {
        return true;
    }

    /**
     * Parses the input arguments according to the argument template.
     * Returns a pair containing the parsed input stream and remaining arguments.
     *
     * @param args The arguments to parse
     * @return A pair containing the parsed input stream and remaining arguments
     */
    @Nonnull
    public ArgumentInputStream parseInput(ArgumentReader args);

    public String getName();

    public boolean onCustomCommand(PlayerEntity sender, Command command, ArgumentReader arguments);

    public List<String> onCustomTabComplete(PlayerEntity sender, Command command, ArgumentReader arguments);

    public Stream<String> onCustomHelp(PlayerEntity sender, ArgumentReader arguments);

//    @DoNotOverride
//    @Override
//    default boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
//        return onCustomCommand(commandSender, command, new ArgumentReader(s, strings));
//    }
//    @Override
//    @DoNotOverride
//    default List<String> onTabComplete(@NotNull CommandSender var1, @NotNull Command var2, @NotNull String var3, @NotNull String[] var4) {
//        return onCustomTabComplete(var1, var2, new ArgumentReader(var3, var4));
//    }

    /**
     * the prefix WILL contains current command name with a blank
     * @param prefix
     * @return
     */
    public Stream<String> getHelp(String prefix);


}
