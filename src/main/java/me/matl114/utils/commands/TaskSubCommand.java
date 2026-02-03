package me.matl114.utils.impl.commands;

import com.mojang.brigadier.Command;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

@Accessors(fluent = true)
public class TaskSubCommand extends SubCommand {
    @Setter
    @Getter
    CommandContext executor;
    public TaskSubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        super(name, argsTemplate, help);
    }


    @Override
    public List<String> onCustomTabComplete(PlayerEntity sender, @Nullable Command command, ArgumentReader arguments) {
        var re =  this.parseInput(arguments);
        if(arguments.hasNext()){
            //already filled all the arguments so use executor to supply the extra args
            return executor == null ? List.of() : executor.supplyTab(sender, re, arguments);
        }else{
            return re.getTabComplete(sender);
        }
    }

    @Override
    public boolean onCustomCommand(PlayerEntity sender, Command command, ArgumentReader arguments) {
        return executor != null && executor.execute(sender, parseInput(arguments), arguments);
    }

    @Override
    public Stream<String> onCustomHelp(PlayerEntity sender, ArgumentReader arguments) {
        if(hasPermission(sender)){
            return getHelp(arguments.getAlreadyReadCmdStr());
        }else{
            return Stream.empty();
        }
    }
}
