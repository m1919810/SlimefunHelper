package me.matl114.utils.commands;

import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.utils.interruptions.TypeError;
import me.matl114.utils.interruptions.ValueAbsentError;
import me.matl114.utils.interruptions.ValueOutOfRangeError;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

public class ArgumentInputStream {
    @AllArgsConstructor
    @Accessors(fluent = true)
    @Setter
    @Getter
    public static class ArgumentReaderResult {
        // the index lies at the next index of result, where it just read the result, so the result is at top of
        // alreadyRead arguments
        // or the index lies at the end of the reader, where the result is not found, anywhere
        public SimpleCommandArgs.Argument argument;
        public String result;
        public ArgumentReader reader;
        int index;
        boolean isDefault;

        public ArgumentReader getReaderAt() {
            return new ArgumentReader(reader).setCursor(index);
        }
        // if the default value is null and you're requiring a primitive type, then it is a ValueAbsentError , not a
        // type error
        private void checkDefaultNullPrimitive() {
            if (isDefault && result == null) {
                throw new ValueAbsentError(getReaderAt(), argument);
            }
        }

        public int getInt() {
            checkDefaultNullPrimitive();
            try {
                return Integer.parseInt(result);
            } catch (Throwable e) {
                throw new TypeError(getReaderAt(), argument, TypeError.BaseArgumentType.INT, result);
            }
        }

        public boolean getBoolean() {
            checkDefaultNullPrimitive();
            switch (result) {
                case "true":
                    return true;
                case "false":
                    return false;
                default:
                    throw new TypeError(getReaderAt(), argument, TypeError.BaseArgumentType.BOOLEAN, result);
            }
        }

        public float getFloat() {
            checkDefaultNullPrimitive();
            try {
                return Float.parseFloat(result);
            } catch (Throwable e) {
                throw new TypeError(getReaderAt(), argument, TypeError.BaseArgumentType.FLOAT, result);
            }
        }

        public double getDouble() {
            checkDefaultNullPrimitive();
            try {
                return Double.parseDouble(result);
            } catch (Throwable e) {
                throw new TypeError(getReaderAt(), argument, TypeError.BaseArgumentType.FLOAT, result);
            }
        }

        public int clampInt(int low, int highEx) {
            int val = getInt();
            if (val >= low && val < highEx) {
                return val;
            } else {
                throw new ValueOutOfRangeError(getReaderAt(), argument.getArgsName(), low, highEx, val);
            }
        }

        public float clampFloat(float low, float highEx) {
            float val = getFloat();
            if (val >= low && val < highEx) {
                return val;
            } else {
                throw new ValueOutOfRangeError(getReaderAt(), argument.getArgsName(), low, highEx, val);
            }
        }

        public double clampDouble(double low, double highEx) {
            double val = getDouble();
            if (val >= low && val < highEx) {
                return val;
            } else {
                throw new ValueOutOfRangeError(
                        getReaderAt(),
                        argument.getArgsName(),
                        String.valueOf(low),
                        String.valueOf(highEx),
                        String.valueOf(val),
                        TypeError.BaseArgumentType.FLOAT);
            }
        }

        public String nonnullResult() {
            if (result == null) {
                throw new ValueAbsentError(getReaderAt(), argument.getArgsName());
            } else {
                return result;
            }
        }

        public <T extends Enum<T>> T enumResult(Class<T> type) {
            String value = nonnullResult();
            T[] results = type.getEnumConstants();
            for (int i = 0; i < results.length; i++) {
                if (results[i].name().equalsIgnoreCase(value)) {
                    return results[i];
                }
            }
            throw new ValueOutOfRangeError(
                    this.getReaderAt(),
                    argument.getArgsName(),
                    Arrays.stream(type.getEnumConstants())
                            .map(Enum::name)
                            .map(s -> s.toLowerCase(Locale.ROOT))
                            .collect(Collectors.toList()),
                    value,
                    TypeError.BaseArgumentType.ENUM);
        }

        public String selectResult(Collection<String> selections) {
            String value = nonnullResult();
            for (var str : selections) {
                if (str.equalsIgnoreCase(value)) {
                    return str;
                }
            }
            throw new ValueOutOfRangeError(
                    this.getReaderAt(), argument.getArgsName(), selections, value, TypeError.BaseArgumentType.ENUM);
        }
    }

    public ArgumentInputStream(
            ArgumentReader reader,
            SimpleCommandArgs.Argument[] args,
            Map<SimpleCommandArgs.Argument, ArgumentReaderResult> argsMap) {
        this.reader = new ArgumentReader(reader);
        this.arguments = args;
        this.argsMap = argsMap;
    }

    ArgumentReader reader;
    SimpleCommandArgs.Argument[] arguments;
    Map<SimpleCommandArgs.Argument, ArgumentReaderResult> argsMap;
    int i = 0;

    public boolean hasNext() {
        return i < arguments.length;
    }

    public SimpleCommandArgs.Argument nextArgument() {
        return arguments[i++];
    }

    private ArgumentReaderResult createDefault(SimpleCommandArgs.Argument argument) {
        return new ArgumentReaderResult(argument, argument.getDefaultValue(), reader, reader.cursor(), true);
    }

    @Nonnull
    public ArgumentReaderResult next() {
        if (hasNext()) {
            SimpleCommandArgs.Argument arg = nextArgument();
            return argsMap.computeIfAbsent(arg, this::createDefault);
        } else {
            throw new RuntimeException("Illegal to access undeclared argument");
        }
    }

    @Nullable
    public String nextArg() {
        return next().result();
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

    // todo: complete getEnum;
    // todo: complete setEnum

    @Nullable
    public List<String> getTabComplete(PlayerEntity sender) {
        for (int i = 0; i <= arguments.length; i++) {
            if (i == arguments.length || argsMap.get(arguments[i]) == null) {
                if (i == 0) {
                    return null;
                }
                final int index = i - 1;
                List<String> tablist = arguments[index].tabCompletor.apply(sender);
                tablist = tablist == null ? List.of() : tablist;
                ArgumentReaderResult result = argsMap.get(arguments[index]);
                String resultResult = result == null ? null : result.result();
                return tablist.stream()
                        .filter(s -> resultResult != null && s.contains(resultResult))
                        .toList();
            }
        }
        return null;
    }
}
