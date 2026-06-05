package me.matl114.managers.file;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

public interface FileStorage {
    public <T, W extends T> W asReadOnly(DynamicOps<T> ops);

    public <T, W extends T> W as(DynamicOps<T> ops);

    public <T> void write(T value, DynamicOps<T> ops);

    public void markDirty(boolean dirty);

    public boolean isDirty();

    public void write();

    public void read();
}
