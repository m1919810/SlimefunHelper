package me.matl114.managers.file;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.io.File;

public interface FileStorage extends AutoCloseable {
    public File getFile();

    public <T, W extends T> W asReadOnly(DynamicOps<T> ops);

    public <T, W extends T> W as(DynamicOps<T> ops);

    public <T> void write(T value, DynamicOps<T> ops);

    public <W> DataResult<W> read(Codec<W> codec);

    public <W> DataResult<?> write(Codec<W> codec, W value);

    public void markDirty(boolean dirty);

    public boolean isDirty();

    public boolean isDeprecated();

    public void markDeprecated(boolean deprecated);

    public void write();

    public void read();

    default void close() {
        markDeprecated(true);
    }
}
