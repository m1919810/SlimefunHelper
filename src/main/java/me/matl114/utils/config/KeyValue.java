package me.matl114.utils.config;

public interface KeyValue<T> {
    /**
     * get the key name
     * @return
     */
    public String getKeyName();

    /**
     * get the original value
     * @return
     */
    public T getOriginValue();

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
}
