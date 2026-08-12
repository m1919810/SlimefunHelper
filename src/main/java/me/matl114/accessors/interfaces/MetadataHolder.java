package me.matl114.accessors.interfaces;

import javax.annotation.Nonnull;
import me.matl114.utils.containers.MetaData;

public interface MetadataHolder {
    @Nonnull
    public MetaData getMetadata();

    public boolean isMetaEmpty();
}
