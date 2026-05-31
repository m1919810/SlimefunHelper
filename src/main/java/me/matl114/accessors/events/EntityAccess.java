package me.matl114.accessors.events;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

public interface EntityAccess<T extends Entity> extends MetadataHolder {
    public void setDataFlag(int flag, boolean val);

    public boolean getDataFlag(int index);

    static <T extends Entity> EntityAccess<T> of(T entity) {
        return (EntityAccess) entity;
    }

    default boolean checkClientPlayer() {
        return this == MinecraftClient.getInstance().player;
    }
}
