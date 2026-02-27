package me.matl114.versioned;

import net.minecraft.SharedConstants;

public class DataVersion {
    public static final String DATA_VERSION_FLAG = "DataVersion";

    public static int getDataVersion() {
        return SharedConstants.getGameVersion().getSaveVersion().getId();
    }

    public static int getSchemaVersion() {
        return 1;
    }
}
