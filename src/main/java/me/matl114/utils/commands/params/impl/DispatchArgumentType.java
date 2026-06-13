package me.matl114.utils.commands.params.impl;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import org.jetbrains.annotations.Nullable;

public class DispatchArgumentType<T> implements ArgumentType<T> {
    String name;

    public DispatchArgumentType(String name) {
        this.name = name;
    }

    List<Pair<BiPredicate<CommandExecution, List<InputArgument<?>>>, ArgumentType<? extends T>>> dispatchMap =
            new ArrayList<>();

    public DispatchArgumentType<T> registerDispatcher(
            BiPredicate<CommandExecution, List<InputArgument<?>>> predicate, ArgumentType<? extends T> type) {
        dispatchMap.add(Pair.of(predicate, type));
        return this;
    }

    public DispatchArgumentType<T> registerArgumentDispatcher(
            int argumentIndex, String string, ArgumentType<? extends T> type) {
        registerDispatcher(
                ((execution, arguments) -> {
                    try {
                        int index = argumentIndex < 0 ? arguments.size() + argumentIndex : argumentIndex;
                        if (index < 0 || index >= arguments.size()) {
                            return false;
                        }
                        var re = arguments.get(index);
                        return re != null
                                && re.isParseSuccess()
                                && re.nonnullResultAsString().equalsIgnoreCase(string);
                    } catch (Throwable ignored) {
                        return false;
                    }
                }),
                type);
        return this;
    }

    @Override
    public String getArgsName() {
        return this.name;
    }

    @Override
    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
        for (var re : dispatchMap) {
            if (re.getFirst().test(sender, args)) {
                return re.getSecond().getTab(sender, args);
            }
        }
        return Stream.empty();
    }

    @Nullable
    @Override
    public InputArgument<T> consume(CommandExecution sender, List<InputArgument<?>> args, ArgumentReader reader) {
        for (var re : dispatchMap) {
            if (re.getFirst().test(sender, args)) {
                return (InputArgument<T>) re.getSecond().consume(sender, args, reader);
            }
        }
        return new EmptyArgumentResult<>(this, reader);
    }
}
