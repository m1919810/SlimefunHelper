package me.matl114.accessors.events;

import me.matl114.accessors.interfaces.MetadataHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

public interface EntityAccess<T extends Entity> extends MetadataHolder {
    public void setDataFlag(int flag, boolean val);

    public boolean getDataFlag(int index);

    public int getLivingTicks();

    static <T extends Entity> EntityAccess<T> of(T entity) {
        return (EntityAccess) entity;
    }

    default boolean checkClientPlayer() {
        return this == MinecraftClient.getInstance().player;
    }
}
