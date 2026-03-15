package me.matl114.utils.commands.params;

import com.google.common.collect.Streams;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import org.jetbrains.annotations.Nullable;

public class ArgumentInputStream {

    public ArgumentInputStream(
            CommandExecution execution,
            ArgumentReader reader,
            List<ArgumentType<?>> argsSet,
            List<InputArgument<?>> argsMap) {
        this.execution = execution;
        this.reader = new ArgumentReader(reader);
        this.arguments = argsSet;
        this.argsMap = argsMap;
    }

    CommandExecution execution;
    ArgumentReader reader;
    List<ArgumentType<?>> arguments;
    List<InputArgument<?>> argsMap;
    //    Map<ArgumentType<?>, InputArgument<?>> argsMap;
    int i = 0;

    public boolean hasNext() {
        return i < arguments.size();
    }

    public ArgumentType<?> nextArgument() {
        return arguments.get(i++);
    }

    private void solveTo(int i) {
        for (var s = argsMap.size(); s <= i; s++) {
            ArgumentType<?> type = arguments.get(s);
            argsMap.add(type.consume(this.execution, argsMap, this.reader));
        }
    }

    public <T> InputArgument<T> peekNext() {
        if (hasNext()) {
            solveTo(i);
            return (InputArgument<T>) argsMap.get(i);
        } else {
            throw new RuntimeException("Illegal to access undeclared argument");
        }
    }

    @Nonnull
    public <T> InputArgument<T> next() {
        if (hasNext()) {
            int idx = i;
            solveTo(idx);
            nextArgument();
            return (InputArgument<T>) argsMap.get(idx);
        } else {
            throw new RuntimeException("Illegal to access undeclared argument");
        }
    }

    @Nullable
    public <T> T nextArg() {
        return this.<T>next().result();
    }

    public int nextInt() {
        return next().getInt();
    }

    public boolean nextBoolean() {
        return next().getBoolean();
    }

    public double nextDouble() {
        return next().getDouble();
    }

    public float nextFloat() {
        return next().getFloat();
    }

    public int nextClampedInt(int from, int toExclude) {
        return next().clampInt(from, toExclude);
    }

    public double nextClampedDouble(double from, double toExclu) {
        return next().clampDouble(from, toExclu);
    }

    public float nextClampedFloat(float from, float to) {
        return next().clampFloat(from, to);
    }

    @Nonnull
    public <T> T nextNonnull() {
        return this.<T>next().nonnullResult();
    }

    @Nonnull
    public String nextNonnullString() {
        return this.next().nonnullResultAsString();
    }

    public <T extends Enum<T>> T nextEnum(Class<T> type) {
        return next().enumResult(type);
    }

    public String nextSelect(Collection<String> selections) {
        return next().selectResult(selections);
    }

    @Nonnull
    public Stream<String> getTabComplete(CommandExecution sender) {
        if (argsMap.isEmpty()) {
            return Stream.empty();
        } else {
            // we only tab at the last block of argument, so check the total length first
            int wasAboutToTab = this.reader.getLength() - 1;
            // argument not fully tabbed, tab the last argument present
            int i = argsMap.size();
            final int index = i - 1;
            // remove this operation, that's ridiculous
            // argumentInputs.remove(argumentInputs.size() - 1);
            InputArgument<?> lastArgumentParsed = this.argsMap.get(index);
            int tabbingCursorPos = lastArgumentParsed.getStartIndex();
            if (tabbingCursorPos == wasAboutToTab) {
                List<Stream<String>> streams = new ArrayList<>();
                for (var s = index; s >= 0; --s) {
                    InputArgument<?> argument = this.argsMap.get(s);
                    // the argument before this will not be tabbed
                    if (argument.getStartIndex() < tabbingCursorPos) {
                        break;
                    }
                    var tabResult = arguments.get(s).getTab(sender, s == index ? argsMap : argsMap.subList(0, s + 1));
                    if (tabResult != null) {
                        streams.add(tabResult);
                    }
                }
                return Streams.concat(streams.toArray(Stream[]::new));
            } else {
                return Stream.empty();
            }
        }
    }

    public <T> T nextArgOrDefault(Supplier<T> def) {
        T val = this.<T>next().result();
        return val == null ? def.get() : val;
    }
}
