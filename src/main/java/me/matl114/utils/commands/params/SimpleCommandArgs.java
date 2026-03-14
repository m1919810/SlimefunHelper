package me.matl114.matlib.utils.command.params;

import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.IntList;

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
import me.matl114.matlib.common.lang.annotations.DoNotCall;
import me.matl114.matlib.common.lang.annotations.DoNotOverride;
import me.matl114.matlib.utils.command.CommandUtils;
import me.matl114.matlib.utils.command.interruption.ArgumentException;
import me.matl114.matlib.utils.command.interruption.ValueParseError;
import me.matl114.matlib.utils.command.params.api.ArgumentType;
import me.matl114.matlib.utils.command.params.api.InputArgument;
import me.matl114.matlib.utils.command.params.api.TabResult;
import me.matl114.matlib.utils.command.params.impl.AbstractArgumentType;
import me.matl114.matlib.utils.command.params.impl.StringArgumentResult;
import org.bukkit.command.CommandSender;

public class SimpleCommandArgs {
    // todo: add Argument type,  consume more args
    // todo: use StringReader

    public static class Argument extends AbstractArgumentType<String> implements ArgumentType<String> {



        public Argument(String argsName) {
            super(argsName);
        }




        @Override
        public InputArgument<String> consume(ArgumentReader reader) {
            if(reader.hasNext()){

                String arg = reader.next();
                return new StringArgumentResult(arg, this, reader, reader.cursor() - 1);
            }else{
                return new StringArgumentResult(this.defaultValue, this, reader, reader.cursor());
            }
        }
    }

    public static ArgumentBuilder<Argument, String> argumentBuilder(){
        return new ArgumentBuilder<>(Argument::new);
    }

    public static <T extends AbstractArgumentType<W>, W> ArgumentBuilder<T, W> argumentBuilder(Function<String, T> builder){
        return new ArgumentBuilder<>(builder);
    }

    @Accessors(fluent = true, chain = true)
    @Getter
    @Setter
    public static class ArgumentBuilder<W  extends AbstractArgumentType<T>, T> {
        public static final List<String> BOOL_TAB = List.of("true", "false");
        Function<String, W> factory;
        public ArgumentBuilder(Function<String, W> factory) {
            this.factory = factory;
        }

        String name;
        T defaultValue;
        List<TabResult> tabCompletor = new ArrayList<>();

        public ArgumentBuilder<W, T> defaultValue(T name){
            this.defaultValue = name;
            return this;
        }

        public ArgumentBuilder<W, T> defaultValue(String defaultValue) {
            this.defaultValue = (T) defaultValue;
            return this;
        }

        public ArgumentBuilder<W, T> tabCompletor(TabResult result) {
            tabCompletor.add(result);
            return this;
        }

        public ArgumentBuilder<W, T> tabSupplier(Supplier<Stream<String>> list) {
            tabCompletor.add(TabResult.ofStreamSupplier(list));
            return this;
        }

        public ArgumentBuilder<W, T> tabCompletor(Function<CommandSender, Stream<String>> list) {
            tabCompletor.add(TabResult.ofStreamFunction(list));
            return this;
        }

        public ArgumentBuilder<W, T> intValue() {
            tabSupplier(CommandUtils.numberStreamSupplier());
            return this;
        }

        public ArgumentBuilder<W, T> intValue(int def) {
            tabSupplier(CommandUtils.numberStreamSupplier()).defaultValue(String.valueOf(def));
            return this;
        }

        public ArgumentBuilder<W, T> intValue(IntList list) {
            tabSupplier(() -> {
                return list.stream().map(String::valueOf);
            });
            return this;
        }

        public ArgumentBuilder<W, T> floatValue(float fl) {
            tabSupplier(CommandUtils.floatStreamSupplier()).defaultValue(String.valueOf(fl));
            return this;
        }

        public ArgumentBuilder<W, T> floatValue() {
            tabSupplier(CommandUtils.floatStreamSupplier());
            return this;
        }

        public ArgumentBuilder<W, T> select(List<String> list) {
            tabSupplier(list::stream);
            return this;
        }

        public ArgumentBuilder<W, T> select(String... list) {
            tabSupplier(() -> Arrays.stream(list));
            return this;
        }

        public ArgumentBuilder<W, T> select(List<String> list, String def) {
            tabSupplier(list::stream).defaultValue(def);
            return this;
        }

        public ArgumentBuilder<W, T> bool(boolean def) {
            tabSupplier(BOOL_TAB::stream).defaultValue(String.valueOf(def));
            return this;
        }

        public ArgumentBuilder<W, T> bool() {
            tabSupplier(BOOL_TAB::stream);
            return this;
        }

        public <R extends Enum<R>> ArgumentBuilder<W, T> enumValue(Class<R> type) {
            tabSupplier(() ->
                    Arrays.stream(type.getEnumConstants()).map(s -> s.name().toLowerCase(Locale.ROOT)));
            return this;
        }

        public <R extends Enum<R>> ArgumentBuilder<W, T> enumValue(R value) {
            Class<R> type = (Class<R>) value.getClass();
            tabSupplier(() -> Arrays.stream(type.getEnumConstants())
                            .map(s -> s.name().toLowerCase(Locale.ROOT)))
                    .defaultValue(value.name().toLowerCase(Locale.ROOT));
            return this;
        }

        public ArgumentBuilder<W, T> dispatchLast(BiFunction<CommandSender, String, Stream<String>> f) {
            tabCompletor(TabResult.ofDispatcher(f));
            return this;
        }

        public ArgumentBuilder<W, T> dispatchLast(Function<String, Stream<String>> f) {
            dispatchLast((p, str) -> f.apply(str));
            return this;
        }

        public ArgumentBuilder<W, T> dispatchLastArg(Function<InputArgument<?>, Stream<String>> f) {
            dispatchLastArg((p, str) -> f.apply(str));
            return this;
        }

        public ArgumentBuilder<W, T> dispatchLastArg(BiFunction<CommandSender, InputArgument<?>, Stream<String>> f) {
            tabCompletor(TabResult.ofArgDispatcher(f));
            return this;
        }

        public W build() {
            W arg = factory.apply(name);
            arg.setDefaultValue(defaultValue);
            List<TabResult> tabResultList = List.copyOf(tabCompletor);
            arg.tabCompletor = TabResult.ofAll(tabResultList);
            return arg;
        }
    }

    @Getter
    ArgumentType<?>[] args;

    public SimpleCommandArgs(String... args) {
        this.args = Arrays.stream(args).map(Argument::new).toArray(ArgumentType[]::new);
    }

    public SimpleCommandArgs(ArgumentType<?>... args) {
        this.args = args;
    }


    public ArgumentInputStream parseInputStream(ArgumentReader reader) {
        final List<InputArgument<?>> inputArguments = new ArrayList<>();
        List<ArgumentType<?>> argSet = Arrays.stream(args).collect(Collectors.toCollection(ArrayList::new));
        for (var selected :  argSet){
            if(!reader.hasNext()){
                break;
            }
            InputArgument<?> argument = selected.consume(reader);
            if(argument != null){
                inputArguments.add(argument);
            }else{
                throw new ValueParseError(selected.getArgsName(), new ArgumentReader(reader));
            }

        }
        return new ArgumentInputStream(reader, argSet, inputArguments);
    }
}
