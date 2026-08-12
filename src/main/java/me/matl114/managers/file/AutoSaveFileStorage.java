package me.matl114.managers.file;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

@AllArgsConstructor
public class AutoSaveFileStorage implements FileStorage {
    @Delegate
    FileStorage fileStorage;

    public void close() {
        if (fileStorage.isDirty()) {
            fileStorage.write();
        }
        fileStorage.close();
    }
}
