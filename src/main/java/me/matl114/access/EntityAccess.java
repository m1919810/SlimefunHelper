package me.matl114.access;

import me.matl114.utils.ApiMethod;
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
    byte RENDER_LEVEL_DISABLE = 0;
    byte RENDER_LEVEL_WHITELIST = 1;
    byte RENDER_LEVEL_FORCE = 2;
    public byte renderTrackedLevel();

    public void markRenderTracked(byte tracked);

    public void addTickWrapper(ProgressWrapper<T> wrapper);

    public void beforeTick();

    public void afterTick();
}
