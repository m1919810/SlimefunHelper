package me.matl114.matlib.utils.command.params.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

import me.matl114.matlib.utils.command.interruption.ArgumentException;
import me.matl114.matlib.utils.command.interruption.TypeError;
import me.matl114.matlib.utils.command.interruption.ValueAbsentError;
import me.matl114.matlib.utils.command.interruption.ValueOutOfRangeError;
import me.matl114.matlib.utils.command.params.ArgumentReader;

public interface InputArgument<W> {
    public ArgumentReader getEndReader();

    public W result();
    //indicates the whole parsed input
    public String resultAsString();
    //indicates the exact block of input for tabbing
    public String tabbingString();

    public ArgumentType<W> getType();

    default boolean isNull(){
        return null == result();
    }


    default void checkResultPresent() throws ArgumentException {
        if(isNull()){
            throw new ValueAbsentError(getEndReader(), getType().getArgsName());
        }
    }

    default int getInt() {
        checkResultPresent();
        String result = resultAsString();
        try {
            return Integer.parseInt(result);
        } catch (Throwable e) {
            throw new TypeError(getEndReader(), getType().getArgsName(), TypeError.BaseArgumentType.INT, result);
        }
    }

    default boolean getBoolean() {
        checkResultPresent();
        String result = resultAsString();
        switch (result) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new TypeError(getEndReader(), getType().getArgsName(), TypeError.BaseArgumentType.BOOLEAN, result);
        }
    }

    default float getFloat() {
        checkResultPresent();
        String result = resultAsString();
        try {
            return Float.parseFloat(result);
        } catch (Throwable e) {
            throw new TypeError(getEndReader(), getType().getArgsName(), TypeError.BaseArgumentType.FLOAT, result);
        }
    }

    default double getDouble() {
        checkResultPresent();
        String result = resultAsString();
        try {
            return Double.parseDouble(result);
        } catch (Throwable e) {
            throw new TypeError(getEndReader(), getType().getArgsName(), TypeError.BaseArgumentType.FLOAT, result);
        }
    }

    default boolean isInt() {
        try {
            getInt();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }


    default boolean isBoolean() {
        try {
            getBoolean();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    default boolean isFloat() {
        try {
            getFloat();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }


    default boolean isDouble() {
        try {
            getDouble();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    default int clampInt(int low, int highEx) {
        int val = getInt();
        if (val >= low && val < highEx) {
            return val;
        } else {
            throw new ValueOutOfRangeError(getEndReader(), getType().getArgsName(), low, highEx, val);
        }
    }

    default float clampFloat(float low, float highEx) {
        float val = getFloat();
        if (val >= low && val < highEx) {
            return val;
        } else {
            throw new ValueOutOfRangeError(getEndReader(), getType().getArgsName(), low, highEx, val);
        }
    }

    default double clampDouble(double low, double highEx) {
        double val = getDouble();
        if (val >= low && val < highEx) {
            return val;
        } else {
            throw new ValueOutOfRangeError(
                getEndReader(),
                getType().getArgsName(),
                String.valueOf(low),
                String.valueOf(highEx),
                String.valueOf(val),
                TypeError.BaseArgumentType.FLOAT);
        }
    }

    default String nonnullResult() {
        checkResultPresent();
        String result = resultAsString();
        if (result == null) {
            throw new ValueAbsentError(getEndReader(), getType().getArgsName());
        } else {
            return result;
        }
    }

    default  <T extends Enum<T>> T enumResult(Class<T> type) {
        String value = nonnullResult();
        T[] results = type.getEnumConstants();
        for (int i = 0; i < results.length; i++) {
            if (results[i].name().equalsIgnoreCase(value)) {
                return results[i];
            }
        }
        throw new ValueOutOfRangeError(
            this.getEndReader(),
            getType().getArgsName(),
            Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList()),
            value,
            TypeError.BaseArgumentType.ENUM);
    }

    default String selectResult(Collection<String> selections) {
        String value = nonnullResult();
        for (var str : selections) {
            if (str.equalsIgnoreCase(value)) {
                return str;
            }
        }
        throw new ValueOutOfRangeError(
            this.getEndReader(), getType().getArgsName(), selections, value, TypeError.BaseArgumentType.ENUM);
    }

    default boolean isNonnull() {
        return result() != null;
    }


    default <T extends Enum<T>> boolean isEnum(Class<T> type) {
        try {
            enumResult(type);
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    default <T extends Enum<T>> boolean isSelect(Collection<String> selections) {
        try {
            selectResult(selections);
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }
}
