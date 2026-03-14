package me.matl114.matlib.utils.command.params;

import java.util.*;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.matlib.utils.command.interruption.TypeError;
import me.matl114.matlib.utils.command.interruption.ValueAbsentError;
import me.matl114.matlib.utils.command.params.api.ArgumentType;
import me.matl114.matlib.utils.command.params.api.InputArgument;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

public class ArgumentInputStream {

    public ArgumentInputStream(
            ArgumentReader reader,
            List<ArgumentType<?>> argsSet,
            List<InputArgument<?>> argsMap) {
        this.reader = new ArgumentReader(reader);
        this.arguments = argsSet;
        this.argsMap = new LinkedHashMap<>();
        for (var re : argsMap){
            this.argsMap.put(re.getType(), re);
        }
    }

    ArgumentReader reader;
    List<ArgumentType<?>> arguments;
    Map<ArgumentType<?>, InputArgument<?>> argsMap;
    int i = 0;

    public boolean hasNext() {
        return i < arguments.size();
    }

    public ArgumentType<?> nextArgument() {
        return arguments.get(i++);
    }
    private InputArgument<?> createDefault(ArgumentType<?> type) {
        return type.consume(this.reader);
    }

    public <T> InputArgument<T> peekNext() {
        if (hasNext()) {
            ArgumentType<?> arg = arguments.get(i);
            return (InputArgument<T>) this.argsMap.computeIfAbsent(arg, this::createDefault);
        } else {
            throw new RuntimeException("Illegal to access undeclared argument");
        }
    }

    @Nonnull
    public <T> InputArgument<T> next() {
        if (hasNext()) {
            ArgumentType<?> arg = nextArgument();
            return (InputArgument<T>) this.argsMap.computeIfAbsent(arg, this::createDefault);
        } else {
            throw new RuntimeException("Illegal to access undeclared argument");
        }
    }

    @Nullable public <T> T nextArg() {
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
    public String nextNonnull() {
        return next().nonnullResult();
    }

    public <T extends Enum<T>> T nextEnum(Class<T> type) {
        return next().enumResult(type);
    }

    public String nextSelect(Collection<String> selections) {
        return next().selectResult(selections);
    }

    @Nullable public List<String> getTabComplete(CommandSender sender) {
        List<InputArgument<?>> argumentInputs = new ArrayList<>();
        for (int i = 0; i <= arguments.size(); i++) {
            InputArgument<?> argument;
            if (i == arguments.size() || (argument = argsMap.get(arguments.get(i))) == null) {
                if (i == 0) {
                    return null;
                }
                final int index = i - 1;
                //remove this operation, that's ridiculous
                //argumentInputs.remove(argumentInputs.size() - 1);
                Stream<String> tablist = arguments.get(index).getTab(sender, argumentInputs);
                tablist = tablist == null ? Stream.empty() : tablist;
                return tablist
                        .toList();
            } else {
                argumentInputs.add(argument);
            }
        }
        return null;
    }
}
