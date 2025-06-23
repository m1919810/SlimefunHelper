package me.matl114.access;

import net.minecraft.entity.Entity;

public interface EntityAccess {
    public void setDataFlag(int flag, boolean val);
    static EntityAccess of(Entity entity){
        return (EntityAccess) entity;
    }
    default void setGlow0(boolean glow){
        setDataFlag(6, glow);
    }
}
