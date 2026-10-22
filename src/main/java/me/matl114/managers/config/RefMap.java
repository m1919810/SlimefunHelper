package me.matl114.managers.config;

import javax.annotation.Nonnull;

public interface RefMap {
    public Ref<?> get(String... key);

    default boolean contains(@Nonnull String... path) {
        return get(path) != null;
    }
    // public <T> Ref<T> getOrCreate(Ref<T> val, String... obj);

    public IntRef getInt(String... path);

    public FlagRef getBoolean(String... path);

    public DoubleRef getDouble(String... path);

    public <T extends ConfigEnum> EnumRef<T> getEnum(String... path);

    public StringRef getString(String... path);

    public ListRef getList(String... path);

    public KeyBindRef getKeyBind(String... path);

    public MapRef asRef();

    // boolean setValue(Ref<?> value, String... path);
}
