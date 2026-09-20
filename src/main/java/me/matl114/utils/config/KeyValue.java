package me.matl114.utils.config;

public interface KeyValue<T> extends Value<T> {
    /**
     * get the key name
     * @return
     */
    public String getKeyName();
}
