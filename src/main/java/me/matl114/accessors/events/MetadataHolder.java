package me.matl114.accessors.events;

import javax.annotation.Nonnull;
import me.matl114.utils.containers.MetaData;

public interface MetadataHolder {
    @Nonnull
    public MetaData getMetadata();
}
