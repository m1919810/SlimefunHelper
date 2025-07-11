package me.matl114.access;

import me.matl114.utils.UtilClass.ProgressWrapper;
import net.minecraft.entity.Entity;

public interface EntityAccess<T extends Entity> {
    public void setDataFlag(int flag, boolean val);
    static <T extends Entity> EntityAccess<T> of(T entity){
        return (EntityAccess) entity;
    }
    default void setGlow0(boolean glow){
        setDataFlag(6, glow);
    }

    public void addTickWrapper(ProgressWrapper<T> wrapper);

    public void beforeTick();

    public void afterTick();
}
