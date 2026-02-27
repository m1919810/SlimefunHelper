package me.matl114.utils.commands;

import java.util.Collection;
import me.matl114.utils.interruptions.ArgumentException;

public interface InputArgument {
    public String result();

    public int getInt();

    default boolean isInt() {
        try {
            getInt();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    public boolean getBoolean();

    default boolean isBoolean() {
        try {
            getBoolean();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    public float getFloat();

    default boolean isFloat() {
        try {
            getFloat();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    public double getDouble();

    default boolean isDouble() {
        try {
            getDouble();
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    public int clampInt(int low, int highEx);

    public float clampFloat(float low, float highEx);

    public double clampDouble(double low, double highEx);

    public String nonnullResult();

    default boolean isNonnull() {
        return result() != null;
    }

    public <T extends Enum<T>> T enumResult(Class<T> type);

    default <T extends Enum<T>> boolean isEnum(Class<T> type) {
        try {
            enumResult(type);
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }

    public String selectResult(Collection<String> selections);

    default <T extends Enum<T>> boolean isSelect(Collection<String> selections) {
        try {
            selectResult(selections);
            return true;
        } catch (ArgumentException e) {
            return false;
        }
    }
}
