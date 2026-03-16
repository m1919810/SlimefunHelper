package me.matl114.utils.commands.commandGroup;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.TabResult;
import org.apache.commons.lang3.function.TriFunction;

/**
 * Represents a sub-command within a command group system.
 * This class provides functionality for creating, configuring, and executing sub-commands
 * with argument parsing, tab completion, and permission checking capabilities.
 *
 * <p>SubCommand instances can be configured with various argument types (int, float, enum)
 * and can be hidden from help displays. They support both custom executors and
 * built-in argument parsing functionality.</p>
 *
 * <p>Each SubCommand has a name, help text, argument template, and optional executor.
 * The class implements TabExecutor to provide tab completion functionality.</p>
 */
public interface SubCommand extends CustomTabExecutor {
    public static class Builder<T extends SubCommand> {
        List<ArgumentType<?>> argsMap = new ArrayList<>();
        String name;
        List<String> helpers = new ArrayList<>();
        String permission;
        List<Consumer<T>> posts = new ArrayList<>();
        TriFunction<String, SimpleCommandArgs, String[], T> builder;

        public Builder(TriFunction<String, SimpleCommandArgs, String[], T> builder) {
            this.builder = builder;
        }

        public Builder(Builder<T> builder) {
            this.argsMap.addAll(builder.argsMap);
            this.helpers.addAll(builder.helpers);
            this.permission = builder.permission;
            this.builder = builder.builder;
            this.name = builder.name;
        }

        public Builder<T> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<T> helpers(List<String> helpers) {
            this.helpers.addAll(helpers);
            return this;
        }

        public Builder<T> helpers(String... helpers) {
            this.helpers.addAll(Arrays.asList(helpers));
            return this;
        }

        public Builder<T> helper(String helper) {
            this.helpers.add(helper);
            return this;
        }

        public Builder<T> permission(String permission) {
            this.permission = permission;
            return this;
        }

        private void postBuild(T command) {
            if (permission != null) {
                command.setPermission(permission);
            }
            posts.forEach(s -> s.accept(command));
        }

        public Builder<T> post(Consumer<T> postTask) {
            posts.add(postTask);
            return this;
        }

        public Builder<T> arg(ArgumentType<?> arg) {
            argsMap.add(arg);
            return this;
        }

        public Builder<T> args(UnaryOperator<SimpleCommandArgs.ArgumentBuilder<?, ?>> arg) {
            var builder = SimpleCommandArgs.argumentBuilder();
            arg.apply(builder);
            argsMap.add(builder.build());
            return this;
        }

        public Builder<T> args(SimpleCommandArgs args) {
            for (var arg : args.getArgs()) {
                this.arg(arg);
            }
            return this;
        }

        public T build() {
            T command = this.builder.apply(
                    name,
                    new SimpleCommandArgs(this.argsMap.toArray(ArgumentType<?>[]::new)),
                    helpers.toArray(String[]::new));
            postBuild(command);
            return command;
        }

        public <W extends SubCommand> W build(TriFunction<String, SimpleCommandArgs, String[], W> builder) {
            W command = builder.apply(
                    name,
                    new SimpleCommandArgs(this.argsMap.toArray(ArgumentType<?>[]::new)),
                    helpers.toArray(String[]::new));
            postBuild((T) command);
            return command;
        }
    }

    public static Builder<SubCommand> emptyBuilder() {
        return new Builder<>(TaskSubCommand::new);
    }

    public static Builder<TaskSubCommand> taskBuilder() {
        return new Builder<>(TaskSubCommand::new);
    }

    public static Builder<TreeSubCommand> treeBuilder() {
        return new Builder<>((a, b, c) -> new TreeSubCommand(a, c));
    }

    public static <W extends SubCommand> Builder<W> factoryBuilder(
            TriFunction<String, SimpleCommandArgs, String[], W> factory) {
        return new Builder<>(factory);
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

        public Collection<SubCommand> getSubCommands();

        public SubCommand getFallbackCommand();

        public void setFallbackCommand(SubCommand fallbackCommand, TabResult fallbackTabSuggestor);

        default <T extends SubCommandCaller> T withFallback(
                SubCommand fallbackCommand, Supplier<Stream<String>> fallbackTabSuggestor) {
            setFallbackCommand(fallbackCommand, TabResult.ofStreamSupplier(fallbackTabSuggestor));
            return (T) this;
        }
        // todo: add help interface

        default <R extends SubCommandCaller, W extends SubCommand> SubBuilder<R, W> subBuilder(Builder<W> builder) {
            return new SubBuilder<>((R) this, builder);
        }
    }

    public static class SubBuilder<R extends SubCommandCaller, W extends SubCommand> extends Builder<W> {
        R treeSubCommand;

        protected SubBuilder(R root, Builder<W> builder) {
            super(builder);
            this.treeSubCommand = root;
        }

        public R complete() {
            W sb = build();
            treeSubCommand.registerSub(sb);
            return treeSubCommand;
        }

        public W buildAndRegister() {
            W sb = build();
            treeSubCommand.registerSub(sb);
            return sb;
        }

        public <S extends SubCommandCaller> SubBuilder<S, W> cast() {
            return (SubBuilder<S, W>) this;
        }

        public SubBuilder<R, W> name(String name) {
            super.name(name);
            return this;
        }

        public SubBuilder<R, W> helpers(List<String> helpers) {
            super.helpers(helpers);
            return this;
        }

        public SubBuilder<R, W> helpers(String... helpers) {
            super.helpers(helpers);
            return this;
        }

        public SubBuilder<R, W> helper(String helper) {
            super.helper(helper);
            return this;
        }

        public SubBuilder<R, W> permission(String permission) {
            super.permission(permission);
            return this;
        }

        public SubBuilder<R, W> post(Consumer<W> postTask) {
            super.post(postTask);
            return this;
        }

        public SubBuilder<R, W> arg(ArgumentType<?> arg) {
            super.arg(arg);
            return this;
        }

        public SubBuilder<R, W> args(UnaryOperator<SimpleCommandArgs.ArgumentBuilder<?, ?>> arg) {
            super.args(arg);
            return this;
        }

        public SubBuilder<R, W> args(SimpleCommandArgs args) {
            super.args(args);
            return this;
        }
    }

    public void setPermission(String permission);

    default SubCommand register(SubCommandCaller caller) {
        caller.registerSub(this);
        return this;
    }
}
