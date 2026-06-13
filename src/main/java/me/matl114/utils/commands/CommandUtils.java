package me.matl114.utils.commands;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import me.matl114.utils.commands.interruption.TypeError;
import me.matl114.utils.commands.interruption.ValueOutOfRangeError;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.TabResult;
import org.jetbrains.annotations.Nullable;

public class CommandUtils {
    public static String getOrDefault(String[] args, int index, String defaultValue) {
        return args.length > index ? args[index] : defaultValue;
    }

    public static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Integer parseIntegerOrDefault(String value, Integer defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double parseDoubleOrDefault(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    public static int validRange(int value, int min, int max) {
        return Math.max(Math.min(max, value), min);
    }

    public static int gint(String val, @Nullable ArgumentType<?> arg) {
        try {
            return Integer.parseInt(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.INT, val);
        }
    }

    public static float gfloat(String val, @Nullable ArgumentType<?> arg) {
        try {
            return Float.parseFloat(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.FLOAT, val);
        }
    }

    public static double gdouble(String val, @Nullable ArgumentType<?> arg) {
        try {
            return Double.parseDouble(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.FLOAT, val);
        }
    }

    public static boolean gbool(String val, @Nullable ArgumentType<?> arg) {
        switch (val) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new TypeError(arg, TypeError.BaseArgumentType.BOOLEAN, val);
        }
    }

    public static int gint(String val, @Nullable String arg) {
        try {
            return Integer.parseInt(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.INT, val);
        }
    }

    public static float gfloat(String val, @Nullable String arg) {
        try {
            return Float.parseFloat(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.FLOAT, val);
        }
    }

    public static double gdouble(String val, @Nullable String arg) {
        try {
            return Double.parseDouble(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.FLOAT, val);
        }
    }

    public static boolean gbool(String val, @Nullable String arg) {
        switch (val) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new TypeError(arg, TypeError.BaseArgumentType.BOOLEAN, val);
        }
    }

    public static void range(String arg, int input, int from, int to) {
        if (input >= from && input < to) {
            return;
        }
        throw new ValueOutOfRangeError(null, arg, from, to, input);
    }

    public static void range(String arg, float input, int from, int to) {
        if (input >= from && input < to) {
            return;
        }
        throw new ValueOutOfRangeError(null, arg, from, to, input);
    }

    public static void range(String arg, double input, double from, double to) {
        if (input >= from && input < to) {
            return;
        }
        throw new ValueOutOfRangeError(
                null,
                arg,
                String.valueOf(from),
                String.valueOf(to),
                String.valueOf(input),
                TypeError.BaseArgumentType.FLOAT);
    }

    public static final List<String> BOOLS = List.of("true", "false");
    public static final List<String> INTS = List.of("0", "1", "16", "64", "114514", "2147483647");
    public static final List<String> FLOATS = List.of("0.0", "1.0", "2.0", "3.0", "3.14159", "1.57079", "6.283185");

    public static Supplier<List<String>> boolSupplier() {
        return () -> BOOLS;
    }

    public static List<String> bools() {
        return BOOLS;
    }

    public static Supplier<Stream<String>> boolStreamSupplier() {
        return BOOLS::stream;
    }

    public static List<String> numbers() {
        return INTS;
    }

    public static List<String> floats() {
        return FLOATS;
    }

    /**
     * Creates a supplier that provides common number values for tab completion.
     *
     * @return A supplier that returns a list of common number values
     */
    public static Supplier<List<String>> numberSupplier() {
        return () -> INTS;
    }

    /**
     * Creates a supplier that provides common float values for tab completion.
     *
     * @return A supplier that returns a list of common float values
     */
    public static Supplier<List<String>> floatSupplier() {
        return () -> FLOATS;
    }

    public static Supplier<Stream<String>> numberStreamSupplier() {
        return INTS::stream;
    }

    /**
     * Creates a supplier that provides common float values for tab completion.
     *
     * @return A supplier that returns a list of common float values
     */
    public static Supplier<Stream<String>> floatStreamSupplier() {
        return FLOATS::stream;
    }

    public static Supplier<Stream<String>> fileSupplier(File parent, Predicate<String> suffix) {
        return () -> {
            return Arrays.stream(parent.listFiles())
                    .filter(File::isFile)
                    .map(File::getName)
                    .filter(suffix);
        };
    }

    public static TabResult createXResult() {
        return TabResult.ofStreamFunction(p -> Stream.of("%.2f".formatted(p.getExecutePos().x), "~ ~ ~", "^ ^ ^"));
    }

    public static TabResult createYResult() {
        return TabResult.ofStreamFunction(p -> Stream.of("%.2f".formatted(p.getExecutePos().y), "~ ~ ~", "^ ^ ^"))
                .combine(TabResult.ofDispatcher((p, str) -> {
                    if (str.startsWith("^")) {
                        return Stream.of("^");
                    } else {
                        return Stream.of("~");
                    }
                }));
    }

    public static TabResult createZResult() {
        return TabResult.ofStreamFunction(p -> Stream.of("%.2f".formatted(p.getExecutePos().z), "~ ~ ~", "^ ^ ^"))
                .combine(TabResult.ofDispatcher((p, str) -> {
                    if (str.startsWith("^")) {
                        return Stream.of("^");
                    } else {
                        return Stream.of("~");
                    }
                }));
    }
}
