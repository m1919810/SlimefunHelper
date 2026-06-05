package me.matl114.managers.file;

import java.io.File;

public abstract class FileStorageImpl implements FileStorage {
    protected final File file;
    public FileStorageImpl(File file) {
        this.file = file;
        ensureParentDir();
    }
    protected boolean dirty;
    @Override
    public void markDirty(boolean dirty) {
        this.dirty = dirty;
    }

    protected void ensureParentDir() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }
}
