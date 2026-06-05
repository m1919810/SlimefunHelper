package me.matl114.managers.file;

import com.mojang.serialization.DynamicOps;
import java.io.File;
import me.matl114.utils.Debug;
import net.minecraft.nbt.NbtCompound;
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
    public void write() {
        ensureParentDir();
        try {
            NbtIo.write(this.nbtCompound, this.file.toPath());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        dirty = false;
    }

    @Override
    public void read() {
        if (!this.file.exists()) {
            Debug.info("Creating new NBTStorage file at", this.file);
            this.nbtCompound = new NbtCompound();
            write();
        } else {
            try {
                this.nbtCompound = NbtIo.read(this.file.toPath());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            dirty = false;
        }
    }
}
