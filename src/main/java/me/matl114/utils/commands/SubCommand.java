package me.matl114.utils.commands;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.function.TriFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public abstract class SubCommand implements CustomTabExecutor {
    public static class Builder<T extends SubCommand>{
        List<SimpleCommandArgs.Argument> argsMap = new ArrayList<>();
        String name;
        List<String> helpers = new ArrayList<>();
        String permission;

        List<Consumer<T>> posts = new ArrayList<>();
        TriFunction<String, SimpleCommandArgs, String[],T> builder;
        public Builder(TriFunction<String, SimpleCommandArgs, String[], T> builder){
            this.builder = builder;
        }
        public Builder(Builder<T> builder){
            this.argsMap.addAll(builder.argsMap);
            this.helpers.addAll(builder.helpers);
            this.permission = builder.permission;

            this.builder = builder.builder;
            this.name = builder.name;
        }
        public Builder<T> name(String name){
            this.name = name;
            return this;
        }
        public Builder<T> helpers(List<String> helpers){
            this.helpers.addAll(helpers);
            return this;
        }

        public Builder<T> helpers(String... helpers){
            this.helpers.addAll(Arrays.asList(helpers));
            return this;
        }
        public Builder<T> helper(String helper){
            this.helpers.add(helper);
            return this;
        }

        public Builder<T> permission(String permission){
            this.permission = permission;
            return this;
        }

        private void postBuild(T command){
            if(permission != null){
                command.setPermission(permission);
            }
            posts.forEach(s -> s.accept(command));
        }

        public Builder<T> post(Consumer<T> postTask){
            posts.add(postTask);
            return this;
        }


        public Builder<T> arg(SimpleCommandArgs.Argument arg){
            argsMap.add(arg);
            return this;
        }

        public Builder<T> args(UnaryOperator<SimpleCommandArgs.ArgumentBuilder> arg){
            var builder = new SimpleCommandArgs.ArgumentBuilder();
            arg.apply(builder);
            argsMap.add(builder.build());
            return this;
        }
        public T build(){
            T command = this.builder.apply(name, new SimpleCommandArgs(this.argsMap.toArray(SimpleCommandArgs.Argument[]::new)), helpers.toArray(String[]::new));
            postBuild(command);
            return command;
        }

        public <W extends SubCommand> W build(TriFunction<String, SimpleCommandArgs, String[],W> builder){
            W command = builder.apply(name, new SimpleCommandArgs(this.argsMap.toArray(SimpleCommandArgs.Argument[]::new)), helpers.toArray(String[]::new));
            postBuild((T) command);
            return command;
        }
    }


    public static Builder<SubCommand> emptyBuilder(){
        return new Builder<>(TaskSubCommand::new);
    }

    public static Builder<TaskSubCommand> taskBuilder(){
        return new Builder<>(TaskSubCommand::new);
    }

    public static Builder<TreeSubCommand> treeBuilder(){
        return new Builder<>((a, b, c)-> new TreeSubCommand(a, c));
    }



    public static <W extends SubCommand> Builder<W> factoryBuilder(TriFunction<String, SimpleCommandArgs, String[], W> factory){
        return new Builder<>(factory);
    }


    @Nullable
    @Override
    public String permissionRequired() {
        return permission;
    }

    /**
     * Interface for registering sub-commands with a parent command.
     * Implemented by classes that can contain and manage sub-commands.
     */
    public interface SubCommandCaller {
        /**
         * Registers a sub-command with this caller.
         *
         * @param command The sub-command to register
         */
        public void registerSub(SubCommand command);

        public SubCommand getSubCommand(String name);


        public Collection<SubCommand> getSubCommands();
        //todo: add help interface

        default  <R extends SubCommandCaller, W extends SubCommand> SubBuilder<R,W> subBuilder(Builder<W> builder){
            return  new SubBuilder<>((R)this, builder);
        }



    }



    public static class SubBuilder<R extends SubCommandCaller ,W extends SubCommand> extends Builder<W>{
        R treeSubCommand;
        protected SubBuilder(R root, Builder<W> builder) {
            super(builder);
            this.treeSubCommand = root;
        }

        public R complete(){
            W sb = build();
            treeSubCommand.registerSub(sb);
            return treeSubCommand;
        }

        public W buildAndRegister(){
            W sb = build();
            treeSubCommand.registerSub(sb);
            return sb;
        }

        public <S extends SubCommandCaller> SubBuilder<S, W> cast(){
            return (SubBuilder<S, W>) this;
        }

        public SubBuilder<R,W> name(String name){
            super.name(name);
            return this;
        }
        public SubBuilder<R, W> helpers(List<String> helpers){
            super.helpers(helpers);
            return this;
        }
        public SubBuilder<R, W> helpers(String... helpers){
            super.helpers(helpers);
            return this;
        }
        public SubBuilder<R, W> helper(String helper){
            super.helper(helper);
            return this;
        }

        public SubBuilder<R, W> permission(String permission){
            super.permission(permission);
            return this;
        }

        public SubBuilder<R,W> post(Consumer<W> postTask){
            super.post(postTask);
            return this;
        }



        public SubBuilder<R, W> arg(SimpleCommandArgs.Argument arg){
            super.arg(arg);
            return this;
        }

        public SubBuilder<R, W> args(UnaryOperator<SimpleCommandArgs.ArgumentBuilder> arg){
            super.args(arg);
            return this;
        }


    }

    /** Help text lines for this sub-command */
    /** Should be like an array of string: <name> <argument> <argument> function, function, function**/
    String[] help;

    /** Argument template defining the expected parameters */
    SimpleCommandArgs template;

    /** Name of this sub-command */
    @Getter
    String name;


    @Setter
    String permission;

    /** Whether this sub-command should be hidden from help displays */
    boolean hide = false;



    private SubCommand(){

    }

    /**
     * Creates a new SubCommand with the specified name, argument template, and help text.
     *
     * @param name The name of the sub-command
     * @param argsTemplate The argument template defining expected parameters
     * @param help Help text lines for this sub-command
     */
    public SubCommand(String name, SimpleCommandArgs argsTemplate, String... help) {
        this.name = name;
        this.template = argsTemplate;
        this.help = help;
    }


    /**
     * Creates a new SubCommand with the specified name, argument template, and help text.
     *
     * @param name The name of the sub-command
     * @param argsTemplate The argument template defining expected parameters
     * @param help Help text lines for this sub-command
     */
    public SubCommand(String name, SimpleCommandArgs argsTemplate, List<String> help) {
        this(name, argsTemplate, help.toArray(String[]::new));
    }

    /**
     * Hides this sub-command from help displays.
     *
     * @return This SubCommand instance for method chaining
     */
    public SubCommand hide() {
        this.hide = true;
        return this;
    }

    /**
     * Checks if this sub-command is visible in help displays.
     *
     * @return true if the sub-command is visible, false if hidden
     */
    public boolean isVisiable() {
        return !this.hide;
    }

    /**
     * Registers this sub-command with the specified caller.
     *
     * @param caller The SubCommandCaller to register with
     * @return This SubCommand instance for method chaining
     */
    public SubCommand register(SubCommandCaller caller) {
        caller.registerSub(this);
        return this;
    }

    /**
     * Parses the input arguments according to the argument template.
     * Returns a pair containing the parsed input stream and remaining arguments.
     *
     * @param args The arguments to parse
     * @return A pair containing the parsed input stream and remaining arguments
     */
    @Nonnull
    @Deprecated(forRemoval = true)
    public Pair<ArgumentInputStream, String[]> parseInput(String[] args) {
        ArgumentReader reader = new ArgumentReader(args);
        var re = template.parseInputStream(reader);
        return new Pair<>(re, reader.getRemainingArgs());
    }

    @Nonnull
    public ArgumentInputStream parseInput(ArgumentReader args) {
        return template.parseInputStream(args);
    }


    @Override
    public Stream<String> getHelp(String prefix) {
        return Arrays.stream(help).map(s -> prefix + s);
    }

//    /**
//     * Parses arguments and creates a CommandArgumentMap for easy access to argument values.
//     *
//     * @param args The arguments to parse
//     * @return A CommandArgumentMap containing the parsed arguments
//     */
//    public CommandArgumentMap parseArgument(String[] args) {
//        return new CommandArgumentMap(CommandUtils.parseArguments(args, this.template.getArgs()));
//    }

    /**
     * Sets a default value for the specified argument.
     *
     * @param arg The argument name
     * @param val The default value
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setDefault(String arg, String val) {
        this.template.setDefault(arg, val);
        return this;
    }

    /**
     * Configures an argument as an integer with default value 0.
     * Also sets up tab completion for integer values.
     *
     * @param arg The argument name
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setInt(String arg) {
        setDefault(arg, "0");
        setTabCompletor(arg, CommandUtils.numberSupplier());
        return this;
    }

    /**
     * Configures an argument as an integer with the specified default value.
     * Also sets up tab completion for integer values.
     *
     * @param arg The argument name
     * @param val The default integer value
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setInt(String arg, int val) {
        setDefault(arg, String.valueOf(val));
        setTabCompletor(arg, CommandUtils.numberSupplier());
        return this;
    }

    /**
     * Configures an argument as a float with default value 0.0.
     * Also sets up tab completion for float values.
     *
     * @param arg The argument name
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setFloat(String arg) {
        setDefault(arg, "0.0");
        setTabCompletor(arg, CommandUtils.floatSupplier());
        return this;
    }

    /**
     * Configures an argument as a float with the specified default value.
     * Also sets up tab completion for float values.
     *
     * @param arg The argument name
     * @param val The default float value
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setFloat(String arg, float val) {
        setDefault(arg, String.valueOf(val));
        setTabCompletor(arg, CommandUtils.floatSupplier());
        return this;
    }

    /**
     * Configures an argument as an enum with the specified values.
     * Sets up tab completion for the enum values.
     *
     * @param arg The argument name
     * @param enumValues The collection of valid enum values
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setEnum(String arg, Collection<String> enumValues) {
        List<String> enums = enumValues.stream().toList();
        setTabCompletor(arg, () -> enums);
        return this;
    }

    /**
     * Configures an argument as an enum with the specified default value and enum values.
     * Sets up tab completion for the enum values.
     *
     * @param arg The argument name
     * @param defaultValue The default enum value
     * @param enumValues The valid enum values
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setEnum(String arg, String defaultValue, String... enumValues) {
        return setEnum(arg, defaultValue, List.of(enumValues));
    }

    /**
     * Configures an argument as an enum with the specified default value and enum values.
     * Sets up tab completion for the enum values.
     *
     * @param arg The argument name
     * @param defaultValue The default enum value
     * @param enumValues The collection of valid enum values
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setEnum(String arg, String defaultValue, Collection<String> enumValues) {
        List<String> enums = enumValues.stream().toList();
        setDefault(arg, defaultValue);
        setTabCompletor(arg, () -> enums);
        return this;
    }

    /**
     * Sets a custom tab completer for the specified argument.
     *
     * @param arg The argument name
     * @param completions A supplier that provides tab completion suggestions
     * @return This SubCommand instance for method chaining
     */
    public SubCommand setTabCompletor(String arg, Supplier<List<String>> completions) {
        this.template.setTabCompletor(arg, completions);
        return this;
    }


}