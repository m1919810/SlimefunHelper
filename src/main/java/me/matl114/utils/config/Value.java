package me.matl114.utils.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface Value<T> extends Consumer<T>, Supplier<T> {
    /**
     * get the string value
     * @return
     */
    public String getInput();

    /**
     * get the original value
     * @return
     */
    public T get();

    /**
     * set the original value, will not update string value
     * @param val
     * @return
     */
    public boolean setOriginValue(T val);

    /**
     * check if the value is valid
     * @param val
     * @return
     */
    boolean isValueValid(T val);

    /**
     * return if the current String can successfully cast into the instance and pass all the validators
     * @return
     */
    public boolean isValidate();

    default void accept(T val) {
        setInput(toInput(val));
    }

    public void setInput(String prop);

    public String toInput(T val);
}
