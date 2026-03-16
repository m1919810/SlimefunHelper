package me.matl114.utils.commands.commandGroup;

import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.utils.commands.interruption.PermissionDenyError;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Accessors(fluent = true, chain = true)
@Getter
@Setter
public class DelegateSubCommand implements SubCommand {
    CustomTabExecutor delegate;
    String name;
    String permissionNode;

    public DelegateSubCommand(String name, CustomTabExecutor delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public String permissionRequired() {
        return permissionNode;
    }

    @NotNull
    @Override
    public ArgumentInputStream parseInput(CommandExecution execution, ArgumentReader args) {
        if (this.delegate != null) {
            return (this.delegate).parseInput(execution, args);
        } else {
            return new ArgumentInputStream(execution, args, List.of(), List.of());
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean onCustomCommand(CommandExecution sender, ArgumentReader arguments) {
        if (hasPermission(sender)) {
            if (delegate != null) {
                return delegate.onCustomCommand(sender, arguments);
            }
            return false;
        } else {
            throw new PermissionDenyError(permissionNode, arguments);
        }
    }

    @Override
    public List<String> onCustomTabComplete(CommandExecution sender, ArgumentReader arguments) {
        if (hasPermission(sender) && delegate != null) return delegate.onCustomTabComplete(sender, arguments);
        return List.of();
    }

    @Override
    public Stream<String> onCustomHelp(CommandExecution sender, ArgumentReader arguments) {
        if (hasPermission(sender) && delegate != null) return delegate.onCustomHelp(sender, arguments);
        return Stream.empty();
    }

    @Override
    public Stream<String> getHelp(String prefix) {
        if (delegate != null) {
            return delegate.getHelp(prefix);
        } else {
            return Stream.empty();
        }
    }

    @Override
    public void setPermission(String permission) {
        permissionNode = permission;
    }
}
