package me.matl114.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@AllArgsConstructor
public class MetadataUpdate {
    final Entity entity;
    DataTracker.SerializedEntry<?> metadata;
}
