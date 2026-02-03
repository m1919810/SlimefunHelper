package me.matl114.utils.impl.commands;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleCommandArgs {
    //todo: add Argument type,  consume more args
    //todo: use StringReader
    public static class Argument implements TabProvider {
        @Getter
        private final String argsName;

        public HashSet<String> argsAlias;

        @Getter
        @Setter
        private String defaultValue = null;

        public Function<PlayerEntity, List<String>> tabCompletor = (p)->List.of();

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

        public List<String> getTab(PlayerEntity sender) {
            return tabCompletor.apply(sender);
        }

    }

    public static ArgumentBuilder argumentBuilder(){
        return new ArgumentBuilder();
    }
    @Accessors(fluent = true, chain = true)
    @Getter
    @Setter
    public static class ArgumentBuilder {
        public ArgumentBuilder(){

        }
        String name;
        String defaultValue;
        List<Function<PlayerEntity, Stream<String>>> tabCompletor = new ArrayList<>();

        public ArgumentBuilder tabSupplier(Supplier<Stream<String>> list){
            tabCompletor.add((p)-> list.get());
            return this;
        }

        public ArgumentBuilder tabCompletor(Function<PlayerEntity, Stream<String>> list){
            tabCompletor.add(list);
            return this;
        }

        Set<String> alias = new HashSet<>();

        public ArgumentBuilder alias(String alias) {
            this.alias.add(alias);
            return this;
        }

        public ArgumentBuilder intValue(){
            tabSupplier(CommandUtils.numberStreamSupplier());
            return this;
        }

        public ArgumentBuilder intValue(int def){
            tabSupplier(CommandUtils.numberStreamSupplier()).defaultValue(String.valueOf(def));
            return this;
        }

        public ArgumentBuilder intValue(IntList list){
            tabSupplier(() -> {return list.stream().map(String::valueOf);});
            return this;
        }

        public ArgumentBuilder floatValue(float fl){
            tabSupplier(CommandUtils.floatStreamSupplier()).defaultValue(String.valueOf(fl));
            return this;
        }

        public ArgumentBuilder floatValue(){
            tabSupplier(CommandUtils.floatStreamSupplier());
            return this;
        }

        public ArgumentBuilder select(List<String> list){
            tabSupplier(list::stream);
            return this;
        }
        public ArgumentBuilder select(String... list){
            tabSupplier(()-> Arrays.stream(list));
            return this;
        }

        public ArgumentBuilder select(List<String> list, String def){
            tabSupplier(list::stream).defaultValue(def);
            return this;
        }

        public ArgumentBuilder bool(boolean def){
            tabSupplier(()-> Stream.of("true", "false")).defaultValue(String.valueOf(def));
            return this;
        }
        public ArgumentBuilder bool(){
            tabSupplier(()-> Stream.of("true", "false"));
            return this;
        }

        public <T extends Enum<T>> ArgumentBuilder enumValue(Class<T> type){
            tabSupplier(()-> Arrays.stream(type.getEnumConstants()).map(s -> s.name().toLowerCase(Locale.ROOT)));
            return this;
        }

        public <T extends Enum<T>> ArgumentBuilder enumValue(T value){
            Class<T> type = (Class<T>) value.getClass();
            tabSupplier(()-> Arrays.stream(type.getEnumConstants()).map(s -> s.name().toLowerCase(Locale.ROOT))).defaultValue(value.name().toLowerCase(Locale.ROOT));
            return this;
        }


        public Argument build(){
            var arg = new Argument(name);
            arg.setDefaultValue(defaultValue);
            arg.tabCompletor = (cmd)->{
                List<String> strings = new ArrayList<>();
                for (var en : tabCompletor){
                    en.apply(cmd).forEach(strings::add);
                }
                return strings;
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

    public void setTabCompletor(String arg, Supplier<List<String>> tabCompletor) {
        for (Argument a : args) {
            if (a.argsName.equals(arg)) {
                a.tabCompletor =  (o)-> tabCompletor.get();
            }
        }
    }

    public void setTabCompletor(String arg, Function<PlayerEntity, List<String>> tabCompletor) {
        for (Argument a : args) {
            if (a.argsName.equals(arg)) {
                a.tabCompletor = tabCompletor;
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
                            argsMap.put(selected, new ArgumentInputStream.ArgumentReaderResult(selected, arg2, reader, reader.cursor(), false));
                        }else{
                            argsMap.put(selected, new ArgumentInputStream.ArgumentReaderResult(selected, "", reader, reader.cursor() - 1, false));
                        }
                    } else {
                        // -f -v means boolean
                        reader.step();

                        argsMap.put(selected, new ArgumentInputStream.ArgumentReaderResult(selected, "true", reader, reader.cursor(), false));
                    }

                } else {
                    //the argument is broken, expect a argument but no argument is here
                    break;
                }
            } else {
                Argument selected = argSet.remove(0);
                reader.step();
                argsMap.put(selected, new ArgumentInputStream.ArgumentReaderResult(selected, arg, reader, reader.cursor(), false));
            }
        }
        return new ArgumentInputStream(reader, args, argsMap);
    }
}
