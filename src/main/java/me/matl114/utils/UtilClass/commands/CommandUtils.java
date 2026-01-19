package me.matl114.utils.UtilClass.commands;

import com.google.common.base.Supplier;
import me.matl114.utils.UtilClass.interruptions.TypeError;
import me.matl114.utils.UtilClass.interruptions.ValueOutOfRangeError;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    public static Map<String, String> parseArguments(String[] args, SimpleCommandArgs.Argument[] requiredDefault) {
        Map<String, String> arguments = new HashMap<>();
        var iter = Arrays.stream(args).iterator();
        var argIter = Arrays.stream(requiredDefault).iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.startsWith("-")) {
                SimpleCommandArgs.Argument selected = null;
                String trueName = arg.replaceFirst("^-+", "");
                for (SimpleCommandArgs.Argument a : requiredDefault) {
                    if (a.isAlias(trueName)) {
                        trueName = a.getArgsName();
                        break;
                    }
                }
                if (arg.startsWith("--")) {
                    // --args inputValue
                    if (iter.hasNext()) {
                        String arg2 = iter.next();

                        arguments.put(trueName, arg2);
                    } else {
                        // ignored
                    }
                } else {
                    // -f -v means boolean
                    arguments.put(trueName, "true");
                }
            } else {
                SimpleCommandArgs.Argument arg1 = null;
                while (argIter.hasNext() && arguments.containsKey((arg1 = argIter.next()).getArgsName())) {
                    // find next argument which is not already collected
                }
                if (arg1 != null) {
                    arguments.put(arg1.getArgsName(), arg);
                } else {
                    // no more argument in list, but still --args -flag should be collected, so no break here
                }
            }
        }
        while (argIter.hasNext()) {
            var re = argIter.next();
            arguments.putIfAbsent(re.getArgsName(), re.getDefaultValue());
        }
        return arguments;
    }

    public static int gint(String val, @Nullable SimpleCommandArgs.Argument arg) {
        try {
            return Integer.parseInt(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.INT, val);
        }
    }

    public static float gfloat(String val, @Nullable SimpleCommandArgs.Argument arg) {
        try {
            return Float.parseFloat(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.FLOAT, val);
        }
    }

    public static double gdouble(String val, @Nullable SimpleCommandArgs.Argument arg) {
        try {
            return Double.parseDouble(val);
        } catch (Throwable e) {
            throw new TypeError(arg, TypeError.BaseArgumentType.FLOAT, val);
        }
    }

    public static boolean gbool(String val, @Nullable SimpleCommandArgs.Argument arg) {
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
            null ,arg, String.valueOf(from), String.valueOf(to), String.valueOf(input), TypeError.BaseArgumentType.FLOAT);
    }

    /**
     * Creates a supplier that provides common number values for tab completion.
     *
     * @return A supplier that returns a list of common number values
     */
    public static Supplier<List<String>> numberSupplier() {
        return () -> List.of("0", "1", "16", "64", "114514", "2147483647");
    }

    /**
     * Creates a supplier that provides common float values for tab completion.
     *
     * @return A supplier that returns a list of common float values
     */
    public static Supplier<List<String>> floatSupplier() {
        return () -> List.of("0.0", "1.0", "2.0", "3.0", "3.14159", "1.57079", "6.283185");
    }

    public static Supplier<Stream<String>> numberStreamSupplier() {
        return () -> Stream.of("0", "1", "16", "64", "114514", "2147483647");
    }

    /**
     * Creates a supplier that provides common float values for tab completion.
     *
     * @return A supplier that returns a list of common float values
     */
    public static Supplier<Stream<String>> floatStreamSupplier() {
        return () -> Stream.of("0.0", "1.0", "2.0", "3.0", "3.14159", "1.57079", "6.283185");
    }
}
