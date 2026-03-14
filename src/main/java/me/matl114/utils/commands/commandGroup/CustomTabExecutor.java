package me.matl114.matlib.utils.command.commandGroup;

import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.matl114.matlib.utils.command.CommandUtils;
import me.matl114.matlib.utils.command.interruption.TypeError;
import me.matl114.matlib.utils.command.params.ArgumentInputStream;
import me.matl114.matlib.utils.command.params.ArgumentReader;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public interface CustomTabExecutor {
    /**
     * Returns the permission required to use this main command.
     * Override this method to specify the required permission.
     * Return null for no permission requirement.
     *
     * @return The permission string, or null if no permission is required
     */
    @Nullable public abstract String permissionRequired();

    /**
     * Checks if the sender has permission to use this sub-command.
     * By default, returns true (no permission required).
     * Override this method to implement custom permission logic.
     *
     * @param sender The command sender to check
     * @return true if the sender has permission, false otherwise
     */
    default boolean hasPermission(CommandSender sender) {
        String permission = permissionRequired();
        return permission == null || sender.hasPermission(permission);
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

    public boolean onCustomCommand(CommandSender sender,  ArgumentReader arguments);

    public List<String> onCustomTabComplete(CommandSender sender, ArgumentReader arguments);

    public Stream<String> onCustomHelp(CommandSender sender, ArgumentReader arguments);

    //    @DoNotOverride
    //    @Override
    //    default boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
    // @NotNull String[] strings) {
    //        return onCustomCommand(commandSender, command, new ArgumentReader(s, strings));
    //    }
    //    @Override
    //    @DoNotOverride
    //    default List<String> onTabComplete(@NotNull CommandSender var1, @NotNull Command var2, @NotNull String var3,
    // @NotNull String[] var4) {
    //        return onCustomTabComplete(var1, var2, new ArgumentReader(var3, var4));
    //    }

    /**
     * the prefix WILL contains current command name with a blank
     * @param prefix
     * @return
     */
    public Stream<String> getHelp(String prefix);

    static int gint(String val) {
        return CommandUtils.gint(val, (String) null);
    }

    static float gfloat(String val) {
        return CommandUtils.gfloat(val, (String) null);
    }

    static double gdouble(String val) {
        return CommandUtils.gdouble(val, (String) null);
    }

    static boolean gbool(String val) {
        return CommandUtils.gbool(val, (String) null);
    }

    static void enumError(String input) {
        throw new TypeError((String) null, TypeError.BaseArgumentType.ENUM, input);
    }

    static void checkRange(int val, int from, int to) {
        CommandUtils.range(null, val, from, to);
    }

    static void checkRange(float val, float from, float to) {
        CommandUtils.range(null, val, from, to);
    }

    static void checkRange(double val, double from, double to) {
        CommandUtils.range(null, val, from, to);
    }
}
