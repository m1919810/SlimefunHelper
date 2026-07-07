package me.matl114.utils.commands.commandGroup;

import java.util.List;
import java.util.function.*;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.entity.player.PlayerEntity;
import org.apache.commons.lang3.function.TriFunction;

public interface CommandContext {
    public boolean execute(CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader);

    default List<String> supplyTab(CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
        return List.of();
    }

    public static CommandContext run(TriFunction<PlayerEntity, ArgumentInputStream, ArgumentReader, Boolean> delegate) {
        return ((var1, streamArgs, argsReader) -> {
            if (var1.isPlayer()) {
                return delegate.apply(var1.getExecutor(), streamArgs, argsReader);
            }
            return false;
        });
    }

    public static CommandContext run(Runnable task) {
        return (var1, streamArgs, argsReader) -> {
            task.run();
            return true;
        };
    }

    public static CommandContext run(BooleanSupplier task) {
        return ((var1, streamArgs, argsReader) -> {
            return task.getAsBoolean();
        });
    }

    public static CommandContext run(Consumer<ArgumentInputStream> var) {
        return ((var1, streamArgs, argsReader) -> {
            var.accept(streamArgs);
            return true;
        });
    }

    public static CommandContext run(BiConsumer<PlayerEntity, ArgumentInputStream> var) {
        return ((var1, streamArgs, argsReader) -> {
            if (var1.isPlayer()) {
                var.accept(var1.getExecutor(), streamArgs);
                return true;
            }
            return false;
        });
    }

    public static CommandContext run(BiPredicate<PlayerEntity, ArgumentInputStream> var) {
        return (var1, streamArgs, argsReader) -> {
            if (var1.isPlayer()) {
                return var.test(var1.getExecutor(), streamArgs);
            } else return false;
        };
    }

    public static CommandContext execute(Consumer<CommandExecution> var) {
        return ((var1, streamArgs, argsReader) -> {
            var.accept(var1);
            return true;
        });
    }

    public static CommandContext execute(BiConsumer<CommandExecution, ArgumentInputStream> var) {
        return ((var1, streamArgs, argsReader) -> {
            var.accept(var1, streamArgs);
            return true;
        });
    }

    public static CommandContext execute(BiPredicate<CommandExecution, ArgumentInputStream> var) {
        return (var1, streamArgs, argsReader) -> {
            return var.test(var1, streamArgs);
        };
    }
}
