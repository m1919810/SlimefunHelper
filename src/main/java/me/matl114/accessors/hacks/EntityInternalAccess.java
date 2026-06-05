package me.matl114.accessors.hacks;

import me.matl114.accessors.events.EntityAccess;
import me.matl114.hacks.utils.entity.Predictor;
import net.minecraft.entity.Entity;

public interface EntityInternalAccess<T extends Entity> extends EntityAccess<T> {
    static <T extends Entity> EntityInternalAccess<T> of(T entity) {
        return (EntityInternalAccess<T>) entity;
    }

    public void setGlow0(boolean glow);

    byte RENDER_LEVEL_DISABLE = 0;
    byte RENDER_LEVEL_WHITELIST = 1;
    byte RENDER_LEVEL_FORCE = 2;

    public byte renderTrackedLevel();

    public void markRenderTracked(byte tracked);

    public Predictor getPositionPredictor();
}
