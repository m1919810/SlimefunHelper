package me.matl114.managers.file;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.io.File;
import java.io.IOException;
import me.matl114.utils.FileUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;

public class NBTFileStorageImpl extends FileStorageImpl {
    NbtCompound nbtCompound;

    public NBTFileStorageImpl(File file) {
        super(file);
        read();
    }

    @Override
    public <T, W extends T> W asReadOnly(DynamicOps<T> ops) {
        return (ops == NbtOps.INSTANCE) ? (W) this.nbtCompound : (W) NbtOps.INSTANCE.convertTo(ops, this.nbtCompound);
    }

    @Override
    public <T, W extends T> W as(DynamicOps<T> ops) {
        return (W) NbtOps.INSTANCE.convertTo(ops, this.nbtCompound);
    }

    @Override
    public <T> void write(T value, DynamicOps<T> ops) {
        this.nbtCompound = (NbtCompound) ops.convertTo(NbtOps.INSTANCE, value);
        this.dirty = true;
    }

    @Override
    public <W> DataResult<W> read(Codec<W> codec) {
        return codec.parse(NbtOps.INSTANCE, this.nbtCompound);
    }

    @Override
    public <W> DataResult<?> write(Codec<W> codec, W value) {
        DataResult<NbtElement> encoded = codec.encodeStart(NbtOps.INSTANCE, value);
        encoded.result().ifPresent(result -> write(result, NbtOps.INSTANCE));
        return encoded;
    }

    @Override
    public void write() {
        ensureParentDir();
        File tempFile = new File(this.file.getParentFile(), this.file.getName() + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        try {
            NbtIo.write(this.nbtCompound, tempFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + file, e);
        }
        try {
            FileUtils.saveTempFile(tempFile, this.file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + file, e);
        }
        dirty = false;
    }

    @Override
    public void read() {
        if (!this.file.exists()) {
            this.nbtCompound = new NbtCompound();
            dirty = false;
            return;
        }
        try {
            this.nbtCompound = NbtIo.read(this.file.toPath());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        dirty = false;
    }

    @Override
    public void delete() {
        nbtCompound = new NbtCompound();
        file.delete();
        deprecated = true;
    }
}
