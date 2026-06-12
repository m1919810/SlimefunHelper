package me.matl114.utils.commands.params.impl;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import org.jetbrains.annotations.Nullable;

public class OptionalArgumentType<T> extends AbstractArgumentType<T> implements ArgumentType<T> {
    private final ArgumentType<? extends T> delegate;
    private final BiPredicate<CommandExecution, InputArgument<? extends T>> predicate;

    public OptionalArgumentType(String argsName, ArgumentType<? extends T> delegate, T defaultValue) {
        this(argsName, delegate, defaultValue, (execution, argument) -> true);
    }

    public OptionalArgumentType(
            String argsName,
            ArgumentType<? extends T> delegate,
            T defaultValue,
            Predicate<InputArgument<? extends T>> predicate) {
        this(argsName, delegate, defaultValue, (execution, argument) -> predicate.test(argument));
    }

    public OptionalArgumentType(
            String argsName,
            ArgumentType<? extends T> delegate,
            T defaultValue,
            BiPredicate<CommandExecution, InputArgument<? extends T>> predicate) {
        super(argsName);
        this.delegate = delegate;
        this.defaultValue = defaultValue;
        this.predicate = predicate;
    }

    @Override
    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
        return Stream.concat(super.getTab(sender, args), delegate.getTab(sender, args));
    }

    @Nullable
    @Override
    public InputArgument<T> consume(CommandExecution sender, List<InputArgument<?>> args, ArgumentReader reader) {
        int startIndex = reader.cursor();
        if (!reader.hasNext()) {
            return defaultResult(reader, startIndex);
        }
        InputArgument<? extends T> parsed = delegate.consume(sender, args, reader);
        if (parsed != null && parsed.isParseSuccess() && accepted(sender, parsed)) {
            try {
                return new OptionalArgumentResult<>(
                        parsed.result(), this, reader, startIndex, parsed.resultAsString(), true);
            } catch (Throwable ignored) {
            }
        }
        reader.setCursor(startIndex);
        return defaultResult(reader, startIndex);
    }

    private boolean accepted(CommandExecution sender, InputArgument<? extends T> parsed) {
        try {
            return predicate.test(sender, parsed);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private OptionalArgumentResult<T> defaultResult(ArgumentReader reader, int startIndex) {
        return new OptionalArgumentResult<>(defaultValue, this, reader, startIndex, null, true);
    }

    private static class OptionalArgumentResult<T> extends AbstractArgumentResult<T> {
        private final String rawString;

        private OptionalArgumentResult(
                T result,
                ArgumentType<T> type,
                ArgumentReader reader,
                int startIndex,
                String rawString,
                boolean parseSuccess) {
            super(result, type, reader, startIndex);
            this.rawString = rawString;
            this.parseSuccess = parseSuccess;
        }

        @Override
        public String resultAsString() {
            return rawString;
        }
    }
}