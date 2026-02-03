package me.matl114.accessors.events;

import me.matl114.utils.impl.containers.MetaData;

import javax.annotation.Nonnull;

public interface MetadataHolder {
    @Nonnull
    public MetaData getMetadata();
}
