package me.matl114.utils.commands;

import com.mojang.brigadier.Command;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.player.PlayerEntity;

@Accessors(fluent = true, chain = true)
@Getter
@Setter
public class DelegateSubCommand extends SubCommand {
    CustomTabExecutor delegate;

    public DelegateSubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        super(name, argsTemplate, help);
    }

    public DelegateSubCommand(String name, SubCommand tree) {
        super(name, tree.template, tree.help);
        this.delegate = tree;
    }

    @Override
    public boolean onCustomCommand(PlayerEntity sender, Command command, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomCommand(sender, command, arguments);
        return false;
    }

    @Override
    public List<String> onCustomTabComplete(PlayerEntity sender, Command command, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomTabComplete(sender, command, arguments);
        return List.of();
    }

    @Override
    public Stream<String> onCustomHelp(PlayerEntity sender, ArgumentReader arguments) {
        if (delegate != null) return delegate.onCustomHelp(sender, arguments);
        return Stream.empty();
    }
}
