package me.matl114.utils.commands;

import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.utils.interruptions.ArgumentException;
import net.minecraft.entity.player.PlayerEntity;

public class SimpleCommandArgs {
    public static interface TabResult {
        public static TabResult EMPTY = (s, arg) -> Stream.empty();

        @Nonnull
        public Stream<String> completeInternal(PlayerEntity sender, List<InputArgument> args) throws ArgumentException;

        @Nonnull
        default Stream<String> completeOrEmpty(PlayerEntity sender, List<InputArgument> args) {
            try {
                return completeInternal(sender, args);
            } catch (ArgumentException e) {
                return Stream.empty();
            }
        }

        default TabResult combine(TabResult result) {
            return (s, arg) -> Stream.concat(completeOrEmpty(s, arg), result.completeOrEmpty(s, arg));
        }

        public static TabResult ofFunction(Function<PlayerEntity, List<String>> f) {
            return (s, arg) -> {
                var list = f.apply(s);
                return list == null ? Stream.empty() : list.stream();
            };
        }

        public static TabResult ofStreamFunction(Function<PlayerEntity, Stream<String>> f) {
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

        public static TabResult ofDispatcher(BiFunction<PlayerEntity, String, Stream<String>> f) {
            return (p, args) -> {
                if (args.isEmpty()) return Stream.empty();
                var re = args.get(args.size() - 1);
                String result = re.result();
                return result == null ? Stream.empty() : f.apply(p, result);
            };
        }

        public static TabResult ofArgDispatcher(BiFunction<PlayerEntity, InputArgument, Stream<String>> f) {
            return (p, args) -> {
                if (args.isEmpty()) return Stream.empty();
                var re = args.get(args.size() - 1);
                return re == null ? Stream.empty() : f.apply(p, re);
            };
        }

        default TabResult ofOptional(Predicate<List<InputArgument>> predicate) {
            return (p, args) -> {
                if (predicate.test(args)) return completeOrEmpty(p, args);
                return Stream.empty();
            };
        }

        default TabResult orElse(Predicate<List<InputArgument>> predicate, TabResult result) {
            return (p, args) -> {
                if (predicate.test(args)) return completeOrEmpty(p, args);
                return result.completeOrEmpty(p, args);
            };
        }
    }

    public static class Argument implements TabProvider {
        @Getter
        private final String argsName;

        public HashSet<String> argsAlias;

        @Getter
        @Setter
        private String defaultValue = null;

        public TabResult tabCompletor = TabResult.EMPTY;

        public Argument(String argsName) {
            this.argsName = argsName;
            this.argsAlias = new HashSet<>();
            argsAlias.add(argsName);
            argsAlias.add(argsName.toLowerCase());
            argsAlias.add(argsName.toUpperCase());
            argsAlias.add(argsName.substring(0, 1));
            argsAlias.add(argsName.substring(0, 1).toLowerCase());
            argsAlias.add(argsName.substring(0, 1).toUpperCase());
        }

        public boolean isAlias(String arg) {
            return argsAlias.contains(arg);
        }

        public Stream<String> getTab(PlayerEntity sender, List<InputArgument> args) {
            try {
                return tabCompletor.completeOrEmpty(sender, args);
            } catch (ArgumentException argumentException) {
                return Stream.of("参数错误");
            }
        }
    }

    public static ArgumentBuilder argumentBuilder() {
        return new ArgumentBuilder();
    }

    @Accessors(fluent = true, chain = true)
    @Getter
    @Setter
    public static class ArgumentBuilder {
        public ArgumentBuilder() {}

        String name;
        String defaultValue;
        List<TabResult> tabCompletor = new ArrayList<>();

        public ArgumentBuilder tabCompletor(TabResult result) {
            tabCompletor.add(result);
            return this;
        }

        public ArgumentBuilder tabSupplier(Supplier<Stream<String>> list) {
            tabCompletor.add(TabResult.ofStreamSupplier(list));
            return this;
        }

        public ArgumentBuilder tabCompletor(Function<PlayerEntity, Stream<String>> list) {
            tabCompletor.add(TabResult.ofStreamFunction(list));
            return this;
        }

        Set<String> alias = new HashSet<>();

        public ArgumentBuilder alias(String alias) {
            this.alias.add(alias);
            return this;
        }

        public ArgumentBuilder intValue() {
            tabSupplier(CommandUtils.numberStreamSupplier());
            return this;
        }

        public ArgumentBuilder intValue(int def) {
            tabSupplier(CommandUtils.numberStreamSupplier()).defaultValue(String.valueOf(def));
            return this;
        }

        public ArgumentBuilder intValue(IntList list) {
            tabSupplier(() -> {
                return list.stream().map(String::valueOf);
            });
            return this;
        }

        public ArgumentBuilder floatValue(float fl) {
            tabSupplier(CommandUtils.floatStreamSupplier()).defaultValue(String.valueOf(fl));
            return this;
        }

        public ArgumentBuilder floatValue() {
            tabSupplier(CommandUtils.floatStreamSupplier());
            return this;
        }

        public ArgumentBuilder select(List<String> list) {
            tabSupplier(list::stream);
            return this;
        }

        public ArgumentBuilder select(String... list) {
            tabSupplier(() -> Arrays.stream(list));
            return this;
        }

        public ArgumentBuilder select(List<String> list, String def) {
            tabSupplier(list::stream).defaultValue(def);
            return this;
        }

