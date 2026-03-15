package me.matl114.utils.commands.params.api;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import me.matl114.utils.commands.interruption.ArgumentException;

public interface TabResult {
    public static TabResult EMPTY = (s, arg) -> Stream.empty();

    @Nonnull
    public Stream<String> completeInternal(CommandExecution sender, List<InputArgument<?>> args)
            throws ArgumentException;

    @Nonnull
    default Stream<String> completeOrEmpty(CommandExecution sender, List<InputArgument<?>> args) {
        try {
            return completeInternal(sender, args);
        } catch (ArgumentException e) {
            return Stream.empty();
        }
    }

    default TabResult combine(TabResult result) {
        return (s, arg) -> Stream.concat(completeOrEmpty(s, arg), result.completeOrEmpty(s, arg));
    }

    public static TabResult ofFunction(Function<CommandExecution, List<String>> f) {
        return (s, arg) -> {
            var list = f.apply(s);
            return list == null ? Stream.empty() : list.stream();
        };
    }

    public static TabResult ofStreamFunction(Function<CommandExecution, Stream<String>> f) {
        return (s, arg) -> {
            var list = f.apply(s);
            return list == null ? Stream.empty() : list;
        };
    }

    public static TabResult ofSupplier(Supplier<List<String>> supplier) {
        return (s, arg) -> {
            var list = supplier.get();
            return list == null ? Stream.empty() : list.stream();
        };
    }

    public static TabResult ofStreamSupplier(Supplier<Stream<String>> supplier) {
        return (s, arg) -> {
            var list = supplier.get();
            return list == null ? Stream.empty() : list;
        };
    }

    public static TabResult ofDispatcher(BiFunction<CommandExecution, String, Stream<String>> f) {
        return (p, args) -> {
            if (args.size() < 2) return Stream.empty();
            var re = args.get(args.size() - 2);
            String result = re.resultAsString();
            return result == null ? Stream.empty() : f.apply(p, result);
        };
    }

    public static TabResult ofArgDispatcher(BiFunction<CommandExecution, InputArgument<?>, Stream<String>> f) {
        return (p, args) -> {
            if (args.size() < 2) return Stream.empty();
            var re = args.get(args.size() - 2);
            return re == null ? Stream.empty() : f.apply(p, re);
        };
    }

    default TabResult ofOptional(Predicate<List<InputArgument<?>>> predicate) {
        return (p, args) -> {
            if (predicate.test(args)) return completeOrEmpty(p, args);
            return Stream.empty();
        };
    }

    default TabResult orElse(Predicate<List<InputArgument<?>>> predicate, TabResult result) {
        return (p, args) -> {
            if (predicate.test(args)) return completeOrEmpty(p, args);
            return result.completeOrEmpty(p, args);
        };
    }

    public static TabResult ofAll(List<TabResult> tabSupplier) {
        return (s, arg) -> {
            List<Stream<String>> result = new ArrayList<>(tabSupplier.size());
            for (var re : tabSupplier) {
                result.add(re.completeOrEmpty(s, arg));
            }
            return Streams.concat(result.toArray(Stream[]::new));
        };
    }
}
