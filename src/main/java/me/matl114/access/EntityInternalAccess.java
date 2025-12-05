package me.matl114.access;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

public interface EntityInternalAccess<T> {
    static <T extends Entity> EntityInternalAccess<T> of(T entity){
        return (EntityInternalAccess<T>) entity;
    }

    default void stopJumpThisTick(){
        throw new UnsupportedOperationException();
    }

    default boolean checkClientPlayer(){
        return this == MinecraftClient.getInstance().player;
    }

    default boolean checkNotClientPlayer(){
        return !checkClientPlayer();
    }
}