        public ArgumentBuilder bool(boolean def) {
            tabSupplier(() -> Stream.of("true", "false")).defaultValue(String.valueOf(def));
            return this;
        }

        public ArgumentBuilder bool() {
            tabSupplier(() -> Stream.of("true", "false"));
            return this;
        }

        public <T extends Enum<T>> ArgumentBuilder enumValue(Class<T> type) {
            tabSupplier(() ->
                    Arrays.stream(type.getEnumConstants()).map(s -> s.name().toLowerCase(Locale.ROOT)));
            return this;
        }

        public <T extends Enum<T>> ArgumentBuilder enumValue(T value) {
            Class<T> type = (Class<T>) value.getClass();
            tabSupplier(() -> Arrays.stream(type.getEnumConstants())
                            .map(s -> s.name().toLowerCase(Locale.ROOT)))
                    .defaultValue(value.name().toLowerCase(Locale.ROOT));
            return this;
        }

        public ArgumentBuilder dispatchLast(BiFunction<PlayerEntity, String, Stream<String>> f) {
            tabCompletor(TabResult.ofDispatcher(f));
            return this;
        }

        public ArgumentBuilder dispatchLast(Function<String, Stream<String>> f) {
            dispatchLast((p, str) -> f.apply(str));
            return this;
        }

        public ArgumentBuilder dispatchLastArg(Function<InputArgument, Stream<String>> f) {
            dispatchLastArg((p, str) -> f.apply(str));
            return this;
        }

        public ArgumentBuilder dispatchLastArg(BiFunction<PlayerEntity, InputArgument, Stream<String>> f) {
            tabCompletor(TabResult.ofArgDispatcher(f));
            return this;
        }

        public Argument build() {
            var arg = new Argument(name);
            arg.setDefaultValue(defaultValue);
            List<TabResult> tabResultList = List.copyOf(tabCompletor);
            arg.tabCompletor = (p, args) -> {
                return Streams.concat((Stream<String>[]) tabCompletor.stream()
                        .map(s -> s.completeOrEmpty(p, args))
                        .toArray(Stream[]::new));
            };
            arg.argsAlias.addAll(alias);
            return arg;
        }
    }

    @Getter
    Argument[] args;

    public SimpleCommandArgs(String... args) {
        this.args = Arrays.stream(args).map(Argument::new).toArray(Argument[]::new);
    }

    public SimpleCommandArgs(Argument... args) {
        this.args = args;
    }

    public void setDefault(String arg, String defaultValue) {
        for (Argument a : args) {
            if (a.argsName.equals(arg)) {
                a.defaultValue = defaultValue;
            }
        }
    }

    public void setTabCompletor(String arg, TabResult tabCompletor) {
        for (Argument a : args) {
            if (a.argsName.equals(arg)) {
                a.tabCompletor = tabCompletor;
            }
        }
    }

    public void setTabCompletor(String arg, Supplier<List<String>> tabCompletor) {
        for (Argument a : args) {
            if (a.argsName.equals(arg)) {
                a.tabCompletor = TabResult.ofSupplier(tabCompletor);
            }
        }
    }

    public void setTabCompletor(String arg, Function<PlayerEntity, List<String>> tabCompletor) {
        for (Argument a : args) {
            if (a.argsName.equals(arg)) {
                a.tabCompletor = TabResult.ofFunction(tabCompletor);
            }
        }
    }

    public ArgumentInputStream parseInputStream(ArgumentReader reader) {
        final HashMap<Argument, ArgumentInputStream.ArgumentReaderResult> argsMap = new HashMap<>();
        //        Iterator<String> iter = Arrays.stream(input).iterator();
        List<Argument> argSet = Arrays.stream(args).collect(Collectors.toCollection(ArrayList::new));
        Object2IntMap<Argument> argsCursorSet = new Object2IntOpenHashMap<>();
        while (reader.hasNext() && !argSet.isEmpty()) {
            String arg = reader.peek();
            if (arg.startsWith("-")) {
                Argument selected = null;
                String trueName = arg.replaceFirst("^-+", "");
                for (Argument a : args) {
                    if (a.isAlias(trueName)) {
                        selected = a;
                        break;
                    }
                }
                if (selected != null) {
                    argSet.remove(selected);
                    if (arg.startsWith("--")) {
                        reader.step();
                        // --args inputValue
                        if (reader.hasNext()) {
                            String arg2 = reader.peek();
                            reader.step();
                            argsMap.put(
                                    selected,
                                    new ArgumentInputStream.ArgumentReaderResult(
                                            selected, arg2, reader, reader.cursor(), false));
                        } else {
                            argsMap.put(
                                    selected,
                                    new ArgumentInputStream.ArgumentReaderResult(
                                            selected, "", reader, reader.cursor() - 1, false));
                        }
                    } else {
                        // -f -v means boolean
                        reader.step();

                        argsMap.put(
                                selected,
                                new ArgumentInputStream.ArgumentReaderResult(
                                        selected, "true", reader, reader.cursor(), false));
                    }
                    continue;
                }
                //                else {
                //                    // the argument is broken, expect a argument but no argument is here
                //                    break;
                //                }
                // - maybe part of a number
            }
            Argument selected = argSet.remove(0);
            reader.step();
            argsMap.put(
                    selected,
                    new ArgumentInputStream.ArgumentReaderResult(selected, arg, reader, reader.cursor(), false));
        }
        return new ArgumentInputStream(reader, args, argsMap);
    }
}
